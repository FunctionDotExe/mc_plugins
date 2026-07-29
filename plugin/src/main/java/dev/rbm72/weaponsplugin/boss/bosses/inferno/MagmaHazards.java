package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>Magma Throw</b> (§1.4): he hauls a chunk of the floor up, winds back, and hurls it down on a marked
 * player — sidestep it, or eat a heavy hit and watch the impact tile become a real, permanent
 * {@link Material#MAGMA_BLOCK}. The permanence is the point: unlike the existing pooled
 * {@code MagmaThrowAttack} (kept in the random "dodge this" cadence for texture), this phase-owned
 * version exists specifically to leave the hazard behind, so it is built on {@link Grief#dropAsBlock}
 * — a real falling block that settles and stays — rather than {@link Grief#throwBlock}, which is
 * deliberately transient and leaves nothing on landing.
 */
final class MagmaHazards {

    private final InfernoFight fight;

    private boolean armed;
    private int throwCount = 1;
    private double intervalCountdownSeconds;

    MagmaHazards(InfernoFight fight) {
        this.fight = fight;
    }

    void arm(int throwCount) {
        this.armed = true;
        this.throwCount = Math.max(1, throwCount);
        this.intervalCountdownSeconds = fight.config().dbl("magma-throw-interval-seconds", 11.0);
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
        intervalCountdownSeconds = fight.config().dbl("magma-throw-interval-seconds", 11.0);
        throwAt(pickTargets());
    }

    private List<Player> pickTargets() {
        List<Player> pool = fight.combatants();
        if (pool.isEmpty()) {
            return List.of();
        }
        List<Player> shuffled = new java.util.ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return shuffled.subList(0, Math.min(throwCount, shuffled.size()));
    }

    private void throwAt(List<Player> targets) {
        // -hazard- infix so these stay separate from MagmaThrowAttack's magma-throw-damage and
        // magma-throw-telegraph-ticks, which land in the same bosses.inferno_warlord section.
        int telegraphTicks = fight.config().num("magma-throw-hazard-telegraph-ticks", 32);
        double height = fight.config().dbl("magma-throw-drop-height", 12.0);
        double damage = fight.config().dbl("magma-throw-hazard-damage", 12.0);
        double radius = fight.config().dbl("magma-throw-radius", 2.2);

        for (Player target : targets) {
            Location column = target.getLocation();
            Telegraph.dangerZone(column, radius);
            Fx.coloredBurst(fight.instance().entity().getLocation().add(0, 1.4, 0), InfernoFight.EMBER, 1.6f, 20, 0.5);
            Fx.sound(fight.instance().entity().getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);

            org.bukkit.scheduler.BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!fight.instance().entity().isValid()) {
                        return;
                    }
                    Grief.dropAsBlock(fight.griefContext(), column, Material.MAGMA_BLOCK, height, damage, radius, landed -> {
                        Fx.sound(landed, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.7f);
                        for (Player nearby : Arena.combatants(landed, radius)) {
                            fight.burning().add(nearby, fight.config().dbl("magma-throw-burning-add", 20.0));
                        }
                    });
                }
            }.runTaskLater(fight.plugin(), telegraphTicks);
            fight.instance().trackTask(task);
        }
    }
}
