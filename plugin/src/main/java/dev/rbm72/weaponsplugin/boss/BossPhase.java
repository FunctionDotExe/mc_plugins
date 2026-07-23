package dev.rbm72.weaponsplugin.boss;

import java.util.List;
import java.util.function.Consumer;

/**
 * One combat phase: the health-fraction band it's active for, its attack
 * pool, whether it's the enrage phase, and a cinematic to run on entry
 * (sound/particles/equipment changes, phase-transition flavor).
 */
public final class BossPhase {

    private final String name;
    private final double entryThresholdFraction;
    private final List<BossAttack> attacks;
    private final boolean enrage;
    private final Consumer<BossInstance> onEnter;
    private final VulnerabilitySpec vulnerabilitySpec;

    public BossPhase(String name, double entryThresholdFraction, List<BossAttack> attacks,
                      boolean enrage, Consumer<BossInstance> onEnter) {
        this(name, entryThresholdFraction, attacks, enrage, onEnter, null);
    }

    /** @param vulnerabilitySpec nullable — if present, gates this phase behind a break-the-weak-points cycle. */
    public BossPhase(String name, double entryThresholdFraction, List<BossAttack> attacks,
                      boolean enrage, Consumer<BossInstance> onEnter, VulnerabilitySpec vulnerabilitySpec) {
        this.name = name;
        this.entryThresholdFraction = entryThresholdFraction;
        this.attacks = attacks;
        this.enrage = enrage;
        this.onEnter = onEnter;
        this.vulnerabilitySpec = vulnerabilitySpec;
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

    /** Nullable — non-null means this phase is gated behind a break-the-weak-points vulnerability cycle. */
    public VulnerabilitySpec vulnerabilitySpec() {
        return vulnerabilitySpec;
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
