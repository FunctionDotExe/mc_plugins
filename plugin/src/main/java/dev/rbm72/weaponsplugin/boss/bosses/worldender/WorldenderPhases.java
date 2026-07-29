package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Worldender's mechanics: eight factory methods, one per phase. Every
 * collaborating object underneath — the vibration model, the ice/lava/rifts terrain, the charge and rot
 * meters, the convergence clocks — is package-private, same discipline as every other reworked boss's
 * {@code *Phases} class.
 */
public final class WorldenderPhases {

    private WorldenderPhases() {
    }

    /** P1 — Awakening. Removes distance as safety; starts the vibration hunting that runs the whole fight. */
    public static PhaseMechanic awakening(BossInstance instance) {
        return new AwakeningPhase(instance);
    }

    /** P2 — The Freezing. Removes reliable footing. */
    public static PhaseMechanic theFreezing(BossInstance instance) {
        return new TheFreezingPhase(instance);
    }

    /** P3 — The Charge. Removes safety in numbers. */
    public static PhaseMechanic theCharge(BossInstance instance) {
        return new TheChargePhase(instance);
    }

    /** P4 — The Burning. Removes free ground. */
    public static PhaseMechanic theBurning(BossInstance instance) {
        return new TheBurningPhase(instance);
    }

    /** P5 — The Rot. Removes healing. */
    public static PhaseMechanic theRot(BossInstance instance) {
        return new TheRotPhase(instance);
    }

    /** P6 — The Unmooring. Removes the arena. */
    public static PhaseMechanic theUnmooring(BossInstance instance) {
        return new TheUnmooringPhase(instance);
    }

    /** P7 — Convergence. Removes doing one thing at a time. */
    public static PhaseMechanic convergence(BossInstance instance) {
        return new ConvergencePhase(instance);
    }

    /** P8 — The Unmaking. Removes separate telegraphs. The finale. */
    public static PhaseMechanic theUnmaking(BossInstance instance) {
        return new TheUnmakingPhase(instance);
    }
}
