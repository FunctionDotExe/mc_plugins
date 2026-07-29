package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P1 — Circling.</b> The baseline aerial fight: it circles, throws fireballs, and occasionally
 * swoops low (the existing {@code WingGustAttack}/{@code DiveBombAttack} pool). New this phase:
 * deflection itself, and the first perch landing — a free, unconditional melee window with no race to
 * lose, so the group has already seen what a grounded window looks like before P2 turns landings into
 * something they have to fight for.
 * <p>
 * Exit (batch-2 §4.3): 100–76% HP, and at least one fireball deflected. The health band alone would let
 * a heavy-DPS group skip past the fight's entire signature interaction without ever touching it —
 * exactly the "burst-skipping" failure mode §0.2 exists to close.
 * <p>
 * Solo (§4.5): the tutorial approach uses the same telegraph length as everywhere else, but with only
 * one player in the fight there is nobody else to also be covering fireballs while it happens — that
 * asymmetry is what the design calls "the boss most transformed by playing solo": deflection carries
 * essentially the whole melee damage load, so a solo player who reads the fireballs well clears this
 * phase fast on skill alone.
 */
public final class CirclingPhase extends DragonPhaseMechanic {

    private enum TutorialStage { PENDING, APPROACHING, DONE }

    private TutorialStage tutorialStage = TutorialStage.PENDING;
    private int tutorialDelayTicksLeft;
    private PerchPillars.Pillar tutorialPillar;

    public CirclingPhase(BossInstance instance, double exitFraction) {
        super(instance, "Circling", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.pillars().build();
        fight.fireballs().arm(fight.config().num("circling-fireball-interval-ticks", 90));
        tutorialStage = TutorialStage.PENDING;
        tutorialDelayTicksLeft = fight.config().num("circling-tutorial-delay-ticks", 340);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        switch (tutorialStage) {
            case PENDING -> {
                tutorialDelayTicksLeft -= intervalTicks;
                if (tutorialDelayTicksLeft <= 0 && !isGrounded()) {
                    beginTutorialApproach();
                }
            }
            case APPROACHING -> {
                if (fight.aerial().arrivedAtPerch(fight.config().dbl("perch-arrival-distance", 2.6))) {
                    tutorialStage = TutorialStage.DONE;
                    instance.showTitle(
                            Component.text("IT HAS LANDED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                            Component.text("A free window — hit it now", NamedTextColor.GRAY));
                    enterGroundedWindow(fight.config().num("perch-grounded-window-ticks", 110));
                }
            }
            case DONE -> {
            }
        }
    }

    private void beginTutorialApproach() {
        tutorialPillar = fight.pillars().pickTarget(null);
        if (tutorialPillar == null) {
            // No pillar to land on (all already broken by an overeager group before the tutorial timer
            // fired) — skip the teaching beat rather than approaching nothing.
            tutorialStage = TutorialStage.DONE;
            return;
        }
        fight.aerial().enterPerchApproach(tutorialPillar.top);
        tutorialStage = TutorialStage.APPROACHING;
        instance.showTitle(
                Component.text("IT'S COMING DOWN", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("Watch where it lands — this one is free", NamedTextColor.GRAY));
    }

    @Override
    protected boolean objectiveMet() {
        return fight.fireballs().deflectionCount() >= 1;
    }

    @Override
    protected int progressSignal() {
        return fight.fireballs().deflectionCount();
    }

    @Override
    protected Component readoutText() {
        boolean done = objectiveMet();
        return Component.text(done ? "a fireball has been deflected" : "deflect a fireball back at it",
                done ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, fight.fireballs().deflectionCount());
    }
}
