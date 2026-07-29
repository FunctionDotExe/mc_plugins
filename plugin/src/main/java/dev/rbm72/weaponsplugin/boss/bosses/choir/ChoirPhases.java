package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Hollow Choir's mechanics: four factory methods, one per phase. The
 * noise model, the instruments, the phrase and the attacks are all package-private, same discipline as
 * {@code StormPhases}.
 */
public final class ChoirPhases {

    private ChoirPhases() {
    }

    /** P1 — The Listening. Exits on three attacks successfully misdirected. */
    public static PhaseMechanic listening(BossInstance instance, double exitFraction) {
        return new ListeningPhase(instance, exitFraction);
    }

    /** P2 — The Dark. Exits on one full Darkness cycle survived. */
    public static PhaseMechanic theDark(BossInstance instance, double exitFraction) {
        return new TheDarkPhase(instance, exitFraction);
    }

    /** P3 — The Round. Exits on the Choir's three-note phrase played back correctly. */
    public static PhaseMechanic theRound(BossInstance instance, double exitFraction) {
        return new TheRoundPhase(instance, exitFraction);
    }

    /** P4 — All Voices. Misdirection stops working; it hunts the nearest player. */
    public static PhaseMechanic allVoices(BossInstance instance) {
        return new AllVoicesPhase(instance);
    }
}
