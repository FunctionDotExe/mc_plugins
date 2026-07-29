package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P3 — The Dark.</b> The walls seal the ceiling and the room's real light level drops. This is not the
 * Darkness status effect — it is ordinary block light, and the fix is the ordinary one: put torches up
 * and defend them, because the Colossus keeps snuffing them out (batch-3 §5.3). Every torch is a tile of
 * room the group can still fight in.
 * <p>
 * Exit requires three wall sections jammed <em>during</em> the dark — piston work by feel, in a crowded
 * room, with something fast in it.
 */
final class TheDarkPhase extends WeepingPhaseMechanic {

    private static final int REQUIRED_JAMS = 3;

    private int baseline;

    TheDarkPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Dark", exitFraction);
    }

    @Override
    protected void onArm() {
        baseline = fight.walls().totalJams();
        fight.ceiling().seal();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.dripstone().pulse(intervalTicks);
    }

    private int jamsHere() {
        return Math.max(0, fight.walls().totalJams() - baseline);
    }

    @Override
    protected boolean objectiveMet() {
        return jamsHere() >= REQUIRED_JAMS;
    }

    @Override
    protected Component readoutText() {
        if (jamsHere() >= REQUIRED_JAMS) {
            return Component.text("three jammed blind — " + fight.ceiling().litCount()
                    + " lights holding", NamedTextColor.GREEN);
        }
        return Component.text(jamsHere() + "/" + REQUIRED_JAMS + " jammed in the dark — "
                + fight.ceiling().litCount() + " lights up, " + fight.ceiling().snuffedCount()
                + " snuffed", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) jamsHere() / REQUIRED_JAMS);
    }
}
