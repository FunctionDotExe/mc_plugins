package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The floor as an economy. Every death inside a catalyst's radius blooms real sculk blocks outward
 * (batch-3 §3.4); sculk slows whoever stands on it, feeds the Bulk while it covers the arena, and grows
 * shriekers in the thickest patches that blind the group with real Darkness. Players clear it the
 * ordinary way — it is a block, so they break it — and that is P2's entire job.
 * <p>
 * Spread is driven here rather than left to vanilla's catalyst behaviour on purpose: sculk vanilla grows
 * by itself is sculk {@code ArenaLedger} never recorded and therefore never restores, and this fight is
 * capable of covering most of an arena. Everything that blooms here goes through
 * {@link Grief#setMechanicBlock}, so the arena comes back clean whatever the group let happen.
 */
final class SculkFloor {

    private final BulkFight fight;
    /** Insertion-ordered so the oldest bloom is the first thing trimmed when the budget is hit. */
    private final Set<Block> sculk = new LinkedHashSet<>();
    private final List<ArenaTotem> shriekers = new ArrayList<>();

    private Handler handler;
    private int shriekCooldownTicks;

    SculkFloor(BulkFight fight) {
        this.fight = fight;
    }

    void arm() {
        if (handler != null) {
            return;
        }
        handler = new Handler();
        fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
    }

    /**
     * One death's worth of growth. Spread per death is fixed (§3.4) — the tax scales with how much the
     * group kills, not with how many of them there are, which is what makes five players the hardest
     * version of this boss rather than the easiest.
     */
    void bloom(Location at) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int budget = fight.config().num("sculk-blocks-per-death", 14);
        double radius = fight.config().dbl("sculk-bloom-radius", 3.0);
        int placed = 0;
        int attempts = 0;
        while (placed < budget && attempts < budget * 6) {
            attempts++;
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double distance = ThreadLocalRandom.current().nextDouble(0, radius);
            int bx = (int) Math.floor(at.getX() + Math.cos(angle) * distance);
            int bz = (int) Math.floor(at.getZ() + Math.sin(angle) * distance);
            Block block = world.getBlockAt(bx, world.getHighestBlockYAt(bx, bz), bz);
            if (block.getType() == Material.SCULK || !block.getType().isSolid()) {
                continue;
            }
            if (Grief.setMechanicBlock(fight.griefContext(), block, Material.SCULK)) {
                sculk.add(block);
                placed++;
            }
        }
        Fx.burst(at.clone().add(0, 0.5, 0), Particle.SCULK_CHARGE_POP, 20, 0.6);
        Fx.sound(at, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.3f, 0.7f);
    }

    void pulse(int intervalTicks) {
        prune();
        applyGround();
        growShriekers(intervalTicks);
        shriek(intervalTicks);
    }

    /** Standing on the Bulk's own floor is slow going — armour does nothing about terrain (§3.6). */
    private void applyGround() {
        int amplifier = Math.max(0, fight.config().num("sculk-slowness-amplifier", 1));
        for (Player player : fight.combatants()) {
            Block under = player.getLocation().getBlock().getRelative(0, -1, 0);
            if (under.getType() != Material.SCULK) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, amplifier, true, false, false));
        }
    }

    /**
     * Shriekers follow coverage, not player count (§3.4): they appear once the floor is thick enough to
     * deserve them, so a group that keeps cleaning never meets this mechanic at all.
     */
    private void growShriekers(int intervalTicks) {
        for (Iterator<ArenaTotem> it = shriekers.iterator(); it.hasNext(); ) {
            if (!it.next().isValid()) {
                it.remove();
            }
        }
        double threshold = fight.config().dbl("shrieker-coverage-threshold", 0.35);
        int max = fight.config().num("shrieker-max", 4);
        if (coverage() < threshold || shriekers.size() >= max || sculk.isEmpty()) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(Math.max(1, 200 / Math.max(1, intervalTicks))) != 0) {
            return;
        }
        List<Block> candidates = new ArrayList<>(sculk);
        Block seed = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Location at = seed.getLocation().add(0.5, 1, 0.5);
        shriekers.add(ArenaTotem.spawn(fight.plugin(), fight.instance(), at, Material.SCULK_SHRIEKER,
                Component.text("Shrieker", NamedTextColor.DARK_AQUA),
                fight.config().dbl("shrieker-health", 30.0),
                Math.max(600, fight.config().num("shrieker-lifetime-ticks", 12000)),
                destroyed -> Fx.sound(destroyed.location(), Sound.BLOCK_SCULK_SHRIEKER_BREAK, 1.3f, 0.8f),
                expired -> {
                }));
        Fx.burst(at, Particle.SCULK_SOUL, 24, 0.5);
        Fx.sound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.1f, 1.2f);
    }

    /** Real vanilla Darkness, from real shriekers — the fight goes blind while a very large thing walks at you. */
    private void shriek(int intervalTicks) {
        if (shriekers.isEmpty()) {
            return;
        }
        shriekCooldownTicks -= intervalTicks;
        if (shriekCooldownTicks > 0) {
            return;
        }
        shriekCooldownTicks = fight.config().num("shrieker-interval-ticks", 200);
        double range = fight.config().dbl("shrieker-range", 12.0);
        int darknessTicks = fight.config().num("shrieker-darkness-ticks", 120);
        for (ArenaTotem shrieker : shriekers) {
            Location at = shrieker.location();
            Fx.sound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.6f, 0.6f);
            for (Player player : dev.rbm72.weaponsplugin.boss.Arena.combatants(at, range)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darknessTicks, 0, true, false, true));
            }
        }
    }

    /** Fraction of the arena floor the sculk has taken, against a fixed notional capacity. */
    double coverage() {
        prune();
        double radius = fight.instance().arena().radius();
        double capacity = Math.max(1.0, Math.PI * radius * radius
                * fight.config().dbl("sculk-capacity-fraction", 0.25));
        return Math.min(1.0, sculk.size() / capacity);
    }

    int blockCount() {
        prune();
        return sculk.size();
    }

    /** How much the Bulk is being fed by the floor right now — the "empowers the Bulk" half of §3.4. */
    void feedBoss(int intervalTicks) {
        double coverage = coverage();
        double threshold = fight.config().dbl("feed-coverage-threshold", 0.2);
        if (coverage <= threshold) {
            return;
        }
        double perSecond = fight.config().dbl("feed-heal-per-second", 3.0) * coverage;
        var entity = fight.instance().entity();
        var max = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        entity.setHealth(Math.min(max.getValue(), entity.getHealth() + perSecond * intervalTicks / 20.0));
    }

    private void prune() {
        sculk.removeIf(block -> block.getType() != Material.SCULK);
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        for (ArenaTotem shrieker : shriekers) {
            shrieker.discard();
        }
        shriekers.clear();
        sculk.clear();
    }

    private final class Handler implements Listener {

        /**
         * Clearing the floor. Tracked here rather than left to {@link #prune()} alone so the break is
         * acknowledged — a group cleaning sculk under pressure should hear that it worked.
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            if (!sculk.remove(event.getBlock())) {
                return;
            }
            Fx.burst(event.getBlock().getLocation().add(0.5, 0.5, 0.5), Particle.SCULK_CHARGE_POP, 8, 0.3);
            Fx.sound(event.getBlock().getLocation(), Sound.BLOCK_SCULK_BREAK, 0.8f, 1.2f);
        }
    }

    /** Test seam: the set of positions currently sculked, for a phase that wants to reason about shape. */
    Set<Block> blocks() {
        return new HashSet<>(sculk);
    }
}
