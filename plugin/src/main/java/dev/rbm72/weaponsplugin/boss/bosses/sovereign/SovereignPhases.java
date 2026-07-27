package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Void Sovereign's mechanics: four factory methods, one per phase. Every
 * collaborating object underneath — the Void Echo meter, the echo trail, the rifts, the phantoms — is
 * package-private, same discipline as {@code KingPhases}/{@code FrostPhases}/{@code StormPhases}/
 * {@code PlaguePhases}.
 */
public final class SovereignPhases {

    private SovereignPhases() {
    }

    /** P1 — Echoes. Exits on one full Echo cycle survived with nobody struck. */
    public static PhaseMechanic echoes(BossInstance instance, double exitFraction) {
        return new EchoesPhase(instance, exitFraction);
    }

    /** P2 — Collapse. Exits on one full Singularity cycle survived. */
    public static PhaseMechanic collapse(BossInstance instance, double exitFraction) {
        return new CollapsePhase(instance, exitFraction);
    }

    /** P3 — Between. Exits on the real Sovereign struck three times. */
    public static PhaseMechanic between(BossInstance instance, double exitFraction) {
        return new BetweenPhase(instance, exitFraction);
    }

    /** P4 — The Unmaking. Pure spatial endgame on what's left of the arena. */
    public static PhaseMechanic theUnmaking(BossInstance instance) {
        return new TheUnmakingPhase(instance);
    }
}
