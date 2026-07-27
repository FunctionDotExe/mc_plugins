package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Frost Queen's mechanics: four factory methods, one per phase. Every
 * collaborating object underneath — the Chill meter, the campfires, the ice field, the avalanche band,
 * the Heart — is package-private, same discipline as {@code KingPhases}.
 */
public final class FrostPhases {

    private FrostPhases() {
    }

    /** P1 — First Frost. Exits on a Frozen Prison broken open. */
    public static PhaseMechanic firstFrost(BossInstance instance, double exitFraction) {
        return new FirstFrostPhase(instance, exitFraction);
    }

    /** P2 — The Shattering. Exits on one full Avalanche cycle survived. */
    public static PhaseMechanic shattering(BossInstance instance, double exitFraction) {
        return new ShatteringPhase(instance, exitFraction);
    }

    /** P3 — Heart of Winter. She is untouchable until the Frozen Heart is destroyed. */
    public static PhaseMechanic heartOfWinter(BossInstance instance, double exitFraction) {
        return new HeartOfWinterPhase(instance, exitFraction);
    }

    /** P4 — Absolute Zero. Pulse survival on a shrinking campfire economy. */
    public static PhaseMechanic absoluteZero(BossInstance instance) {
        return new AbsoluteZeroPhase(instance);
    }
}
