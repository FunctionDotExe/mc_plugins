package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Voidwyrm's mechanics: four factory methods, one per phase. Every
 * collaborating object underneath — the flee rig, the burrow, the segment chain, the pocket room — is
 * package-private, same discipline as {@code StormPhases}/{@code ColossusPhases}.
 */
public final class WyrmPhases {

    private WyrmPhases() {
    }

    /** P1 — The Wyrmling. Exits on three real corners landed, on top of the health threshold. */
    public static PhaseMechanic wyrmling(BossInstance instance, double exitFraction) {
        return new WyrmlingPhase(instance, exitFraction);
    }

    /** P2 — The Burrower. Exits on two punished surfacings, on top of the health threshold. */
    public static PhaseMechanic burrower(BossInstance instance, double exitFraction) {
        return new BurrowerPhase(instance, exitFraction);
    }

    /** P3 — The Serpent. Exits on three segments broken, on top of the health threshold. */
    public static PhaseMechanic serpent(BossInstance instance, double exitFraction) {
        return new SerpentPhase(instance, exitFraction);
    }

    /** P4 — The Ancient. No exit beyond the fight's own health-zero death. */
    public static PhaseMechanic ancient(BossInstance instance) {
        return new AncientPhase(instance);
    }
}
