package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — The Shroud.</b> He closes the sky with a real block canopy and the group has to tear it open.
 * <p>
 * This is the phase the whole boss exists for. The counter is not a damage check or a timing window, it is
 * changing the weather over your own head: bring the canopy's anchors down and vanilla's own rule — undead
 * burn in daylight — resolves the horde problem for you, all at once, in front of everyone. He re-knits it
 * afterwards, so holding the sky open is upkeep rather than a switch.
 * <p>
 * The trip upward is where the group splits, and the anchors are deliberately out of melee reach: this is
 * the one job a bow or a stack of scaffolding does better than a sword, and both are on the arena floor.
 * With two players it is a genuine act of trust — one of you is leaving the other alone with the horde.
 * Solo gets two anchors instead of four, so one player can make the climb between waves.
 */
public final class ShroudPhase extends NecroPhaseMechanic {

    private final Shroud shroud;

    public ShroudPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Shroud", exitFraction);
        this.shroud = new Shroud(fight);
    }

    @Override
    protected void onArm() {
        shroud.raise();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        shroud.pulse(intervalTicks);
    }

    @Override
    protected void onDisarm() {
        // Takes the canopy back down on the way out. A phase that handed P3 a roof it never asked for
        // would leave the daylight the group just earned switched off by leftover scenery.
        shroud.discard();
    }

    @Override
    protected boolean objectiveMet() {
        return shroud.breaks() >= Math.max(1, fight.config().num("shroud-breaks-to-advance", 1));
    }

    /**
     * Anchors cut, plus a full band per shroud break so the count still rises across a rebuild — he puts
     * every anchor back, which resets "standing" and would otherwise read as the group losing ground for
     * having solved the phase once.
     */
    @Override
    protected int progressSignal() {
        return shroud.breaks() * 1000 + Math.max(0, shroud.anchorsPlaced() - shroud.anchorsStanding());
    }

    @Override
    protected Component readoutText() {
        if (!shroud.isUp()) {
            int seconds = shroud.rebuildTicksLeft() / 20;
            return Component.text("DAYLIGHT — he re-knits it in " + seconds + "s", NamedTextColor.GOLD);
        }
        return Component.text("anchors " + shroud.anchorsStanding() + "/" + shroud.anchorsPlaced()
                + " standing — they are up high", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        if (!shroud.isUp()) {
            return 1.0;
        }
        int placed = Math.max(1, shroud.anchorsPlaced());
        return (double) (placed - shroud.anchorsStanding()) / placed;
    }
}
