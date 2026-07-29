package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Solar Colossus's mechanics: four factory methods, one per phase.
 * Every collaborating object underneath — the joints, the kneel cycle, the beacons, the pillars and
 * debris — is package-private, same discipline as {@code StormPhases}/{@code NecroFight}'s siblings.
 */
public final class ColossusPhases {

    private ColossusPhases() {
    }

    /** P1 — Siege. Exits once both ankle joints are broken. */
    public static PhaseMechanic siege(BossInstance instance, double exitFraction) {
        return new SiegePhase(instance, exitFraction);
    }

    /** P2 — The Climb. Exits once the shoulder core is broken. */
    public static PhaseMechanic theClimb(BossInstance instance, double exitFraction) {
        return new TheClimbPhase(instance, exitFraction);
    }

    /** P3 — Solar Charge. Exits once three charge cycles have been interrupted. Never a damage gate. */
    public static PhaseMechanic solarCharge(BossInstance instance, double exitFraction) {
        return new SolarChargePhase(instance, exitFraction);
    }

    /** P4 — Collapse. The chest core is permanently exposed; ends purely on health like every roster finale. */
    public static PhaseMechanic collapse(BossInstance instance) {
        return new CollapsePhase(instance, 0.0);
    }
}
