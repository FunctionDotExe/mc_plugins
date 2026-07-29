package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — The Chamber.</b> Full-size room, slow huge Colossus, and four wall sections beginning to
 * advance on a visible, audible cycle. The lesson is that space lost early is never recovered (batch-3
 * §5.3), so the phase teaches jamming before the room is small enough for it to matter.
 * <p>
 * Exit requires two wall sections jammed, on top of the health threshold.
 */
final class ChamberPhase extends WeepingPhaseMechanic {

    private static final int REQUIRED_JAMS = 2;

    ChamberPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Chamber", exitFraction);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.walls().totalJams() >= REQUIRED_JAMS;
    }

    @Override
    protected Component readoutText() {
        int jams = fight.walls().totalJams();
        if (jams >= REQUIRED_JAMS) {
            return Component.text("you can stop a wall — " + fight.walls().chamberRadius()
                    + " blocks of room left", NamedTextColor.GREEN);
        }
        return Component.text(jams + "/" + REQUIRED_JAMS
                + " walls jammed — cut a redstone feed or wedge a piston", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) fight.walls().totalJams() / REQUIRED_JAMS);
    }
}
