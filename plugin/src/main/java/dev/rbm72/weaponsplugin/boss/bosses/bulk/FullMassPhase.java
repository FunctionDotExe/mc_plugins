package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P4 — Full Mass.</b> It balloons to full size, stops shedding, the catalyst nodes come out, and it
 * simply walks at the group across whatever floor they left behind. No adds, no nodes, no restraint
 * puzzle — a straight movement fight on terrain the players either maintained or didn't (batch-3 §3.3).
 * <p>
 * Objective is satisfied the instant the phase starts, like every roster boss's final phase.
 */
final class FullMassPhase extends BulkPhaseMechanic {

    FullMassPhase(BossInstance instance) {
        super(instance, "Full Mass", 0.0);
    }

    @Override
    protected void onArm() {
        fight.bulklings().stopShedding();
        fight.catalysts().discardAll();
        instance.showTitle(
                Component.text("🟢 FULL MASS 🟢", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("No more adds — just it, and the floor you left", NamedTextColor.GRAY));
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
        int percent = (int) Math.round(fight.sculk().coverage() * 100);
        if (percent <= 20) {
            return Component.text("clean ground to kite on — " + percent + "% sculk", NamedTextColor.GREEN);
        }
        return Component.text(percent + "% of the floor is its now — mind your footing", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return Math.max(0.0, 1.0 - fight.sculk().coverage());
    }
}
