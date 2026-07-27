package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P3 — Reanimation.</b> The arena fills with the fight's own dead, and the group's efficiency is the
 * difficulty.
 * <p>
 * The sun the group bought in P2 stays up here, which sounds like a reward and is in fact the trap: undead
 * burning to death in the open produce corpse piles just as surely as ones the group cuts down, and every
 * pile is real bone blocks physically in the way. Piles get back up on a timer, and the only way to stop one
 * is to mine it out — which competes for exactly the same seconds as fighting does.
 * <p>
 * So the phase asks a question the genre almost never asks: <em>should you stop killing things?</em> A
 * five-player group clearing adds at full speed buries its own chokepoint in under a minute. The exit is not
 * a kill count and not a damage number — it is a floor with fewer than a handful of piles left standing,
 * which a group can only reach by putting weapons away and picking up a pickaxe.
 */
public final class ReanimationPhase extends NecroPhaseMechanic {

    public ReanimationPhase(BossInstance instance, double exitFraction) {
        super(instance, "Reanimation", exitFraction);
    }

    @Override
    protected void onArm() {
        // The canopy is down and P2's daylight is the state the group earned. Kept explicitly rather than
        // inherited, so the phase plays the same whether the shroud happened to be mid-rebuild when the
        // band changed.
        fight.letTheSunIn();
        if (fight.needsArtificialSun()) {
            fight.horde().scorch();
        }
        instance.showTitle(
                Component.text("REANIMATION", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Your dead are getting up — mine them out", NamedTextColor.GRAY));
    }

    @Override
    protected double corpseRiseMultiplier() {
        return fight.config().dbl("corpse-rise-multiplier-reanimation", 1.6);
    }

    /**
     * Two conditions, and the first one matters as much as the second: a group that has barely killed
     * anything has an empty floor by default, and clearing a floor that never filled is not the mechanic.
     */
    @Override
    protected boolean objectiveMet() {
        return fight.corpses().buriedTotal() >= minimumBuried()
                && fight.corpses().liveCount() <= clearThreshold();
    }

    /**
     * Piles mined out, not piles remaining. The floor emptying because its dead stood up is the boss
     * winning, and the valve must not read that as the group working.
     */
    @Override
    protected int progressSignal() {
        return fight.corpses().clearedTotal();
    }

    @Override
    protected Component readoutText() {
        int live = fight.corpses().liveCount();
        if (fight.corpses().buriedTotal() < minimumBuried()) {
            return Component.text("the floor is still filling", NamedTextColor.GRAY);
        }
        return Component.text(live + " piles standing — clear to " + clearThreshold(),
                objectiveMet() ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        int live = fight.corpses().liveCount();
        int threshold = clearThreshold();
        if (live <= threshold) {
            return 1.0;
        }
        return (double) threshold / live;
    }

    /**
     * Piles the fight must have raised before "the floor is clear" means anything, scaled by group size
     * for the same reason the threshold below is: a five-player group buries eight piles in seconds, and
     * a gate that trivial is not a gate.
     */
    private int minimumBuried() {
        return Math.max(1, fight.config().num("corpse-floor-minimum-buried", 8)
                + fight.config().num("corpse-floor-minimum-buried-per-extra-player", 4)
                * (fight.playerCount() - 1));
    }

    /**
     * How few piles count as a cleared floor — <b>count</b> scaling, up per extra player.
     * <p>
     * A fixed threshold is the wrong shape here and it fails in the harsh direction. Five players bury
     * the floor several times faster than one, so asking every group for the same two-pile floor asks the
     * big group to out-mine an intake several times its own; the phase would read as a wall to a full
     * group and a formality to a solo player. Scaling the target keeps the ask "clear most of what you
     * made" at every group size, which is the mechanic the design describes. The pressure a bigger group
     * gets is still real — there are simply more piles in the way of everything else it is doing.
     */
    private int clearThreshold() {
        return Math.max(0, fight.config().num("corpse-floor-clear-threshold", 2)
                + fight.config().num("corpse-floor-clear-threshold-per-extra-player", 1)
                * (fight.playerCount() - 1));
    }
}
