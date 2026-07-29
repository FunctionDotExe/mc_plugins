package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * "Arm Sweep": on melee cluster, it winds an arm back horizontally across a visible arc — batch-2
 * §2.4. Used by both the ground-brawl phases ({@code SiegePhase} and {@code CollapsePhase}); the
 * table lists it as a whole-fight tell with a fixed arc rather than a single phase's signature, and
 * P4's own punish text ("no discipline about the last sweeps") confirms it is still live there.
 * <p>
 * Deliberately reactive rather than on a flat timer — it only arms once enough players are actually
 * standing in its melee range, so a group that fights at range never eats it for free, and a group
 * that face-tanks in a pile gets exactly the anti-clump tax the design calls for.
 */
final class ArmSweep {

    private final ColossusFight fight;
    private int cooldownTicksLeft;
    private boolean telegraphing;
    private int telegraphTicksLeft;
    private Vector aim = new Vector(1, 0, 0);

    ArmSweep(ColossusFight fight) {
        this.fight = fight;
    }

    void reset() {
        cooldownTicksLeft = fight.config().num("arm-sweep-cooldown-ticks", 90);
        telegraphing = false;
    }

    void pulse(int intervalTicks) {
        if (telegraphing) {
            telegraphTicksLeft -= intervalTicks;
            drawTelegraph();
            if (telegraphTicksLeft <= 0) {
                land();
                telegraphing = false;
                cooldownTicksLeft = fight.config().num("arm-sweep-cooldown-ticks", 90);
            }
            return;
        }
        cooldownTicksLeft -= intervalTicks;
        if (cooldownTicksLeft > 0 || !clustered()) {
            return;
        }
        beginTelegraph();
    }

    private boolean clustered() {
        int required = Math.min(fight.config().num("arm-sweep-cluster-size", 2), Math.max(1, fight.playerCount()));
        double meleeRange = fight.config().dbl("arm-sweep-melee-range", 4.5);
        Location at = fight.instance().entity().getLocation();
        int count = 0;
        for (Player player : fight.combatants()) {
            if (flat(player.getLocation(), at) <= meleeRange) {
                count++;
            }
        }
        return count >= required;
    }

    private void beginTelegraph() {
        telegraphing = true;
        telegraphTicksLeft = fight.config().num("arm-sweep-telegraph-ticks", 24);
        Location at = fight.instance().entity().getLocation();
        Vector direction = at.getDirection().setY(0);
        aim = direction.lengthSquared() > 1.0E-4 ? direction.normalize() : new Vector(1, 0, 0);
        Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 0.5f);
        Fx.coloredBurst(at.clone().add(0, 1.6, 0), ColossusFight.SOLAR_GOLD, 2.0f, 30, 0.5);
    }

    private void drawTelegraph() {
        Location at = fight.instance().entity().getLocation();
        double radius = fight.config().dbl("arm-sweep-radius", 5.0);
        double halfAngle = Math.toRadians(fight.config().dbl("arm-sweep-half-angle-degrees", 70.0));
        for (int i = -1; i <= 1; i++) {
            double a = Math.atan2(aim.getZ(), aim.getX()) + i * halfAngle;
            Location end = at.clone().add(Math.cos(a) * radius, 0.3, Math.sin(a) * radius);
            Fx.line(at.clone().add(0, 0.3, 0), end, Particle.CRIT, 12);
        }
    }

    private void land() {
        Location at = fight.instance().entity().getLocation();
        double radius = fight.config().dbl("arm-sweep-radius", 5.0);
        double halfAngle = Math.toRadians(fight.config().dbl("arm-sweep-half-angle-degrees", 70.0));
        double damage = fight.config().dbl("arm-sweep-damage", 12.0);
        for (Player player : fight.combatants()) {
            Vector toPlayer = player.getLocation().toVector().subtract(at.toVector()).setY(0);
            double dist = toPlayer.length();
            if (dist > radius || dist < 1.0E-3) {
                continue;
            }
            if (toPlayer.normalize().angle(aim) > halfAngle) {
                continue;
            }
            player.damage(damage, fight.instance().entity());
            Vector out = toPlayer.clone().multiply(1.7).setY(0.45);
            player.setVelocity(out);
            fight.plugin().actionBarHub().flash(player,
                    Component.text("SWEPT ASIDE", NamedTextColor.RED), 1600L,
                    dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        Fx.coloredRing(at, ColossusFight.SOLAR_GOLD, 2.0f, radius, 40, 0);
        Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
    }

    private static double flat(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
