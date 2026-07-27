package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Plague Warden's mechanics: four factory methods, one per phase. Every
 * collaborating object underneath — the Infection meter, the pyres, the Spore Nodes, the Host — is
 * package-private, same discipline as {@code KingPhases}/{@code FrostPhases}/{@code StormPhases}.
 */
public final class PlaguePhases {

    private PlaguePhases() {
    }

    /** P1 — Contagion. Exits when nobody stands above half Infection. */
    public static PhaseMechanic contagion(BossInstance instance, double exitFraction) {
        return new ContagionPhase(instance, exitFraction);
    }

    /** P2 — The Bloom. Exits on three Spore Nodes destroyed. */
    public static PhaseMechanic theBloom(BossInstance instance, double exitFraction) {
        return new TheBloomPhase(instance, exitFraction);
    }

    /** P3 — Host. Only the cleansed hurt him until the Host is broken open. */
    public static PhaseMechanic host(BossInstance instance, double exitFraction) {
        return new HostPhase(instance, exitFraction);
    }

    /** P4 — Pandemic. Infection everywhere, pyres burning out one by one. */
    public static PhaseMechanic pandemic(BossInstance instance) {
        return new PandemicPhase(instance);
    }
}
