package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Weeping Colossus's mechanics: four factory methods, one per phase. The
 * walls, the dripstone and the ceiling are all package-private, same discipline as {@code StormPhases}.
 */
public final class WeepingPhases {

    private WeepingPhases() {
    }

    /** P1 — The Chamber. Exits on two wall sections jammed. */
    public static PhaseMechanic chamber(BossInstance instance, double exitFraction) {
        return new ChamberPhase(instance, exitFraction);
    }

    /** P2 — Dripstone. Exits on a full drip cycle survived with nobody clustered. */
    public static PhaseMechanic dripstone(BossInstance instance, double exitFraction) {
        return new DripstonePhase(instance, exitFraction);
    }

    /** P3 — The Dark. Exits on three wall sections jammed after the ceiling seals. */
    public static PhaseMechanic theDark(BossInstance instance, double exitFraction) {
        return new TheDarkPhase(instance, exitFraction);
    }

    /** P4 — The Box. The walls halt; the room is whatever the group kept. */
    public static PhaseMechanic theBox(BossInstance instance) {
        return new TheBoxPhase(instance);
    }
}
