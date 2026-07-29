package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The four perch pillars: real physical structures the Dragon Elder telegraphs a landing on and the
 * group can break to deny it. Modelled directly on {@code StormPylons} — a real column struck down
 * with ordinary melee via {@link BlockDamageEvent} rather than a special interaction, because reaching
 * one in time through the air is the actual objective.
 * <p>
 * Fixed at four regardless of player count (batch-2 §4.4: "perch count = 4 pillars ... it always
 * telegraphs which") — the tension the design wants is the P2/P3 trade-off of how many survive, not a
 * scaled count. What scales instead is how quickly the group can reach one (more hands), and P3's
 * multiplayer note is explicit that a bigger group leaves itself with *less* surviving cover precisely
 * because P2 is easier for it.
 * <p>
 * Fight-scoped rather than owned by one phase: pillars broken in P2 stay broken for P3's cover
 * reckoning, and the ones left standing after the tutorial landing in P1 are exactly what P2 spends
 * down.
 */
final class PerchPillars {

    static final class Pillar {
        Block core;
        Location top;
        double hp;
        double maxHp;
        boolean destroyed;
    }

    private final DragonFight fight;
    private final List<Pillar> pillars = new ArrayList<>();
    private Handler handler;

    PerchPillars(DragonFight fight) {
        this.fight = fight;
    }

    void build() {
        if (!pillars.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = Math.max(1, fight.config().num("pillar-count", 4));
        double fraction = fight.config().dbl("pillar-fraction", 0.7);
        double maxHp = fight.config().dbl("pillar-max-hp", 45.0);
        int height = fight.config().num("pillar-height", 6);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            for (int y = 0; y < height; y++) {
                Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY() + y, spot.getBlockZ());
                Material material = y == height - 1 ? Material.BONE_BLOCK : Material.CALCITE;
                Grief.setMechanicBlock(fight.griefContext(), block, material);
            }
            Pillar pillar = new Pillar();
            pillar.core = world.getBlockAt(spot.getBlockX(), spot.getBlockY() + height - 1, spot.getBlockZ());
            pillar.top = pillar.core.getLocation().add(0.5, 1.0, 0.5);
            pillar.hp = maxHp;
            pillar.maxHp = maxHp;
            pillars.add(pillar);
            Fx.coloredBurst(spot.clone().add(0, 1.5, 0), DragonFight.EMBER, 1.4f, 26, 0.6);
        }
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    List<Pillar> all() {
        return pillars;
    }

    List<Pillar> standing() {
        return pillars.stream().filter(p -> !p.destroyed).toList();
    }

    boolean isStanding(Pillar pillar) {
        return pillar != null && !pillar.destroyed;
    }

    int standingCount() {
        return standing().size();
    }

    int destroyedCount() {
        return pillars.size() - standingCount();
    }

    /** Any surviving pillar within {@code radius} of {@code loc} — cover, per P3's spec. */
    boolean hasCoverNear(Location loc, double radius) {
        for (Pillar pillar : standing()) {
            if (flat(loc, pillar.core.getLocation()) <= radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * A standing pillar to telegraph a landing on, biased away from whichever one was targeted last so
     * the same pillar isn't picked twice in a row — "it always telegraphs which" only reads as a fair
     * race if the target actually varies.
     */
    Pillar pickTarget(Pillar avoid) {
        List<Pillar> candidates = standing();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1 && avoid != null) {
            List<Pillar> withoutAvoid = candidates.stream().filter(p -> p != avoid).toList();
            if (!withoutAvoid.isEmpty()) {
                candidates = withoutAvoid;
            }
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    void pulse() {
        for (Pillar pillar : pillars) {
            if (pillar.destroyed) {
                continue;
            }
            Fx.coloredBurst(pillar.top.clone(), DragonFight.EMBER, 0.9f, 3, 0.25);
        }
    }

    private void destroy(Pillar pillar) {
        pillar.destroyed = true;
        Grief.setMechanicBlock(fight.griefContext(), pillar.core, Material.AIR);
        Location at = pillar.core.getLocation().add(0.5, 1, 0.5);
        Fx.coloredBurst(at, DragonFight.DRAGON_RED, 2.4f, 60, 1.0);
        Fx.sound(at, Sound.BLOCK_BONE_BLOCK_BREAK, 1.4f, 0.7f);
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        pillars.clear();
    }

    private Location surfaceSpot(double angleRadians, double fraction) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()));
        return spot;
    }

    private static double flat(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(BlockDamageEvent event) {
            for (Pillar pillar : pillars) {
                if (pillar.destroyed || !pillar.core.equals(event.getBlock())) {
                    continue;
                }
                pillar.hp -= fight.config().dbl("pillar-hit-damage", 5.0);
                Fx.blockBurst(event.getBlock().getLocation().add(0.5, 0.5, 0.5), Material.CALCITE, 10, 0.4);
                if (pillar.hp <= 0) {
                    destroy(pillar);
                }
                return;
            }
        }
    }
}
