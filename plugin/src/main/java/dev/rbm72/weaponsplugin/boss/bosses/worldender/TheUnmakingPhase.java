package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

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
            if (angularDistance(player, centre) <= HIT_ARC_DEGREES) {
                player.damage(damage, instance.entity());
                Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), WorldenderFight.UNMAKING, 1.6f, 20, 0.5);
            }
        }
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
