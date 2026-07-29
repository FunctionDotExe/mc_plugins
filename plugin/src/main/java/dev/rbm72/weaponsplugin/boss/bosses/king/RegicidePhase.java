package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * <b>P2 — Regicide.</b> The crown comes apart into three real items on the floor, and the fight turns
 * into a relay run under pursuit.
 * <p>
 * Two things change at once, and they are designed to pull in opposite directions. The King starts
 * <b>hunting whoever is carrying a shard</b> — not the weakest player, the carrier — so the person doing
 * the objective is the person he is chasing. And while any shard is loose he <b>reflects</b> a share of
 * what he takes, so the group's default answer of "everyone keeps swinging" is actively self-harming.
 * The way through is for the Challenger to stop duelling and start <em>pulling him away</em>, which is
 * the same player doing the same job as P1 with the goal inverted.
 * <p>
 * The phase cannot end until all three shards are seated. Burst does not open it: {@code recordExposure}
 * is not called until the throne is whole, so the health seam stays pinned however hard the group hits.
 */
final class RegicidePhase extends KingPhaseMechanic {

    RegicidePhase(BossInstance instance, double exitFraction) {
        super(instance, "Regicide", exitFraction);
    }

    /** How much a solo King's pursuit speed is cut — §1.5: "a solo relay is possible with good kiting". */
    private static final double SOLO_PURSUIT_SPEED_MULTIPLIER = 0.75;

    private boolean speedReduced;

    @Override
    protected void onArm() {
        fight.judgment().setRunning(false);
        fight.shards().scatter();
        // He goes after the crown, not the weakest player. Supplied as a live query rather than a fixed
        // player because the shard changes hands constantly — that is what the hand-offs are for, and a
        // pursuit that kept chasing whoever picked it up first would make throwing it pointless.
        instance.setTargetOverride(this::hunted);
        if (solo()) {
            AttributeInstance speed = instance.entity().getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * SOLO_PURSUIT_SPEED_MULTIPLIER);
                speedReduced = true;
            }
        }
    }

    @Override
    protected void onDisarm() {
        if (!speedReduced) {
            return;
        }
        AttributeInstance speed = instance.entity().getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() / SOLO_PURSUIT_SPEED_MULTIPLIER);
        }
        speedReduced = false;
    }

    /**
     * The carrier he is currently running down: the nearest one, so a group spreading its carriers out
     * genuinely splits his attention rather than letting him lock onto an arbitrary one across the room.
     */
    private Player hunted() {
        List<Player> carriers = fight.shards().carriers();
        if (carriers.isEmpty()) {
            return null;
        }
        Player nearest = null;
        double best = Double.MAX_VALUE;
        var at = instance.entity().getLocation();
        for (Player carrier : carriers) {
            if (carrier.getWorld() == null || !carrier.getWorld().equals(at.getWorld())) {
                continue;
            }
            double distance = carrier.getLocation().distanceSquared(at);
            if (distance < best) {
                best = distance;
                nearest = carrier;
            }
        }
        return nearest;
    }

    @Override
    protected boolean objectiveMet() {
        return fight.shards().allSeated();
    }

    @Override
    protected int progressSignal() {
        return fight.shards().seated() * 100 + fight.court().chainsBroken();
    }

    @Override
    protected Component readoutText() {
        int seated = fight.shards().seated();
        int total = Math.max(1, fight.shards().total());
        if (seated >= total) {
            return Component.text("the crown is whole", NamedTextColor.GREEN);
        }
        return Component.text("shards " + seated + "/" + total + " seated — he reflects while any is loose",
                NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return (double) fight.shards().seated() / Math.max(1, fight.shards().total());
    }
}
