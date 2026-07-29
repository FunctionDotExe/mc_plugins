package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — The Dark.</b> Darkness arrives in waves and vision goes, so the only reliable information left
 * is audio: the note blocks the group is striking, the bells, and the Choir's own tells (batch-3 §4.3).
 * This is the supernatural Darkness that light does not answer — see {@code ChoirAttacks}' header for the
 * deconfliction against the Weeping Colossus's real, lightable dark.
 * <p>
 * Exit requires one full Darkness cycle survived: the wave lands, runs its length, and the group is still
 * standing at the end of it. Punishes groups that rely on their eyes, and equally punishes a group that
 * goes silent — a silent group cannot misdirect, and the attacks all come home.
 */
final class TheDarkPhase extends ChoirPhaseMechanic {

    private int waveCooldownTicks;
    private int cycleRemainingTicks;
    private boolean surviving;
    private boolean survived;

    private int wailCooldownTicks;
    private int fangCooldownTicks;
    private int vexCooldownTicks;

    TheDarkPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Dark", exitFraction);
    }

    @Override
    protected void onArm() {
        waveCooldownTicks = fight.config().num("darkness-first-delay-ticks", 100);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        pulseAttacks(intervalTicks);

        if (surviving) {
            cycleRemainingTicks -= intervalTicks;
            if (cycleRemainingTicks <= 0) {
                surviving = false;
                survived = true;
            }
            return;
        }
        if (survived) {
            return;
        }
        waveCooldownTicks -= intervalTicks;
        if (waveCooldownTicks > 0) {
            return;
        }
        waveCooldownTicks = fight.config().num("darkness-interval-ticks", 360);
        fight.attacks().darknessWave();
        surviving = true;
        cycleRemainingTicks = fight.config().num("darkness-ticks", 160);
    }

    private void pulseAttacks(int intervalTicks) {
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
        return survived;
    }

    @Override
    protected Component readoutText() {
        if (survived) {
            return Component.text("you can fight it blind", NamedTextColor.GREEN);
        }
        if (surviving) {
            return Component.text("DARKNESS — " + Math.max(0, cycleRemainingTicks / 20)
                    + "s, listen for the beat", NamedTextColor.RED);
        }
        return Component.text("survive a full Darkness cycle — keep making noise", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        if (survived) {
            return 1.0;
        }
        if (!surviving) {
            return 0.0;
        }
        int total = Math.max(1, fight.config().num("darkness-ticks", 160));
        return Math.max(0.0, Math.min(1.0, 1.0 - (double) cycleRemainingTicks / total));
    }
}
