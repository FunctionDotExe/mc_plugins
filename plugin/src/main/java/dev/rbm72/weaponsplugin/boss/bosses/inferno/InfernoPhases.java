package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Inferno Warlord's mechanics: four factory methods, one per phase. Every
 * collaborating object underneath — the Burning meter, cauldrons, rising lava, fire trails, TNT clusters,
 * magma hazards, burning logs, Cinder Nova — is package-private, same discipline as
 * {@code StormPhases}/{@code KingPhases}/{@code NecroPhases}-style boss packages.
 */
public final class InfernoPhases {

    private InfernoPhases() {
    }

    /** P1 — The Forge. Exits on the first fuse cut before it detonates. */
    public static PhaseMechanic forge(BossInstance instance, double exitFraction) {
        return new ForgePhase(instance, exitFraction);
    }

    /** P2 — Flood the Foundry. Exits once the group is standing on ground it made. */
    public static PhaseMechanic floodTheFoundry(BossInstance instance, double exitFraction) {
        return new FloodTheFoundryPhase(instance, exitFraction);
    }

    /** P3 — Powder Keg. Exits on three TNT clusters neutralised, not detonated. */
    public static PhaseMechanic powderKeg(BossInstance instance, double exitFraction) {
        return new PowderKegPhase(instance, exitFraction);
    }

    /** P4 — Meltdown. Ungated: a pure damage race on whatever ground the group built. */
    public static PhaseMechanic meltdown(BossInstance instance) {
        return new MeltdownPhase(instance);
    }
}
