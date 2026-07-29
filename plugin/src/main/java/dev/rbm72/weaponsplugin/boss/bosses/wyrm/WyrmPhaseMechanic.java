package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.mechanics.FalseGroundMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.TickingMechanic;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

/**
 * Shared body of all four Voidwyrm phases. batch-4 §1.4 lists False Ground as running the whole fight
 * ("all fight"), not just one phase — but a {@code BossPhase} only ever carries one {@link
 * dev.rbm72.weaponsplugin.boss.PhaseMechanic}. Rather than re-authoring the shimmer/collapse logic four
 * times, every phase here composes its own private {@link FalseGroundMechanic} child, started and
 * stopped alongside itself: patches re-roll on every phase transition, which is a feature, not a
 * corner cut (a group that has just learned the tell from a P1 fall shouldn't get to memorise the
 * exact same four spots for the rest of the fight).
 * <p>
 * The composed child never touches {@code setForcedInvulnerable} and this class never touches {@code
 * setDamageMultiplier}, so the two can run concurrently without fighting over instance state — each
 * phase expresses its own damage rule purely through {@link #filterDamage}. The mechanic bar, however,
 * <em>is</em> contested ground (both are {@code MechanicBar.Owner.MECHANIC}), so it is deliberately left
 * to the False Ground child alone; a phase's own objective is communicated through titles and action-bar
 * notices on state changes instead of a second, competing progress bar.
 */
abstract class WyrmPhaseMechanic extends TickingMechanic {

    private static final long PULSE_TICKS = 5L;
    private static final Color STARLIGHT = Color.fromRGB(220, 180, 255);

    protected final WyrmFight fight;

    private final Double exitFraction;
    private final FalseGroundMechanic falseGround;

    private int lastProgressSignal;
    private boolean objectiveRecorded;

    /** @param exitFraction nullable — null means "no health floor beyond this phase's own health band" (the last phase). */
    protected WyrmPhaseMechanic(BossInstance instance, Double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = WyrmFight.of(instance);
        this.exitFraction = exitFraction;
        this.falseGround = new FalseGroundMechanic(instance, "False Ground", STARLIGHT,
                fight.config().num("false-ground-patches", 4),
                fight.config().dbl("false-ground-radius", 3.4),
                fight.config().num("false-ground-cycle-ticks", 240),
                fight.config().num("false-ground-reveal-ticks", 50),
                fight.config().dbl("false-ground-plunge-damage", 16.0),
                fight.config().num("false-ground-disorient-ticks", 70),
                fight.config().dbl("false-ground-placement-fraction", 0.75));
    }

    @Override
    protected final void onStart() {
        falseGround.start();
        onArm();
    }

    @Override
    protected final void onStop() {
        falseGround.stop();
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        onPulse((int) PULSE_TICKS);

        int signal = progressSignal();
        if (signal > lastProgressSignal) {
            lastProgressSignal = signal;
            instance.recordProgress();
        }
        if (!objectiveRecorded && objectiveMet()) {
            objectiveRecorded = true;
            instance.recordExposure();
        }
    }

    @Override
    public final boolean readyToAdvance() {
        return objectiveMet() && (exitFraction == null || healthFraction() <= exitFraction);
    }

    // ------------------------------------------------------------ subclass hooks

    protected void onArm() {
    }

    protected void onDisarm() {
    }

    protected void onPulse(int intervalTicks) {
    }

    protected abstract boolean objectiveMet();

    /** Monotonic partial-progress signal for the phase-floor timeout valve. Default: no partial credit. */
    protected int progressSignal() {
        return 0;
    }

    // ---------------------------------------------------------------- helpers

    protected final double healthFraction() {
        AttributeInstance max = instance.entity().getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 0.0;
        if (maxHealth <= 0.0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, instance.entity().getHealth() / maxHealth));
    }
}
