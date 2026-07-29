package dev.rbm72.weaponsplugin.boss.bosses.storm;

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
 * <b>P3 — Eye of the Storm.</b> He ascends and is fed by four Storm Pylons; while any stands, only
 * currently-discharged players land a real hit on him (see the header on {@code StormPhaseMechanic} for
 * why that is a damage rule and not another invulnerability wall). Meanwhile a rolling band of strikes
 * sweeps the arena with one rotating gap — a real batch-1 §3.3/§3.4 "Rolling Barrage": "a visible
 * advancing wall of strikes with a clear gap".
 * <p>
 * Exit requires two pylons destroyed (one solo) on top of the health threshold — batch-1 §3.3: they are
 * strong-but-optional, permanently removing the discharge restriction one at a time rather than gating
 * the whole phase behind all four.
 */
final class EyeOfTheStormPhase extends StormPhaseMechanic {

    private double corridorAngle;
    private int direction = 1;

    EyeOfTheStormPhase(BossInstance instance, double exitFraction) {
        super(instance, "Eye of the Storm", exitFraction);
    }

    @Override
    protected void onArm() {
        // Rods lost in P2 don't respawn until "next phase" (§3.4) — without this, a group that lost every
        // rod earlier would carry zero rods into the one phase whose entire damage rule depends on being
        // able to discharge at all.
        fight.rods().raiseAll();
        fight.pylons().build();
        corridorAngle = ThreadLocalRandom.current().nextDouble(0, 360.0);
    }

    /**
     * The one damage rule in this rework's roster that filters on the <em>attacker's own state</em>
     * rather than on position or timing — while a pylon still feeds him, an undischarged hit is cut to
     * almost nothing rather than to zero, for the same "still reads as connecting" reason the Fallen
     * King's facing rule does.
     */
    @Override
    public double filterDamage(Player attacker, double damage) {
        if (!fight.pylons().anyStanding()) {
            return damage;
        }
        return fight.isDischarged(attacker) ? damage : damage * fight.config().dbl("eye-undischarged-fraction", 0.05);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        double degreesPerSecond = fight.config().dbl("barrage-degrees-per-second", 20.0);
        corridorAngle = wrap(corridorAngle + direction * degreesPerSecond * (intervalTicks / 20.0));

        double halfWidth = fight.config().dbl("barrage-half-width-degrees", 26.0);
        Location centre = instance.entity().getLocation();
        double radius = instance.arena().radius();
        drawCorridor(centre, radius, halfWidth);

        if (elapsedTicks % 20 == 0) {
            double damage = fight.config().dbl("barrage-damage-per-second", 5.0);
            for (Player player : fight.combatants()) {
                if (inCorridor(player, centre, halfWidth)) {
                    continue;
                }
                tickHurt(player, damage);
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), StormFight.STORM_WHITE, 1.4f, 14, 0.4);
            }
        }
    }

    private boolean inCorridor(Player player, Location centre, double halfWidth) {
        double bearing = Math.toDegrees(Math.atan2(
                player.getLocation().getZ() - centre.getZ(), player.getLocation().getX() - centre.getX()));
        double delta = Math.abs(((bearing - corridorAngle + 540) % 360) - 180);
        return delta <= halfWidth;
    }

    private void drawCorridor(Location centre, double radius, double halfWidth) {
        for (int side = -1; side <= 1; side += 2) {
            double edge = Math.toRadians(corridorAngle + side * halfWidth);
            Location from = centre.clone().add(0, 0.4, 0);
            Location to = centre.clone().add(Math.cos(edge) * radius, 0.4, Math.sin(edge) * radius);
            Fx.line(from, to, Particle.END_ROD, 18);
        }
    }

    private static double wrap(double degrees) {
        double wrapped = degrees % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped;
    }

    @Override
    protected boolean objectiveMet() {
        int required = fight.playerCount() <= 1 ? 1 : 2;
        return fight.pylons().destroyedCount() >= required;
    }

    /** Partial pylon damage counts too — see {@code StormPylons#damageProgress}. */
    @Override
    protected int progressSignal() {
        return (int) Math.round(fight.pylons().damageProgress() * 1000);
    }

    @Override
    protected Component readoutText() {
        int required = fight.playerCount() <= 1 ? 1 : 2;
        if (fight.pylons().destroyedCount() >= required) {
            return Component.text("the sky answers to you now", NamedTextColor.GREEN);
        }
        if (fight.pylons().anyStanding()) {
            return Component.text("only the discharged can hurt him — " + fight.pylons().destroyedCount()
                    + "/" + required + " pylons down", NamedTextColor.RED);
        }
        return Component.text(fight.pylons().destroyedCount() + "/" + required + " pylons down", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        int required = fight.playerCount() <= 1 ? 1 : 2;
        return Math.min(1.0, (double) fight.pylons().destroyedCount() / required);
    }
}
