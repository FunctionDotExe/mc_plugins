package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — Echoes.</b> Baseline: the Void Echo trail runs from the first second, delayed strikes visibly
 * marking each player's own recent positions. The rule teaches itself in about ten seconds (batch-1
 * §5.7): keep moving, never backtrack into your own trail.
 * <p>
 * Exit requires one full cycle survived with nobody struck, on top of the health threshold — batch-1
 * §5.3. A group that keeps eating its own echoes has not learned the phase's one rule yet.
 */
final class EchoesPhase extends SovereignPhaseMechanic {

    private int cycleCountdown;
    private int cyclesSurvived;

    EchoesPhase(BossInstance instance, double exitFraction) {
        super(instance, "Echoes", exitFraction);
    }

    @Override
    protected void onArm() {
        cycleCountdown = fight.config().num("echo-cycle-ticks", 300);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        cycleCountdown -= intervalTicks;
        if (cycleCountdown > 0) {
            return;
        }
        cycleCountdown = fight.config().num("echo-cycle-ticks", 300);
        if (fight.echoTrail().consumeHits() == 0) {
            cyclesSurvived++;
        }
    }

    @Override
    protected boolean objectiveMet() {
        return cyclesSurvived >= 1;
    }

    @Override
    protected int progressSignal() {
        return cyclesSurvived;
    }

    @Override
    protected Component readoutText() {
        return cyclesSurvived >= 1
                ? Component.text("the trail is clean", NamedTextColor.GREEN)
                : Component.text("keep moving — never backtrack", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        int total = Math.max(1, fight.config().num("echo-cycle-ticks", 300));
        return 1.0 - Math.max(0.0, cycleCountdown / (double) total);
    }
}
