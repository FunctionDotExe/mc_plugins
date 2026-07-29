package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Threefold Bane's mechanics: four factory methods, one per phase. The
 * clocks, the heads' attacks and the cover are all package-private, same discipline as
 * {@code StormPhases}.
 */
public final class BanePhases {

    private BanePhases() {
    }

    /** P1 — Three Clocks. Exits on one Convergence survived. */
    public static PhaseMechanic threeClocks(BossInstance instance, double exitFraction) {
        return new ThreeClocksPhase(instance, exitFraction);
    }

    /** P2 — Swelling. Exits on one clock left running slower than it started. */
    public static PhaseMechanic swelling(BossInstance instance, double exitFraction) {
        return new SwellingPhase(instance, exitFraction);
    }

    /** P3 — Coronation. Exits on two Convergences broken by desync, against its own re-tuning. */
    public static PhaseMechanic coronation(BossInstance instance, double exitFraction) {
        return new CoronationPhase(instance, exitFraction);
    }

    /** P4 — All Three Speak. Clocks lock at whatever tempo the group left them on. */
    public static PhaseMechanic allThreeSpeak(BossInstance instance) {
        return new AllThreeSpeakPhase(instance);
    }
}
