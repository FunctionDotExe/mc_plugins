package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * P1's flee/corner AI — the roster's only mob that actively runs from players instead of pursuing
 * them. Same {@code LivingEntity#setAI(false)} + raw per-tick {@code setVelocity} approach {@code
 * dragon.AerialRig} established, for the identical reason: {@code BossInstance#tick} unconditionally
 * issues a {@code moveTo} toward whichever player it thinks is the target, and that call cannot be
 * reached or skipped from this package. With AI off it queues harmlessly; this rig is the only thing
 * that actually moves the entity.
 * <p>
 * Two states only: fleeing (steer away from the group, staying inside the arena) and held (frozen in
 * place — the brief window after it's been cornered, while it's actually damageable). {@link
 * WyrmlingPhase} owns the transition between them; this class only knows how to move.
 * <p>
 * <b>Settled, the hard way.</b> This class used to carry a caveat that the {@code setAI(false)} trick
 * was proven against a {@code Phantom} but unverified against the {@code EnderDragon} the Voidwyrm then
 * used, and that the symptom of it failing would be the dragon's own flight controller fighting this
 * rig's velocity. That is exactly what happened: a dragon's flight is driven outside the goal system
 * {@code setAI} gates, so it flew itself away from the arena and the fight presented as an audible but
 * invisible, unhittable boss. {@code Voidwyrm#baseEntityType} is a {@code Phantom} now — the entity this
 * rig is actually proven against — so the reasoning above holds as written. Any future boss that steers
 * its entity by raw velocity needs an ordinary {@code Mob}; a dragon cannot be driven this way.
 */
final class EvasiveRig {

    private final WyrmFight fight;

    private boolean aiSuppressed;
    private boolean held;
    private BukkitTask task;

    EvasiveRig(WyrmFight fight) {
        this.fight = fight;
    }

    void start() {
        if (task != null) {
            return;
        }
        held = false;
        suppressVanillaAi();
        task = fight.plugin().getServer().getScheduler().runTaskTimer(fight.plugin(), this::tickMovement, 1L, 1L);
        fight.instance().trackTask(task);
    }

    void discard() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        restoreVanillaAi();
    }

    void hold() {
        held = true;
    }

    void release() {
        held = false;
    }

    boolean isHeld() {
        return held;
    }

    /**
     * Fraction of the eight compass directions around the wyrm that are currently blocked, either by
     * solid terrain (real walls, player-placed blocks — both read identically as "solid" here, which
     * is what lets arena-supplied blocks actually work as a cornering tool) or by a player standing
     * close enough in that direction to be a body in the way. 1.0 means every escape line is shut.
     */
    double blockedFraction(double checkDistance, double playerBlockRadius) {
        LivingEntity boss = fight.instance().entity();
        if (boss == null || !boss.isValid()) {
            return 0.0;
        }
        Location at = boss.getLocation();
        List<Player> nearby = fight.combatants();
        int blocked = 0;
        int directions = 8;
        for (int i = 0; i < directions; i++) {
            double angle = (Math.PI * 2 * i) / directions;
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            if (isDirectionBlocked(at, dir, checkDistance, nearby, playerBlockRadius)) {
                blocked++;
            }
        }
        return blocked / (double) directions;
    }

    private boolean isDirectionBlocked(Location at, Vector dir, double checkDistance, List<Player> nearby, double playerBlockRadius) {
        // Real terrain: walk a few sample points out along this heading at body height.
        for (double d = 1.0; d <= checkDistance; d += 0.75) {
            Location sample = at.clone().add(dir.getX() * d, 0.2, dir.getZ() * d);
            Location head = sample.clone().add(0, 1.0, 0);
            if (sample.getBlock().getType().isSolid() || head.getBlock().getType().isSolid()) {
                return true;
            }
        }
        // A player standing roughly in this heading, close enough to be a body blocking the line.
        for (Player player : nearby) {
            Vector toPlayer = player.getLocation().toVector().subtract(at.toVector());
            toPlayer.setY(0);
            double distance = toPlayer.length();
            if (distance > checkDistance || distance < 1.0E-3) {
                continue;
            }
            double cos = toPlayer.normalize().dot(dir);
            // Roughly within a 45-degree cone of this heading and inside the block radius.
            if (cos >= 0.7 && distance <= playerBlockRadius) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------- movement

    private void tickMovement() {
        LivingEntity boss = fight.instance().entity();
        if (boss == null || !boss.isValid()) {
            return;
        }
        if (held) {
            // A small in-place struggle rather than dead stillness — it reads as fighting the trap,
            // not as the AI having simply switched off.
            boss.setVelocity(new Vector(0, boss.getVelocity().getY(), 0));
            if (boss.getLocation().getY() % 1.0 < 0.02) {
                Fx.burst(boss.getLocation().add(0, 0.5, 0), Particle.PORTAL, 3, 0.3);
            }
            return;
        }
        Vector fleeDirection = fleeVector(boss);
        double speed = fight.config().dbl("flee-speed", 0.42);
        boss.setVelocity(fleeDirection.multiply(speed));
        Fx.point(boss.getLocation().add(0, 0.2, 0), Particle.PORTAL, 1);
    }

    /**
     * Away from the combatants' centroid, steered back inward whenever that would run it past the
     * arena wall — a fleeing target that simply bolts for the edge and gets stuck there stops being a
     * chase and starts being a wall-hug, which defeats the entire phase.
     */
    private Vector fleeVector(LivingEntity boss) {
        List<Player> nearby = fight.combatants();
        Location at = boss.getLocation();
        Vector away;
        if (nearby.isEmpty()) {
            away = new Vector(1, 0, 0);
        } else {
            Vector centroid = new Vector();
            for (Player player : nearby) {
                centroid.add(player.getLocation().toVector());
            }
            centroid.multiply(1.0 / nearby.size());
            away = at.toVector().subtract(centroid);
            away.setY(0);
            if (away.lengthSquared() < 1.0E-4) {
                away = new Vector(1, 0, 0);
            } else {
                away.normalize();
            }
        }

        Location centre = fight.instance().arena().center();
        double radius = fight.instance().arena().radius();
        Vector fromCentre = at.toVector().subtract(centre.toVector());
        fromCentre.setY(0);
        double edgeFraction = fromCentre.length() / radius;
        if (edgeFraction > 0.8) {
            // Close to the wall: blend in a pull back toward the centre so it curves along the wall
            // instead of pinning itself against it.
            Vector inward = fromCentre.clone().multiply(-1);
            if (inward.lengthSquared() > 1.0E-4) {
                inward.normalize();
                away = away.multiply(0.4).add(inward.multiply(0.6));
            }
        }
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(1, 0, 0);
        }
        return away.normalize();
    }

    private void suppressVanillaAi() {
        if (aiSuppressed) {
            return;
        }
        fight.instance().entity().setAI(false);
        aiSuppressed = true;
    }

    private void restoreVanillaAi() {
        if (!aiSuppressed) {
            return;
        }
        LivingEntity boss = fight.instance().entity();
        if (boss != null && boss.isValid()) {
            boss.setAI(true);
        }
        aiSuppressed = false;
    }
}
