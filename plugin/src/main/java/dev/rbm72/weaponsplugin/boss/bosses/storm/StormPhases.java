package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Storm Tyrant's mechanics: four factory methods, one per phase. Every
 * collaborating object underneath — the Static Charge meter, the rods, the flooding, the pylons — is
 * package-private, same discipline as {@code KingPhases}/{@code FrostPhases}.
 */
public final class StormPhases {

    private StormPhases() {
    }

    /** P1 — Static Build. Exits on every player having discharged at least once. */
    public static PhaseMechanic staticBuild(BossInstance instance, double exitFraction) {
        return new StaticBuildPhase(instance, exitFraction);
    }

    /** P2 — Floodplain. Exits on one full strike cycle survived with no rod lost during it. */
    public static PhaseMechanic floodplain(BossInstance instance, double exitFraction) {
        return new FloodplainPhase(instance, exitFraction);
    }

    /** P3 — Eye of the Storm. Only discharged players hurt him while a pylon stands. */
    public static PhaseMechanic eyeOfTheStorm(BossInstance instance, double exitFraction) {
        return new EyeOfTheStormPhase(instance, exitFraction);
    }

    /** P4 — Stormcall. Rods burn out after one use; charge climbs twice as fast. */
    public static PhaseMechanic stormcall(BossInstance instance) {
        return new StormcallPhase(instance);
    }
}
