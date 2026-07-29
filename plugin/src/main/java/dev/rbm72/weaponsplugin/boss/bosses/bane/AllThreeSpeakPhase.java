package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P4 — All Three Speak.</b> The machinery is destroyed and every clock locks at the period it was
 * left on. There is no more sabotage: what remains is a pure, fully-telegraphed rhythm gauntlet at
 * <em>the tempo the group engineered</em> (batch-3 §2.3). A group that lengthened the loops gets a fast
 * but fair finish; a group that ignored them gets a wall, and it is the most direct consequence in the
 * roster.
 * <p>
 * Objective is satisfied the instant the phase starts, like every roster boss's final phase.
 */
final class AllThreeSpeakPhase extends BanePhaseMechanic {

    AllThreeSpeakPhase(BossInstance instance) {
        super(instance, "All Three Speak", 0.0);
    }

    @Override
    protected void onArm() {
        fight.clocks().lockAll();
        instance.showTitle(
                Component.text("☠ ALL THREE SPEAK ☠", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Locked at the tempo you left it — count it out", NamedTextColor.GRAY));
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
        if (fight.clocks().convergencePending()) {
            return Component.text("CONVERGENCE — cover, now", NamedTextColor.RED);
        }
        return Component.text(String.format("locked at %.1fs / %.1fs — %d pillars standing",
                fight.clocks().fastestSeconds(), fight.clocks().slowestSeconds(),
                fight.pillars().standing()), NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        // Reads as "how much room to breathe you bought yourself": the slowest loop against its ceiling.
        double ceiling = Math.max(1.0, fight.config().num("clock-base-ticks", 50)
                + fight.config().num("clock-slots", 6) * fight.config().num("clock-ticks-per-repeater", 22)) / 20.0;
        return Math.max(0.0, Math.min(1.0, fight.clocks().slowestSeconds() / ceiling));
    }
}
