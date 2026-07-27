package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — The Shattering.</b> The ceiling starts dropping real ice, sweeping across the arena in a
 * moving band and punching real powder-snow pits wherever it lands (see {@link Avalanche}). Sliding on
 * her growing ice field plus a floor that is actively disappearing is what batch-1 §2.3 calls
 * "route-planning at speed".
 * <p>
 * Exit requires one full Avalanche sweep to have completed on top of the health threshold — the phase
 * cannot be nuked past before the group has actually lived through the mechanic it exists to teach.
 */
final class ShatteringPhase extends FrostPhaseMechanic {

    ShatteringPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Shattering", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.avalanche().reset();
    }

    @Override
    protected void onDisarm() {
        fight.avalanche().stop();
    }

    @Override
    protected boolean objectiveMet() {
        return fight.avalanche().cyclesCompleted() >= 1;
    }

    @Override
    protected int progressSignal() {
        return fight.avalanche().cyclesCompleted();
    }

    @Override
    protected Component readoutText() {
        return fight.avalanche().cyclesCompleted() >= 1
                ? Component.text("the sky has cleared, for now", NamedTextColor.GREEN)
                : Component.text("survive the avalanche band", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, fight.avalanche().cyclesCompleted());
    }
}
