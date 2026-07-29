package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — The Climb.</b> Both ankles are already broken (P1's own exit condition guarantees it), so
 * this phase opens straight into a kneel — see {@link KneelCycle} for the full standing/kneeling/
 * rising state machine, the player-placed scaffolding, and the shake-off eject. Batch-2 §2.3: "the
 * group splits into climbers and ground crew — the ground crew must keep it kneeling by re-breaking
 * leg joints, or the climbers get thrown."
 * <p>
 * Exit requires the shoulder core broken on top of the health threshold.
 */
final class TheClimbPhase extends ColossusPhaseMechanic {

    TheClimbPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Climb", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.kneelCycle().begin();
    }

    @Override
    protected void onDisarm() {
        fight.kneelCycle().discard();
        fight.joints().despawn(Joints.Id.KNEE_LEFT);
        fight.joints().despawn(Joints.Id.KNEE_RIGHT);
        // The shoulder core is left standing deliberately if this phase ends by health alone before
        // it breaks — repairAll() in P3 can still restore/heal off it, and there is nothing to lose by
        // leaving a broken-or-not core marker in place while the fight moves on.
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.kneelCycle().pulse(intervalTicks);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.joints().isBroken(Joints.Id.SHOULDER);
    }

    @Override
    protected int progressSignal() {
        return (int) Math.round(fight.joints().damageProgress(Joints.Id.SHOULDER) * 1000) + fight.kneelCycle().kneelCycles();
    }

    @Override
    protected Component readoutText() {
        if (objectiveMet()) {
            return Component.text("shoulder core broken — it staggers", NamedTextColor.GREEN);
        }
        if (fight.kneelCycle().kneeling()) {
            return Component.text("KNEELING — climb and break the shoulder core", NamedTextColor.GREEN);
        }
        return Component.text("standing — ground crew, force it back down", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return fight.joints().damageProgress(Joints.Id.SHOULDER);
    }
}
