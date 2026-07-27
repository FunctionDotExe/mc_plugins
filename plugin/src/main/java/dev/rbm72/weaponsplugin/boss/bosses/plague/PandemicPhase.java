package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P4 — Pandemic.</b> Infection now rises regardless of position and the pyres burn out one by one —
 * batch-1 §4.3: "cleanse efficiency versus the clock, with the boss actively contesting the last pyre".
 * Every earlier phase's wasted fuel is felt here.
 * <p>
 * Objective is satisfied the instant the phase starts, exactly like every roster boss's final phase.
 */
final class PandemicPhase extends PlaguePhaseMechanic {

    private int snuffCountdown;

    PandemicPhase(BossInstance instance) {
        super(instance, "Pandemic", 0.0);
    }

    @Override
    protected void onArm() {
        fight.infection().setRateScale(fight.config().dbl("pandemic-rate-scale", 1.8));
        snuffCountdown = fight.config().num("pandemic-first-snuff-ticks", 160);
        instance.showTitle(
                Component.text("☣ PANDEMIC ☣", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Every pyre will burn out — spend them wisely", NamedTextColor.GRAY));
    }

    @Override
    protected void onPulse(int intervalTicks) {
        snuffCountdown -= intervalTicks;
        if (snuffCountdown <= 0) {
            snuffCountdown = fight.config().num("pandemic-snuff-interval-ticks", 200);
            fight.pyres().snuffOldest();
        }
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
        return Component.text(fight.pyres().activeCount() + "/" + fight.pyres().total()
                + " pyres left", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        int total = Math.max(1, fight.pyres().total());
        return (double) fight.pyres().activeCount() / total;
    }
}
