package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;

/**
 * <b>P1 — Siege.</b> Pure ground fight: fist slams and sweeping arms from the existing attack pool,
 * real falling sand/gravel pillars from {@link Pillars}, and its own new mechanic — the ankle joints
 * are only ever exposed in a recurring window ("mid-step or mid-slam"), armoured the rest of the time.
 * Batch-2 §2.3: "damage requires timing, not just position."
 * <p>
 * Exit requires both ankle joints broken on top of the health threshold.
 */
final class SiegePhase extends ColossusPhaseMechanic {

    private boolean exposed;
    private boolean telegraphed;
    private int cycleTicksLeft;

    SiegePhase(BossInstance instance, double exitFraction) {
        super(instance, "Siege", exitFraction);
    }

    @Override
    protected void onArm() {
        double ankleHp = fight.config().dbl("ankle-max-hp", 40.0);
        fight.joints().spawn(Joints.Id.ANKLE_LEFT, ankleHp);
        fight.joints().spawn(Joints.Id.ANKLE_RIGHT, ankleHp);
        fight.joints().setExposed(Joints.Id.ANKLE_LEFT, false);
        fight.joints().setExposed(Joints.Id.ANKLE_RIGHT, false);
        exposed = false;
        telegraphed = false;
        cycleTicksLeft = fight.config().num("ankle-armored-ticks", 100);
        fight.pillars().reset();
        fight.armSweep().reset();
    }

    @Override
    protected void onDisarm() {
        // Both are broken by the time this phase ends (its own exit condition) — despawned so the
        // climb doesn't have two dead, ground-level husks cluttering a scene that has moved upward.
        fight.joints().despawn(Joints.Id.ANKLE_LEFT);
        fight.joints().despawn(Joints.Id.ANKLE_RIGHT);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.pillars().pulse(intervalTicks);
        fight.armSweep().pulse(intervalTicks);
        pulseAnkleWindow(intervalTicks);
    }

    /**
     * The recovery-window cycle: armoured for a stretch, then a telegraph, then a real exposure window
     * during which the ankles take full damage, then back to armoured. Narratively tied to its slam
     * cadence without needing a hook into the attack system (attacks are out of scope for this rework —
     * see the handoff doc) — the window itself is what teaches "time your hits", independent of which
     * specific attack happened to be playing when it opened.
     */
    private void pulseAnkleWindow(int intervalTicks) {
        if (fight.joints().allBroken(Joints.Id.ANKLE_LEFT, Joints.Id.ANKLE_RIGHT)) {
            return;
        }
        cycleTicksLeft -= intervalTicks;
        int telegraphTicks = fight.config().num("ankle-expose-telegraph-ticks", 30);
        if (!exposed && !telegraphed && cycleTicksLeft <= telegraphTicks) {
            telegraphed = true;
            telegraphAnkles();
        }
        if (cycleTicksLeft > 0) {
            return;
        }
        exposed = !exposed;
        telegraphed = false;
        fight.joints().setExposed(Joints.Id.ANKLE_LEFT, exposed);
        fight.joints().setExposed(Joints.Id.ANKLE_RIGHT, exposed);
        cycleTicksLeft = exposed
                ? fight.config().num("ankle-expose-window-ticks", 70)
                : fight.config().num("ankle-armored-ticks", 100);
        if (exposed) {
            fight.instance().showTitle(
                    Component.text("RECOVERY", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                    Component.text("The ankles are exposed", NamedTextColor.GRAY));
        }
    }

    private void telegraphAnkles() {
        Fx.sound(fight.instance().entity().getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.7f);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.joints().allBroken(Joints.Id.ANKLE_LEFT, Joints.Id.ANKLE_RIGHT);
    }

    @Override
    protected int progressSignal() {
        return (int) Math.round(fight.joints().damageProgress(Joints.Id.ANKLE_LEFT, Joints.Id.ANKLE_RIGHT) * 1000);
    }

    @Override
    protected Component readoutText() {
        if (objectiveMet()) {
            return Component.text("both ankles broken — it will drop to a knee", NamedTextColor.GREEN);
        }
        String state = exposed ? "EXPOSED — hit them now" : "armoured — wait for the recovery window";
        return Component.text(state, exposed ? NamedTextColor.GREEN : NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return fight.joints().damageProgress(Joints.Id.ANKLE_LEFT, Joints.Id.ANKLE_RIGHT);
    }
}
