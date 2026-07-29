package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — The Listening.</b> Baseline: the sensors, the note blocks, and the rule that noise draws
 * attacks. The lesson lands the first time someone strikes a note block and watches the fangs erupt over
 * there instead of under them (batch-3 §4.3).
 * <p>
 * Exit requires three attacks successfully misdirected — resolved somewhere no player was standing. A
 * group that fought loudly in one place the whole band has not learned the boss and does not pass.
 */
final class ListeningPhase extends ChoirPhaseMechanic {

    private static final int REQUIRED = 3;

    private int wailCooldownTicks;
    private int fangCooldownTicks;
    private int vexCooldownTicks;

    ListeningPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Listening", exitFraction);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        wailCooldownTicks -= intervalTicks;
        fangCooldownTicks -= intervalTicks;
        vexCooldownTicks -= intervalTicks;
        if (wailCooldownTicks <= 0) {
            wailCooldownTicks = fight.config().num("wail-interval-ticks", 180);
            fight.attacks().wail();
        }
        if (fangCooldownTicks <= 0) {
            fangCooldownTicks = fight.config().num("fang-interval-ticks", 120);
            fight.attacks().fangLine();
        }
        if (vexCooldownTicks <= 0) {
            vexCooldownTicks = fight.config().num("vex-interval-ticks", 300);
            fight.attacks().vexSwarm();
        }
    }

    @Override
    protected boolean objectiveMet() {
        return fight.attacks().misdirected() >= REQUIRED;
    }

    @Override
    protected Component readoutText() {
        int done = fight.attacks().misdirected();
        if (done >= REQUIRED) {
            return Component.text("it is chasing sounds now, not people", NamedTextColor.GREEN);
        }
        return Component.text(done + "/" + REQUIRED
                + " misdirected — hit a note block away from you", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) fight.attacks().misdirected() / REQUIRED);
    }
}
