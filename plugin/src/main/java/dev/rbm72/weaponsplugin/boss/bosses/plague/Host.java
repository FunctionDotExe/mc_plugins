package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * P3's sculk Host: a real multi-block growth at arena centre the Warden burrows into. He is not
 * invulnerable — {@code HostPhase#filterDamage} lets only players currently under the cleansed line hurt
 * him while it stands (batch-1 §4.3) — but the Host itself is a separate, breakable structure, opened
 * with ordinary melee like {@code StormPylons}/{@code SporeNodes}.
 * <p>
 * <b>Noise discipline.</b> The real sculk sensors do the listening — every vibration one of them
 * actually receives ({@link BlockReceiveGameEvent}, vanilla's own sensor-activation hook) adds to a
 * shared noise total, so a crouched player is quiet for the same reason they are quiet against a real
 * Warden: vanilla suppresses vibrations from sneaking entities before this event ever fires, with no
 * code of ours needed to special-case it. Once the total spikes, every sensor shrieks at once —
 * Darkness on everyone nearby and a small add wave — and it resets. The fight's one deliberate tonal
 * pivot (batch-1 §4.1): frantic play is measurably worse than careful play, for the length of exactly
 * one phase.
 */
final class Host {

    private final PlagueFight fight;
    private final List<Block> blocks = new ArrayList<>();
    private Block core;
    private double hp;
    private boolean brokenOpen;
    private double noise;
    private long shriekReadyAtMs;
    private Handler handler;

    Host(PlagueFight fight) {
        this.fight = fight;
    }

    boolean gatesDamage() {
        return !brokenOpen;
    }

    boolean brokenOpen() {
        return brokenOpen;
    }

    void build() {
        if (core != null) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        hp = fight.config().dbl("host-max-hp", 70.0);
        Location centre = fight.instance().arena().center();
        int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            Block block = world.getBlockAt(centre.getBlockX() + offset[0], centre.getBlockY(), centre.getBlockZ() + offset[1]);
            Material material = (offset[0] == 0 && offset[1] == 0) ? Material.SCULK_CATALYST : Material.SCULK_SENSOR;
            if (Grief.setMechanicBlock(fight.griefContext(), block, material)) {
                blocks.add(block);
            }
        }
        core = world.getBlockAt(centre.getBlockX(), centre.getBlockY(), centre.getBlockZ());
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
        Fx.coloredBurst(centre.clone().add(0, 1, 0), PlagueFight.SICKLY, 2.6f, 70, 1.0);
        Fx.sound(centre, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.4f, 0.5f);
    }

    double fraction() {
        double max = fight.config().dbl("host-max-hp", 70.0);
        return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, hp / max));
    }

    /** Ambient upkeep only now — the actual noise accumulation lives in {@link Handler#onVibration}. */
    void pulse(int intervalTicks) {
        if (core == null || brokenOpen) {
            return;
        }
        Location centre = core.getLocation().add(0.5, 0.5, 0.5);
        if (System.currentTimeMillis() / 400 % 2 == 0) {
            Fx.coloredBurst(centre, PlagueFight.SICKLY, 1.0f, 4, 0.4);
        }
    }

    private void shriek(Location centre) {
        noise = 0;
        shriekReadyAtMs = System.currentTimeMillis() + fight.config().num("host-shriek-cooldown-ms", 8000);
        double radius = fight.config().dbl("host-noise-radius", 10.0);
        Fx.coloredBurst(centre, PlagueFight.SICKLY, 3.0f, 90, 1.3);
        Fx.sound(centre, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.7f);
        for (Player player : Arena.combatants(centre, radius)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                    fight.config().num("host-shriek-darkness-ticks", 100), 0, false, false));
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE SENSORS SHRIEK", NamedTextColor.DARK_GREEN),
                    2400L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        World world = centre.getWorld();
        if (world != null) {
            int swarm = fight.config().num("host-shriek-swarm-size", 3);
            for (int i = 0; i < swarm; i++) {
                fight.instance().addManager().spawn(world, centre.clone().add(
                        org.bukkit.util.Vector.getRandom().subtract(new org.bukkit.util.Vector(0.5, 0.5, 0.5)).multiply(3)),
                        EntityType.SILVERFISH, entity -> entity.setPersistent(false));
            }
        }
    }

    void discard() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        blocks.clear();
        core = null;
        brokenOpen = false;
        noise = 0;
    }

    private void breakOpen() {
        brokenOpen = true;
        for (Block block : blocks) {
            Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
        }
        Location at = core.getLocation().add(0.5, 0.5, 0.5);
        Fx.coloredBurst(at, PlagueFight.SICKLY, 3.0f, 90, 1.2);
        Fx.burst(at, Particle.SCULK_SOUL, 60, 0.9);
        Fx.sound(at, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.6f, 0.4f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE HOST IS BROKEN OPEN", NamedTextColor.GREEN),
                    2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(BlockDamageEvent event) {
            if (brokenOpen || core == null || !blocks.contains(event.getBlock())) {
                return;
            }
            hp -= fight.config().dbl("host-hit-damage", 5.0);
            Fx.blockBurst(event.getBlock().getLocation().add(0.5, 0.5, 0.5), Material.SCULK, 12, 0.4);
            if (hp <= 0) {
                breakOpen();
            }
        }

        /**
         * A real sculk sensor of ours actually picking up a vibration — vanilla already refuses to fire
         * this at all for a sneaking source, which is the whole of "approach crouched" (batch-1 §4.4)
         * for free. Not cancelled: the sensor still does its own thing (redstone pulse, particles); this
         * only piggybacks the noise total on top of it.
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onVibration(BlockReceiveGameEvent event) {
            if (brokenOpen || core == null || !blocks.contains(event.getBlock())) {
                return;
            }
            noise += fight.config().dbl("host-noise-per-vibration", 8.0);
            if (noise >= fight.config().dbl("host-noise-threshold", 100.0)
                    && System.currentTimeMillis() >= shriekReadyAtMs) {
                shriek(core.getLocation().add(0.5, 0.5, 0.5));
            }
        }
    }
}
