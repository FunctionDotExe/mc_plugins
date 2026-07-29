package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Amalgamated Bulk's mechanics: four factory methods, one per phase.
 * Every collaborating object underneath — the catalysts, the sculk floor, the Bulklings — is
 * package-private, same discipline as {@code StormPhases}.
 */
public final class BulkPhases {

    private BulkPhases() {
    }

    /** P1 — Shedding. Exits on five Bulklings killed outside catalyst range. */
    public static PhaseMechanic shedding(BossInstance instance, double exitFraction) {
        return new SheddingPhase(instance, exitFraction);
    }

    /** P2 — The Spread. Exits on sculk coverage pushed back under its threshold. */
    public static PhaseMechanic theSpread(BossInstance instance, double exitFraction) {
        return new TheSpreadPhase(instance, exitFraction);
    }

    /** P3 — Reabsorption. Exits on three reabsorption attempts denied. */
    public static PhaseMechanic reabsorption(BossInstance instance, double exitFraction) {
        return new ReabsorptionPhase(instance, exitFraction);
    }

    /** P4 — Full Mass. No adds, no catalysts; a movement fight on the floor the group left. */
    public static PhaseMechanic fullMass(BossInstance instance) {
        return new FullMassPhase(instance);
    }
}
