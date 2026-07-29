package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * <b>Burning Logs</b> (§1.4): "ceiling cracks, then real falling log blocks" — one per player, landing
 * lit and starting a fresh fire. P1 onward, so it is live for the whole fight rather than a single
 * phase's signature, which is why it lives fight-scoped like {@link MagmaHazards} rather than being
 * owned by one {@code InfernoPhaseMechanic} subclass.
 * <p>
 * Built on {@link Grief#dropAsBlock} exactly like {@link MagmaHazards} — a real settling block, not a
 * cosmetic falling entity — and the landing hook is what turns "a log fell" into "a fresh fire trail
 * seed": the tile beside it ignites the same way {@link FireTrail} ignites its own path, so a lazily
 * avoided log keeps punishing the ground around it long after it lands.
 */
final class BurningLogs {

    private final InfernoFight fight;

    private boolean armed;
    private double intervalCountdownSeconds;

    BurningLogs(InfernoFight fight) {
        this.fight = fight;
    }

    void arm() {
        armed = true;
        intervalCountdownSeconds = fight.config().dbl("logs-interval-seconds", 15.0);
    }

    void disarm() {
        armed = false;
    }

    void pulse(int intervalTicks) {
        if (!armed) {
            return;
        }
        intervalCountdownSeconds -= intervalTicks / 20.0;
        if (intervalCountdownSeconds > 0) {
            return;
        }
        intervalCountdownSeconds = fight.config().dbl("logs-interval-seconds", 15.0);
        dropLogs();
    }

    private void dropLogs() {
        int telegraphTicks = fight.config().num("logs-telegraph-ticks", 34);
        double height = fight.config().dbl("logs-drop-height", 16.0);
        double damage = fight.config().dbl("logs-damage", 9.0);
        double radius = fight.config().dbl("logs-radius", 2.3);

        for (Player target : fight.combatants()) {
            Location column = target.getLocation();
            Fx.burst(column.clone().add(0, 6, 0), Particle.LARGE_SMOKE, 14, 0.6);
            Fx.sound(column, Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);
            Telegraph.dangerZone(column, radius);

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!fight.instance().entity().isValid()) {
                        return;
                    }
                    Grief.dropAsBlock(fight.griefContext(), column, Material.OAK_LOG, height, damage, radius, landed -> {
                        igniteBeside(landed);
                        for (Player nearby : Arena.combatants(landed, radius)) {
                            fight.burning().add(nearby, fight.config().dbl("logs-burning-add", 18.0));
                        }
                    });
                }
            }.runTaskLater(fight.plugin(), telegraphTicks);
            fight.instance().trackTask(task);
        }
    }

    /** The log "lands lit and starts a fresh fire" — one adjacent, open floor tile catches for real. */
    private void igniteBeside(Location landed) {
        World world = landed.getWorld();
        if (world == null) {
            return;
        }
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            Block candidate = world.getBlockAt(landed.getBlockX() + offset[0], landed.getBlockY(), landed.getBlockZ() + offset[1]);
            if (candidate.getType().isAir() && candidate.getRelative(0, -1, 0).getType().isSolid()) {
                Grief.setMechanicBlock(fight.griefContext(), candidate, Material.FIRE);
                Fx.sound(candidate.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.7f);
                return;
            }
        }
    }
}
