package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P4 — The Unmaking.</b> Most of the arena is gone by now; what remains is a small platform network
 * with real pistons shoving on the edges (see {@link Pistons}). Chorus fruit and caught pearls dropped
 * earlier in the fight are the only survival kit left — batch-1 §5.3: "the fight ends on a knife-edge
 * of footing, using the tools you didn't waste earlier".
 * <p>
 * Objective is satisfied the instant the phase starts, exactly like every roster boss's final phase.
 */
final class TheUnmakingPhase extends SovereignPhaseMechanic {

    TheUnmakingPhase(BossInstance instance) {
        super(instance, "The Unmaking", 0.0);
    }

    @Override
    protected void onArm() {
        fight.pistons().build();
        instance.showTitle(
                Component.text("✦ THE UNMAKING ✦", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Hold your footing — nothing else is left", NamedTextColor.GRAY));
    }

    @Override
    protected boolean objectiveMet() {
        return true;
    }

    @Override
    protected boolean announcesCompletion() {
        return false;
    }

    @Override
    protected Component readoutText() {
        return Component.text(fight.rifts().opened() + " rifts torn open — hold what's left", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return 1.0;
    }
}
