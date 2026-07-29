package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — The Spread.</b> The floor is now the problem. Sculk slows whoever stands on it, feeds the Bulk
 * while it covers ground, and grows shriekers in the thickest patches that blind the group with real
 * Darkness. The counterplay is the ordinary Minecraft one: it is a block, so break it (batch-3 §3.3).
 * <p>
 * Exit requires coverage pushed back under a threshold — an economy of attention between killing,
 * cleaning and damaging, and the only phase in the roster whose objective is <em>housework</em>.
 */
final class TheSpreadPhase extends BulkPhaseMechanic {

    TheSpreadPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Spread", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.catalysts().place();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.bulklings().pulseShedding(intervalTicks);
    }

    private double threshold() {
        return fight.config().dbl("spread-clear-threshold", 0.15);
    }

    /**
     * A group that never let the floor grow in the first place satisfies this immediately, which is
     * correct — the phase is asking for a clean arena, not for a chore to be performed.
     */
    @Override
    protected boolean objectiveMet() {
        return fight.sculk().coverage() <= threshold();
    }

    @Override
    protected Component readoutText() {
        int percent = (int) Math.round(fight.sculk().coverage() * 100);
        int target = (int) Math.round(threshold() * 100);
        if (fight.sculk().coverage() <= threshold()) {
            return Component.text("floor clear enough — " + percent + "% sculk", NamedTextColor.GREEN);
        }
        return Component.text(percent + "% sculk — clear it back under " + target + "%", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        double coverage = fight.sculk().coverage();
        if (coverage <= threshold()) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, threshold() / coverage));
    }
}
