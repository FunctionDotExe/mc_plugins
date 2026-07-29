package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P4 — Collapse.</b> It can no longer stand fully, so the chest core is permanently reachable — no
 * kneel cycle to force, no window to time, just the joint-armour rule ({@link
 * ColossusPhaseMechanic#filterDamage}) narrowed to one always-exposed target. Real blocks shed off its
 * body as falling debris ({@link DebrisField}), littering the arena with cover from its remaining arm
 * sweeps ({@link ArmSweep}, reused from P1 — batch-2 §2.4 lists it as a whole-fight tell, not a single
 * phase's signature).
 * <p>
 * No non-health exit condition — this is the roster's mandatory ungated final phase, satisfied the
 * instant it starts, so it ends purely on the health threshold like every finale in the roster.
 */
final class CollapsePhase extends ColossusPhaseMechanic {

    CollapsePhase(BossInstance instance, double exitFraction) {
        super(instance, "Collapse", exitFraction);
    }

    @Override
    protected void onArm() {
        double coreHpBase = fight.config().dbl("chest-core-max-hp-base", 70.0);
        double coreHpPerPlayer = fight.config().dbl("chest-core-max-hp-per-player", 24.0);
        double coreHp = coreHpBase + coreHpPerPlayer * (fight.playerCount() - 1);
        fight.joints().spawn(Joints.Id.CHEST_CORE, coreHp);
        fight.joints().setExposed(Joints.Id.CHEST_CORE, true);
        fight.debris().reset();
        fight.armSweep().reset();
    }

    @Override
    protected void onDisarm() {
        fight.joints().despawn(Joints.Id.CHEST_CORE);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.debris().pulse(intervalTicks);
        fight.armSweep().pulse(intervalTicks);
    }

    /** Satisfied the moment it starts — see the class header. A "you did it" banner here would land on top of the enrage cinematic. */
    @Override
    protected boolean announcesCompletion() {
        return false;
    }

    @Override
    protected boolean objectiveMet() {
        return true;
    }

    @Override
    protected Component readoutText() {
        return Component.text("the chest core is exposed — finish it", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return 1.0 - fight.joints().damageProgress(Joints.Id.CHEST_CORE);
    }
}
