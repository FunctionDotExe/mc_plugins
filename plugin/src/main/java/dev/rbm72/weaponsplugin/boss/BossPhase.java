package dev.rbm72.weaponsplugin.boss;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One combat phase: the health-fraction band it's active for, its attack
 * pool, whether it's the enrage phase, a cinematic to run on entry
 * (sound/particles/equipment changes, phase-transition flavor), and the
 * {@link PhaseMechanic} deciding what players must do to make the boss damageable
 * during it.
 */
public final class BossPhase {

    private final String name;
    private final double entryThresholdFraction;
    private final List<BossAttack> attacks;
    private final boolean enrage;
    private final Consumer<BossInstance> onEnter;
    private final Function<BossInstance, PhaseMechanic> mechanicFactory;

    /** Ungated: the boss is hittable for this phase's whole band — a straight, dangerous DPS race. */
    public BossPhase(String name, double entryThresholdFraction, List<BossAttack> attacks,
                      boolean enrage, Consumer<BossInstance> onEnter) {
        this(name, entryThresholdFraction, attacks, enrage, onEnter, (Function<BossInstance, PhaseMechanic>) null);
    }

    /**
     * Convenience overload for the break-the-weak-points gate, by far the most-authored archetype.
     *
     * @param vulnerabilitySpec nullable — {@code null} leaves the phase ungated.
     */
    public BossPhase(String name, double entryThresholdFraction, List<BossAttack> attacks,
                      boolean enrage, Consumer<BossInstance> onEnter, VulnerabilitySpec vulnerabilitySpec) {
        this(name, entryThresholdFraction, attacks, enrage, onEnter,
                vulnerabilitySpec == null ? null : instance -> new Vulnerability(instance, vulnerabilitySpec));
    }

    /**
     * @param mechanicFactory nullable — builds this phase's gate against the live fight. A {@link Boss} is
     *                    a single shared, stateless definition, so the phase can only hold the recipe;
     *                    the gate itself is per-fight state built by {@link BossInstance}.
     */
    public BossPhase(String name, double entryThresholdFraction, List<BossAttack> attacks,
                      boolean enrage, Consumer<BossInstance> onEnter,
                      Function<BossInstance, PhaseMechanic> mechanicFactory) {
        this.name = name;
        this.entryThresholdFraction = entryThresholdFraction;
        this.attacks = attacks;
        this.enrage = enrage;
        this.onEnter = onEnter;
        this.mechanicFactory = mechanicFactory;
    }

    public String name() {
        return name;
    }

    public double entryThresholdFraction() {
        return entryThresholdFraction;
    }

    public List<BossAttack> attacks() {
        return attacks;
    }

    public boolean isEnrage() {
        return enrage;
    }

    /**
     * Nullable — {@code null} means this phase is deliberately ungated and the boss is hittable
     * throughout it. Non-null builds the gate players must satisfy to damage it.
     */
    public Function<BossInstance, PhaseMechanic> mechanicFactory() {
        return mechanicFactory;
    }

    /** True when this phase makes players clear something before the boss can be hurt. */
    public boolean hasMechanic() {
        return mechanicFactory != null;
    }

    public void onEnter(BossInstance instance) {
        onEnter.accept(instance);
    }

    /**
     * Picks the phase whose {@code entryThresholdFraction} is the smallest
     * value still {@code >= fraction} — i.e. the most specific phase the
     * boss has dropped into. Phases must be authored with strictly
     * descending thresholds (phase 1's threshold is always 1.0).
     */
    public static BossPhase select(List<BossPhase> phases, double fraction) {
        BossPhase best = null;
        for (BossPhase phase : phases) {
            if (phase.entryThresholdFraction() >= fraction
                    && (best == null || phase.entryThresholdFraction() < best.entryThresholdFraction())) {
                best = phase;
            }
        }
        return best != null ? best : phases.get(phases.size() - 1);
    }
}
