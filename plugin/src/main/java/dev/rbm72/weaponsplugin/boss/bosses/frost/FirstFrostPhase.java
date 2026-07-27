package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — First Frost.</b> Baseline: the Chill meter and campfires are live, the ice field starts
 * growing outward from her feet, and Ice Lance volleys teach players to fight on a slick floor.
 * <p>
 * Exit requires at least one Frozen Prison broken open by the group, on top of the health threshold —
 * batch-1 §2.3: a group that never lets the meter or the mechanic actually happen should not get to
 * bulldoze past the phase that exists to teach both.
 */
final class FirstFrostPhase extends FrostPhaseMechanic {

    FirstFrostPhase(BossInstance instance, double exitFraction) {
        super(instance, "First Frost", exitFraction);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.prison().brokenCount() >= 1;
    }

    @Override
    protected int progressSignal() {
        return fight.prison().brokenCount();
    }

    @Override
    protected Component readoutText() {
        return fight.prison().brokenCount() >= 1
                ? Component.text("a prison has been broken", NamedTextColor.GREEN)
                : Component.text("break a Frozen Prison open", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, fight.prison().brokenCount());
    }
}
