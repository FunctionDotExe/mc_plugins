package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P3 — Dive Bomb.</b> Batch-2 §4.3: it stops perching and starts strafing the arena in straight
 * runs, breathing fire along the ground and leaving real burning lanes. Cover is whatever pillars
 * survived P2 — the phase's whole tension is that decision getting paid for, one way or the other.
 * <p>
 * <b>Strafing Run</b> is owned here via {@link StrafingRuns}, because it directly gates this phase's
 * exit condition — the run cycle, its cover check against the surviving pillars, and its own
 * {@link FireLanes}-backed burning lane all have to live somewhere that can answer "how many have been
 * survived" for {@link #objectiveMet()}. <b>Fire Breath</b> is deliberately <em>not</em> re-implemented
 * here — {@code FirestormBreathAttack} already exists, already matches the mechanics-table entry
 * ("throat glows, an audible inhale, then a cone"), and gates nothing, so per this rework's own
 * attacks-are-separate-from-phases rule it belongs in the ordinary "dodge this" pool wired in from
 * {@code DragonElder} rather than duplicated as phase-owned state. The one honest gap that leaves: that
 * attack ignites players directly but does not lay real persistent fire blocks the way
 * {@link StrafingRuns} does, so "burning ground" for Fire Breath specifically is closer to scorch-and-
 * ignite than a lane you have to route around afterward — see the implementation report.
 * <p>
 * Exit: 50–22% HP, and three strafing runs survived — fixed at three regardless of group size per
 * batch-2 §4.3's phase text; the mechanics-table's "run count = 3 + 1 per 2 players" instead scales how
 * many runs the dragon commits to across the whole phase (see {@link StrafingRuns#plannedTotalRuns()}),
 * not the number needed to open the exit.
 * <p>
 * Solo (§4.5): unchanged run count and lane width — there is no player count "sharing" a strafing run's
 * lane the way there is a perch race, so nothing about this phase's shape needs a solo-specific answer
 * beyond what §4.5 already covers (fewer surviving pillars is a group-size trade-off from P2, not one
 * this phase introduces on its own).
 */
public final class DiveBombPhase extends DragonPhaseMechanic {

    private StrafingRuns strafingRuns;

    public DiveBombPhase(BossInstance instance, double exitFraction) {
        super(instance, "Dive Bomb", exitFraction);
    }

    @Override
    protected void onArm() {
        // Same reasoning as DenyThePerchPhase: a carried-over grounded window has no timer left to end
        // it once the mechanic instance that was counting it down is gone.
        fight.aerial().enterCircling();
        fight.pillars().build();
        fight.fireballs().restart(fight.config().num("divebomb-fireball-interval-ticks", 65));
        strafingRuns = new StrafingRuns(fight);
        instance.showTitle(
                Component.text("IT STOPS PERCHING", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Strafing runs now — use what pillars survived", NamedTextColor.GRAY));
    }

    @Override
    protected void onPulse(int intervalTicks) {
        strafingRuns.pulse(intervalTicks);
    }

    @Override
    protected boolean objectiveMet() {
        return strafingRuns.runsCompleted() >= 3;
    }

    @Override
    protected int progressSignal() {
        return strafingRuns.runsCompleted();
    }

    @Override
    protected Component readoutText() {
        return Component.text("strafing runs survived " + Math.min(strafingRuns.runsCompleted(), 3) + "/3",
                objectiveMet() ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.text("   cover " + fight.pillars().standingCount() + " pillars left", NamedTextColor.GRAY));
    }

    @Override
    protected double readoutProgress() {
        return Math.min(strafingRuns.runsCompleted(), 3) / 3.0;
    }
}
