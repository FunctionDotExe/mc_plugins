package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — The Bloom.</b> The ground physically corrupts outward from real Spore Nodes — mud, soul sand
 * and fungal growth that doubles Infection gain and slows anyone standing on it. Left alone, the whole
 * arena eventually turns hostile; destroying nodes is the only thing that stops the spread at its source.
 * <p>
 * Exit requires three Spore Nodes destroyed on top of the health seam — batch-1 §4.3.
 */
final class TheBloomPhase extends PlaguePhaseMechanic {

    TheBloomPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Bloom", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.sporeNodes().build();
    }

    private int required() {
        return Math.min(fight.config().num("bloom-nodes-required", 3), Math.max(1, fight.sporeNodes().total()));
    }

    @Override
    protected boolean objectiveMet() {
        return fight.sporeNodes().destroyedCount() >= required();
    }

    @Override
    protected int progressSignal() {
        return fight.sporeNodes().destroyedCount();
    }

    @Override
    protected Component readoutText() {
        int destroyed = fight.sporeNodes().destroyedCount();
        int required = required();
        return destroyed >= required
                ? Component.text("the spread has stopped", NamedTextColor.GREEN)
                : Component.text(destroyed + "/" + required + " Spore Nodes destroyed", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) fight.sporeNodes().destroyedCount() / required());
    }
}
