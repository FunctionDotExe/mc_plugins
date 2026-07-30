package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * <b>P8 — The Unmaking (&lt;14%) · removes separate telegraphs.</b> Every rule is live at once,
 * permanently: the floor gets one last freeze and one last flood, a handful more rifts tear open — but
 * the arena does not become unreadable. Instead of eight separate telegraphs it becomes one: a single
 * rotating sweep from the arena's fixed centre, visible and audible, that the group either reads and
 * dodges with the seven tools they have been carrying, or does not.
 */
final class TheUnmakingPhase extends WorldenderPhaseMechanic {

    private static final double HIT_ARC_DEGREES = 7.0;

    private double sweepAngleDegrees;
    /**
     * Who the beam is currently standing on, so it can only bill them once per pass.
     * <p>
     * This is membership, not a cooldown, and the difference is the whole point. The sweep advances 6°
     * per pulse through a 14°-wide arc, so a player it crosses is inside that arc for two or three
     * consecutive pulses — and every one of those used to be a separate full-damage hit. A timer would
     * have papered over that; tracking the crossing itself means one hit per pass, however wide the arc
     * or however slow the rotation is later tuned to be. A player leaves this set the moment the beam is
     * off them, so the next pass hits again.
     */
    private final Set<UUID> struckThisPass = new HashSet<>();

    TheUnmakingPhase(BossInstance instance) {
        super(instance, "The Unmaking");
    }

    @Override
    protected void onArm() {
        fight.ice().freeze();
        fight.lava().floodEverything(fight.config().dbl("unmaking-keep-fraction", 0.1));
        double radius = fight.config().dbl("unmaking-rift-radius", 3.0);
        int extraRifts = fight.config().num("unmaking-extra-rifts", 3);
        for (int i = 0; i < extraRifts; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double fraction = ThreadLocalRandom.current().nextDouble(0.3, 0.8);
            Location centre = instance.arena().center();
            double dist = instance.arena().radius() * fraction;
            Location spot = centre.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            var world = fight.world();
            if (world != null) {
                spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()));
                fight.rifts().openOne(spot, radius);
            }
        }
        sweepAngleDegrees = ThreadLocalRandom.current().nextDouble(0, 360);
    }

    @Override
    protected void onDisarm() {
        struckThisPass.clear();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        double degreesPerPulse = fight.config().dbl("unmaking-sweep-degrees-per-pulse", 6.0);
        sweepAngleDegrees = (sweepAngleDegrees + degreesPerPulse) % 360.0;

        Location centre = instance.arena().center();
        double radius = instance.arena().radius();
        double radians = Math.toRadians(sweepAngleDegrees);
        Location end = centre.clone().add(Math.cos(radians) * radius, 0, Math.sin(radians) * radius);
        centre.setY(end.getY() + 1);
        end.setY(end.getY() + 1);
        Fx.line(centre, end, Particle.SOUL_FIRE_FLAME, 40);
        Fx.line(centre, end, Particle.END_ROD, 24);
        Fx.sound(centre, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);

        double damage = fight.config().dbl("unmaking-sweep-damage", 14.0);
        for (Player player : fight.combatants()) {
            boolean inArc = inHitArc(player, centre);
            // Rising edge only: the beam hits you as it sweeps across you, once, and cannot hit you again
            // until it has left and come back around. See struckThisPass for why this is not a throttle.
            boolean entered = inArc && struckThisPass.add(player.getUniqueId());
            if (!inArc) {
                struckThisPass.remove(player.getUniqueId());
            }
            if (!entered) {
                continue;
            }
            player.damage(damage, instance.entity());
            Fx.bloodSpray(player.getLocation().add(0, 1, 0));
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), WorldenderFight.UNMAKING, 1.6f, 20, 0.5);
        }
        // Anyone who left the fight keeps no seat in the set.
        struckThisPass.retainAll(fight.combatants().stream().map(Player::getUniqueId).collect(Collectors.toSet()));
    }

    /**
     * Whether the sweep is currently over this player.
     * <p>
     * The pivot exemption is the important half. Angles are meaningless at the centre of a rotating
     * sweep — every direction is the same direction — and the original reading of that was "always in
     * the arc", which put anyone standing at the pivot permanently inside the beam. The boss stands at
     * the pivot, so that was every melee player in the phase, taking the sweep's full hit on every
     * pulse. Standing where the beam is thinnest is not supposed to be the most lethal spot in the
     * arena; the sweep is a thing you dodge, and at the pivot there is nothing to dodge.
     */
    private boolean inHitArc(Player player, Location centre) {
        double pivotRadius = fight.config().dbl("unmaking-sweep-pivot-radius", 2.0);
        double dx = player.getLocation().getX() - centre.getX();
        double dz = player.getLocation().getZ() - centre.getZ();
        if (dx * dx + dz * dz <= pivotRadius * pivotRadius) {
            return false;
        }
        return angularDistance(player, centre) <= HIT_ARC_DEGREES;
    }

    private double angularDistance(Player player, Location centre) {
        double dx = player.getLocation().getX() - centre.getX();
        double dz = player.getLocation().getZ() - centre.getZ();
        if (dx * dx + dz * dz < 0.25) {
            return 0.0;
        }
        double playerAngle = Math.toDegrees(Math.atan2(dz, dx));
        if (playerAngle < 0) {
            playerAngle += 360.0;
        }
        double diff = Math.abs(playerAngle - sweepAngleDegrees) % 360.0;
        return Math.min(diff, 360.0 - diff);
    }

    @Override
    protected Component readoutText() {
        return Component.text("everything, at once — read the sweep", NamedTextColor.DARK_RED);
    }
}
