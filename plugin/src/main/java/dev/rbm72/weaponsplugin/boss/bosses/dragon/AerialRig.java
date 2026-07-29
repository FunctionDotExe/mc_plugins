package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * The circle / perch-approach / strafing-run / grounded state machine — batch-2 §6.1 item 9, flagged
 * as the roster's single highest implementation-risk piece, and the reason this boss was scheduled
 * last in its batch.
 * <p>
 * <b>The problem this exists to solve.</b> {@code BossInstance#tick} unconditionally issues
 * {@code mob.getPathfinder().moveTo(currentTarget, 1.0)} on every tick the boss is not mid-{@code
 * BossAttack} — that call cannot be reached from this package, so it cannot be skipped or overridden.
 * Left alone, the vanilla pathfinder would spend every one of those ticks trying to walk the boss
 * horizontally to whichever player it is chasing, directly fighting any velocity this rig sets for
 * circling or a strafing run. {@link HoverRepositionAttack} in the old flight model tolerated this by
 * living entirely inside a short-cooldown {@code BossAttack}'s own {@code attackInProgress} window,
 * which only ever bought it a fraction of the fight's ticks.
 * <p>
 * The fix used here is {@code LivingEntity#setAI(false)} for every airborne state. In the vanilla
 * mob-tick loop, goal-driven behaviour — including {@code PathNavigation#tick()}, which is what
 * actually advances a mob along a path {@code Pathfinder#moveTo} queued — only runs while
 * {@code isEffectiveAi()} is true; general physics (gravity, velocity integration, collision) does not
 * gate on it. So with AI off, the framework's forced {@code moveTo} call still executes harmlessly
 * every tick (it queues a path that is never walked) while a raw {@code setVelocity} call from this
 * rig is the only thing actually moving the entity. AI is switched back on for {@link
 * AerialState#GROUNDED}, so a grounded window or P4 gets ordinary chase/attack movement from the
 * framework's own per-tick {@code moveTo}/{@code lookAt} calls rather than anything hand-rolled here.
 * <p>
 * This reasoning is sound against the documented NMS tick order, but it has not been verified in a
 * live server — see the implementation report for the honest caveat.
 */
final class AerialRig {

    enum AerialState { CIRCLING, PERCH_APPROACH, STRAFING_RUN, GROUNDED }

    private final DragonFight fight;

    private AerialState state = AerialState.CIRCLING;
    private double circleAngle;
    private Location perchTarget;
    private Location strafeFrom;
    private Location strafeTo;
    private Vector strafeVelocity;
    private Location groundedSpot;
    private boolean aiSuppressed;

    private BukkitTask task;

    AerialRig(DragonFight fight) {
        this.fight = fight;
        // Random start angle so every fight's circle looks different rather than always beginning at
        // the same compass point.
        this.circleAngle = Math.random() * Math.PI * 2;
    }

    void start() {
        if (task != null) {
            return;
        }
        suppressVanillaAi();
        task = fight.plugin().getServer().getScheduler().runTaskTimer(fight.plugin(), this::tickMovement, 1L, 1L);
        fight.instance().trackTask(task);
    }

    /** Fight teardown only — never called mid-fight, so it does not need to be idempotent-safe against restart. */
    void discard() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        restoreVanillaAi();
    }

    AerialState state() {
        return state;
    }

    void enterCircling() {
        state = AerialState.CIRCLING;
        perchTarget = null;
        suppressVanillaAi();
    }

    void enterPerchApproach(Location pillarTop) {
        state = AerialState.PERCH_APPROACH;
        this.perchTarget = pillarTop.clone();
        suppressVanillaAi();
    }

    void enterStrafingRun(Location from, Location to) {
        state = AerialState.STRAFING_RUN;
        this.strafeFrom = from.clone();
        this.strafeTo = to.clone();
        Vector direction = strafeTo.toVector().subtract(strafeFrom.toVector());
        this.strafeVelocity = direction.lengthSquared() > 1.0E-4
                ? direction.normalize().multiply(fight.config().dbl("strafe-speed", 1.6))
                : new Vector(1, 0, 0);
        suppressVanillaAi();
    }

    /**
     * Hands the boss to vanilla ground AI. Deliberate: the framework's own per-tick {@code
     * moveTo}/{@code lookAt} calls (and the existing melee attack pool — Tail Sweep, Grab and Drop)
     * already know how to run a grounded brawl; this rig's job during a grounded window is to get out
     * of the way, not to reimplement ground combat.
     */
    void enterGrounded(Location settleSpot) {
        state = AerialState.GROUNDED;
        groundedSpot = settleSpot == null ? null : settleSpot.clone();
        restoreVanillaAi();
    }

    Location strafeFrom() {
        return strafeFrom;
    }

    Location strafeTo() {
        return strafeTo;
    }

    boolean arrivedAtPerch(double threshold) {
        if (state != AerialState.PERCH_APPROACH || perchTarget == null) {
            return false;
        }
        Location boss = fight.instance().entity().getLocation();
        return flat(boss, perchTarget) <= threshold && Math.abs(boss.getY() - perchTarget.getY()) <= 2.0;
    }

    boolean arrivedAtStrafeEnd(double threshold) {
        return state == AerialState.STRAFING_RUN && strafeTo != null && flat(fight.instance().entity().getLocation(), strafeTo) <= threshold;
    }

    // -------------------------------------------------------------------- movement

    private void tickMovement() {
        LivingEntity boss = fight.instance().entity();
        if (boss == null || !boss.isValid()) {
            return;
        }
        switch (state) {
            case CIRCLING -> tickCircling(boss);
            case PERCH_APPROACH -> nudgeToward(boss, perchTarget, fight.config().dbl("approach-nudge-strength", 0.14),
                    fight.config().dbl("approach-max-speed", 0.85));
            case STRAFING_RUN -> tickStrafing(boss);
            case GROUNDED -> tickSettling(boss);
        }
    }

    private void tickCircling(LivingEntity boss) {
        double angularSpeed = fight.config().dbl("circle-angular-speed", 0.011);
        circleAngle += angularSpeed;
        Location centre = fight.instance().arena().center();
        double radius = fight.instance().arena().radius() * fight.config().dbl("circle-radius-fraction", 0.55);
        double height = fight.config().dbl("circle-height", 11.0);
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        Location target = centre.clone().add(Math.cos(circleAngle) * radius, height, Math.sin(circleAngle) * radius);
        nudgeToward(boss, target, fight.config().dbl("circle-nudge-strength", 0.10), fight.config().dbl("circle-max-speed", 0.85));
    }

    private void tickStrafing(LivingEntity boss) {
        if (strafeVelocity == null) {
            return;
        }
        // A dash, not a nudge — a strafing run has to blow past its endpoint at speed rather than
        // decelerate into it the way a perch approach or the circling orbit should.
        boss.setVelocity(strafeVelocity);
        Location loc = boss.getLocation();
        Fx.point(loc.clone().add(0, -0.3, 0), Particle.FLAME, 2);
    }

    private void tickSettling(LivingEntity boss) {
        if (groundedSpot == null) {
            return;
        }
        double dy = boss.getLocation().getY() - groundedSpot.getY();
        if (dy > 1.5) {
            // Phantom's own flight navigation can keep it hovering even with AI back on for a ground
            // brawl; a small persistent downward nudge is a cheap safeguard so "grounded" reliably
            // reads as grounded rather than floating a few blocks up.
            Vector v = boss.getVelocity();
            boss.setVelocity(new Vector(v.getX() * 0.6, -0.12, v.getZ() * 0.6));
        }
    }

    private void nudgeToward(LivingEntity boss, Location target, double strength, double maxSpeed) {
        if (target == null) {
            return;
        }
        Location bossLoc = boss.getLocation();
        Vector toTarget = target.toVector().subtract(bossLoc.toVector()).multiply(strength);
        if (toTarget.length() > maxSpeed) {
            toTarget = toTarget.normalize().multiply(maxSpeed);
        }
        boss.setVelocity(toTarget);
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
        fight.instance().entity().setAI(true);
        aiSuppressed = false;
    }

    private static double flat(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
