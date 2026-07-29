package dev.rbm72.weaponsplugin.boss.bosses.graft;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P4 — Seams Open.</b> The grafts tear loose. Every module the group never cut keeps firing from the
 * floor as an independent turret, still wired to its power source and now killable by cutting that wire
 * for good; every module they did cut falls dead where it stands. The turret field is literally the
 * fight's own history (§1.3), and the Horror itself is finally soft.
 * <p>
 * Objective is satisfied the instant the phase starts, like every roster boss's final phase — there is
 * no next band to pin a seam against.
 */
final class SeamsOpenPhase extends GraftPhaseMechanic {

    SeamsOpenPhase(BossInstance instance) {
        super(instance, "Seams Open", 0.0);
    }

    @Override
    protected void onArm() {
        fight.grafts().detachAll();
        instance.showTitle(
                Component.text("⚚ SEAMS OPEN ⚚", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every module you left running is a turret now", NamedTextColor.GRAY));
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
        int turrets = fight.grafts().liveTurrets();
        if (turrets == 0) {
            return Component.text("nothing left firing — finish it", NamedTextColor.GREEN);
        }
        return Component.text(turrets + " turret" + (turrets == 1 ? "" : "s")
                + " still wired — cut them or race it", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        int armed = Math.max(1, fight.grafts().armedCount());
        return 1.0 - (double) fight.grafts().liveTurrets() / armed;
    }
}
