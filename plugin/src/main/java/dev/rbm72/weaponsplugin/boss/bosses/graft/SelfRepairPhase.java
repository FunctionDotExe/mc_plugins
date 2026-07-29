package dev.rbm72.weaponsplugin.boss.bosses.graft;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P3 — Self-Repair.</b> A fourth module rebuilds severed lines on a timer, so a single cut is now
 * worth almost nothing: the repair graft shortens every other graft's downtime while it runs. The
 * repair module has its own line, and cutting <em>that</em> first is what opens the window everything
 * else has to happen inside (§1.3).
 * <p>
 * Exit requires three circuits cut inside one repair window — the group's first genuinely planned play.
 * Solo it is a route-optimisation puzzle against the window length rather than a coordination one, which
 * §1.5 calls the good solo shape, so the window is not shortened for small groups.
 */
final class SelfRepairPhase extends GraftPhaseMechanic {

    private static final int REQUIRED_CUTS = 3;

    private int bestInWindow;

    SelfRepairPhase(BossInstance instance, double exitFraction) {
        super(instance, "Self-Repair", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.grafts().arm(Grafts.Kind.DISPENSER);
        fight.grafts().arm(Grafts.Kind.PISTON);
        fight.grafts().arm(Grafts.Kind.OBSERVER);
        fight.grafts().arm(Grafts.Kind.REPAIR);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        bestInWindow = Math.max(bestInWindow, fight.grafts().cutsInRepairWindow());
    }

    @Override
    protected boolean objectiveMet() {
        return bestInWindow >= REQUIRED_CUTS;
    }

    @Override
    protected Component readoutText() {
        if (bestInWindow >= REQUIRED_CUTS) {
            return Component.text("three at once — its repairs can't keep up", NamedTextColor.GREEN);
        }
        if (fight.grafts().repairWindowOpen()) {
            return Component.text("WINDOW OPEN — " + fight.grafts().cutsInRepairWindow() + "/"
                    + REQUIRED_CUTS + " cut", NamedTextColor.YELLOW);
        }
        return Component.text("cut the repair line first, then three more", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        int shown = fight.grafts().repairWindowOpen()
                ? Math.max(bestInWindow, fight.grafts().cutsInRepairWindow())
                : bestInWindow;
        return Math.min(1.0, (double) shown / REQUIRED_CUTS);
    }
}
