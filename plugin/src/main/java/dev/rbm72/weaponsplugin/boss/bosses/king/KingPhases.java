package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Fallen King's mechanics: four factory methods, one per phase.
 * <p>
 * Everything else in this package — the duel, the court, the shards, the bell, the anvil field — is
 * package-private and stays that way. A boss definition's job is to say <em>which</em> phase runs in
 * which health band and what he looks like doing it; it has no business holding a reference to a
 * Challenger or a chain block, and a package that exposed those would invite exactly that.
 */
public final class KingPhases {

    private KingPhases() {
    }

    /** P1 — the duel is taught and his court walks in. Exits on a full Challenger rotation. */
    public static PhaseMechanic court(BossInstance instance, double exitFraction) {
        return new CourtPhase(instance, exitFraction);
    }

    /** P2 — the crown shatters into real items. Exits on all of them seated on the throne. */
    public static PhaseMechanic regicide(BossInstance instance, double exitFraction) {
        return new RegicidePhase(instance, exitFraction);
    }

    /** P3 — he abandons the duel and only rear hits count. Exits on the bell being rung twice. */
    public static PhaseMechanic brokenOath(BossInstance instance, double exitFraction) {
        return new BrokenOathPhase(instance, exitFraction);
    }

    /** P4 — anvil-strewn throne room, Execution charges, and no damage rule left at all. */
    public static PhaseMechanic lastStand(BossInstance instance) {
        return new LastStandPhase(instance);
    }
}
