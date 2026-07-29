package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — The Court.</b> The duel is established, the three combos are taught, and his court walks in
 * behind them.
 * <p>
 * The phase exits on a <b>full Challenger rotation</b>, not on health: the mantle has to have passed
 * through every player present at least once before the health seam opens. That is deliberately the
 * hardest thing for a group to do accidentally — the mantle only moves on a riposte or a Wound, so
 * "everyone has held it" means everyone has stood in front of him and either read a combo correctly or
 * eaten one. Nobody reaches P2 having watched.
 * <p>
 * Solo has a different bar for the same reason rather than a disabled mechanic (§0.2 rule 7): with
 * nobody to rotate to, the Challenger mark stays put for the whole fight by design, so the phase asks
 * instead for a couple of clean ripostes — the same proof, measured on the one player there is.
 */
final class CourtPhase extends KingPhaseMechanic {

    CourtPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Court", exitFraction);
    }

    @Override
    protected void onArm() {
        // No Judgment yet: P1 is the phase where the floor is still intact and the group is learning to
        // read him. Anvils arrive in P3, and their whole meaning is that the room degrades over time.
        fight.judgment().setRunning(false);
    }

    @Override
    protected boolean objectiveMet() {
        if (solo()) {
            return fight.duel().ripostes() >= Math.max(1, fight.config().num("court-solo-ripostes", 2));
        }
        return fight.duel().distinctHolders() >= required();
    }

    /** One hand-off per player present: the mantle has been all the way round the group. */
    private int required() {
        return Math.max(1, fight.playerCount());
    }

    @Override
    protected int progressSignal() {
        // Rotations dominate, with ripostes and cut chains counting underneath them, so a group grinding
        // through the phase honestly keeps resetting the floor-lock clock even between hand-offs.
        return fight.duel().rotations() * 100 + fight.duel().ripostes() * 10 + fight.court().chainsBroken();
    }

    @Override
    protected Component readoutText() {
        if (solo()) {
            int done = Math.min(fight.duel().ripostes(), Math.max(1, fight.config().num("court-solo-ripostes", 2)));
            return Component.text("ripostes " + done + "/"
                    + Math.max(1, fight.config().num("court-solo-ripostes", 2)), NamedTextColor.RED);
        }
        return Component.text("rotation " + Math.min(fight.duel().distinctHolders(), required())
                + "/" + required(), NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        if (solo()) {
            int target = Math.max(1, fight.config().num("court-solo-ripostes", 2));
            return Math.min(1.0, (double) fight.duel().ripostes() / target);
        }
        return Math.min(1.0, (double) fight.duel().distinctHolders() / required());
    }
}
