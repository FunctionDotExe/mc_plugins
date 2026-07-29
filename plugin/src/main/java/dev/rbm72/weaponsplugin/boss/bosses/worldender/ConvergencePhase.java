package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;

/**
 * <b>P7 — Convergence (28–14%) · removes doing one thing at a time.</b> Three real redstone clocks run at
 * once, each reactivating one earlier phase's rule on its own tempo — ice, charge, lava. The group must
 * hold three different disciplines at once, on a beat they can hear; a repeater pulled speeds a head up,
 * one added slows it down.
 */
final class ConvergencePhase extends WorldenderPhaseMechanic {

    ConvergencePhase(BossInstance instance) {
        super(instance, "Convergence");
    }

    @Override
    protected void onArm() {
        WorldenderSupplies.repeater(fight);
        fight.clocks().build();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.clocks().pulse(intervalTicks);
    }

    @Override
    protected Component readoutText() {
        return fight.clocks().readout();
    }
}
