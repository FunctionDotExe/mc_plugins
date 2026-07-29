package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — Dripstone.</b> The danger goes vertical in a room that is already closing horizontally: real
 * pointed dripstone grows above marked tiles and falls, and what falls stays as floor obstruction
 * (batch-3 §5.3). The group has to look up while managing walls and a boss.
 * <p>
 * Exit requires one full drip cycle survived with nobody pinned — no spike dropped on a cluster of two
 * or more. Spreading out is the ask, and in a shrinking room that is a real cost rather than free advice.
 */
final class DripstonePhase extends WeepingPhaseMechanic {

    private int cycleTicks;
    private boolean cleanCycle;

    DripstonePhase(BossInstance instance, double exitFraction) {
        super(instance, "Dripstone", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.dripstone().resetClusterWatch();
        cycleTicks = fight.config().num("dripstone-cycle-ticks", 400);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.dripstone().pulse(intervalTicks);
        if (cleanCycle) {
            return;
        }
        cycleTicks -= intervalTicks;
        if (cycleTicks > 0) {
            return;
        }
        if (fight.dripstone().largestCluster() <= 1) {
            cleanCycle = true;
            return;
        }
        // Somebody was caught in a cluster: reset the watch and the group gets another cycle to do it
        // cleanly. Failing this is a delay, never a dead end.
        fight.dripstone().resetClusterWatch();
        cycleTicks = fight.config().num("dripstone-cycle-ticks", 400);
    }

    @Override
    protected boolean objectiveMet() {
        return cleanCycle;
    }

    @Override
    protected Component readoutText() {
        if (cleanCycle) {
            return Component.text("a clean cycle — " + fight.dripstone().obstructions()
                    + " spikes in the floor now", NamedTextColor.GREEN);
        }
        if (fight.dripstone().largestCluster() > 1) {
            return Component.text("clustered — spread out, cycle restarting", NamedTextColor.RED);
        }
        return Component.text("survive a drip cycle unclustered — "
                + Math.max(0, cycleTicks / 20) + "s", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        if (cleanCycle) {
            return 1.0;
        }
        int total = Math.max(1, fight.config().num("dripstone-cycle-ticks", 400));
        return Math.max(0.0, Math.min(1.0, 1.0 - (double) cycleTicks / total));
    }
}
