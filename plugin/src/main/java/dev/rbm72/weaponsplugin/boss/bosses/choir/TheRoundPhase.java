package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P3 — The Round.</b> The Choir starts singing a phrase, and playing it back on the arena's note
 * blocks shatters its ward. Grand Illusion runs alongside it: identical copies that make no sound when
 * they move, so the group has to listen rather than look while doing a live puzzle in the dark (batch-3
 * §4.3). The single most distinctive moment in the roster — a boss fight that briefly becomes music.
 * <p>
 * Exit requires the phrase played back correctly once. It repeats continuously until then, because this
 * is a listening challenge and not a memory test.
 */
final class TheRoundPhase extends ChoirPhaseMechanic {

    private int wailCooldownTicks;
    private int fangCooldownTicks;
    private int darknessCooldownTicks;

    TheRoundPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Round", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.attacks().raiseIllusions();
        fight.phrase().begin();
        darknessCooldownTicks = fight.config().num("darkness-interval-ticks", 360);
    }

    @Override
    protected void onDisarm() {
        fight.phrase().stop();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.phrase().pulse(intervalTicks);

        wailCooldownTicks -= intervalTicks;
        fangCooldownTicks -= intervalTicks;
        darknessCooldownTicks -= intervalTicks;
        if (wailCooldownTicks <= 0) {
            wailCooldownTicks = fight.config().num("wail-interval-ticks", 180);
            fight.attacks().wail();
        }
        if (fangCooldownTicks <= 0) {
            fangCooldownTicks = fight.config().num("fang-interval-ticks", 120);
            fight.attacks().fangLine();
        }
        if (darknessCooldownTicks <= 0) {
            darknessCooldownTicks = fight.config().num("darkness-interval-ticks", 360);
            fight.attacks().darknessWave();
        }
        // A fresh phrase each cycle (§4.3): once one is answered the ward is broken and the phase can
        // end, but if the group is still here it gets a new one rather than silence.
        if (!fight.phrase().active() && !objectiveMet()) {
            fight.phrase().begin();
        }
    }

    @Override
    protected boolean objectiveMet() {
        return fight.phrase().completed() >= 1;
    }

    @Override
    protected Component readoutText() {
        if (objectiveMet()) {
            return Component.text("the ward is broken — it is only a voice now", NamedTextColor.GREEN);
        }
        if (fight.phrase().wrongNotes() > 0 && fight.phrase().progress() == 0) {
            return Component.text("wrong note — listen again, one caller only", NamedTextColor.RED);
        }
        return Component.text("play it back — " + fight.phrase().progress() + "/"
                + fight.phrase().length() + " notes, "
                + fight.attacks().illusionCount() + " copies watching", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        if (objectiveMet()) {
            return 1.0;
        }
        return Math.min(1.0, (double) fight.phrase().progress() / fight.phrase().length());
    }
}
