package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — Rising Tide.</b> Water floods the arena in stages — still partly a ground fight, with the
 * shoreline visibly moving between stages — while the group learns the tide schedule and places the
 * fight's first conduit.
 * <p>
 * Exit requires the health threshold <em>and</em> one conduit placed and currently standing (batch-2
 * §3.3) — "held", not merely placed once: {@link #objectiveMet()} reads {@code activeCount()} live, so
 * a conduit smashed right at the seam pins the boss there until it is replaced. Nobody smashes
 * conduits before P2, so in practice this only ever costs time, never becomes a hard lock — and the
 * framework's own floor-lock timeout is the backstop regardless.
 * <p>
 * Solo: exactly one pedestal exists ({@link Conduits#buildPedestals}), so there is only ever one thing
 * to defend and nowhere for attention to split.
 */
public final class RisingTidePhase extends LeviathanPhaseMechanic {

    private int stagesRaised;

    public RisingTidePhase(BossInstance instance, double exitFraction) {
        super(instance, "Rising Tide", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.conduits().buildPedestals();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        double stage1At = fight.config().dbl("rising-tide-stage1-seconds", 6.0);
        double stage2At = fight.config().dbl("rising-tide-stage2-seconds", 26.0);
        double stage1Fraction = fight.config().dbl("rising-tide-stage1-fraction", 0.35);
        double stage2Fraction = fight.config().dbl("rising-tide-stage2-fraction", 0.68);

        if (stagesRaised < 1 && elapsedSeconds() >= stage1At) {
            fight.water().raiseTo(stage1Fraction);
            stagesRaised = 1;
        } else if (stagesRaised < 2 && elapsedSeconds() >= stage2At) {
            fight.water().raiseTo(stage2Fraction);
            stagesRaised = 2;
        }
    }

    @Override
    protected boolean objectiveMet() {
        return fight.conduits().activeCount() >= 1;
    }

    @Override
    protected int progressSignal() {
        return fight.conduits().activeCount();
    }

    @Override
    protected Component readoutText() {
        return objectiveMet()
                ? Component.text("a conduit holds", NamedTextColor.GREEN)
                : Component.text("place the conduit — tide " + Math.round(fight.water().surfaceFraction() * 100) + "%",
                        NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return objectiveMet() ? 1.0 : fight.water().surfaceFraction();
    }
}
