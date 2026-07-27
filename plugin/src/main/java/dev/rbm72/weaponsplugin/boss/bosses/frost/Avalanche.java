package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * P2's moving ceiling-fall: a band of real falling ice sweeps across the arena along one axis, and
 * wherever it lands the floor is gone — converted to real {@link Material#POWDER_SNOW}, which is a
 * vanilla hazard already (sink and freeze fast, unless you're wearing leather boots) rather than
 * something this class has to simulate. Batch-1 §2.3: "combined with sliding, the fight becomes
 * route-planning at speed".
 * <p>
 * One sweep from one edge of the arena to the other is "one full Avalanche cycle" — P2's exit condition
 * counts full sweeps, and {@link #reset()} rearms a fresh one every time the phase (re)starts.
 */
final class Avalanche {

    private boolean active;
    private boolean axisIsX;
    private double position;
    private int cyclesCompleted;
    private long nextDropAtMs;

    private final FrostFight fight;

    Avalanche(FrostFight fight) {
        this.fight = fight;
    }

    int cyclesCompleted() {
        return cyclesCompleted;
    }

    /** Rearms a fresh sweep — called from P2's {@code onArm}, so every entry into the phase gets one. */
    void reset() {
        active = true;
        axisIsX = ThreadLocalRandom.current().nextBoolean();
        position = -fight.instance().arena().radius();
        cyclesCompleted = 0;
        nextDropAtMs = 0L;
    }

    void stop() {
        active = false;
    }

    void pulse(int intervalTicks) {
        if (!active) {
            return;
        }
        double speed = fight.config().dbl("avalanche-speed-blocks-per-second", 2.2);
        position += speed * (intervalTicks / 20.0);

        Location centre = fight.instance().arena().center();
        double radius = fight.instance().arena().radius();
        double bandWidth = fight.config().dbl("avalanche-band-width", 5.0)
                + Math.max(0, fight.playerCount() - 2) * 1.5;

        telegraphBand(centre, radius, bandWidth);

        long now = System.currentTimeMillis();
        if (now >= nextDropAtMs) {
            nextDropAtMs = now + fight.config().num("avalanche-drop-interval-ms", 500);
            dropAlongBand(centre, radius, bandWidth);
        }

        if (position > radius) {
            active = false;
            cyclesCompleted++;
        }
    }

    private void telegraphBand(Location centre, double radius, double bandWidth) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        double along = position;
        for (double perp = -radius; perp <= radius; perp += 2.0) {
            Location spot = axisIsX
                    ? centre.clone().add(along, 0.2, perp)
                    : centre.clone().add(perp, 0.2, along);
            spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 0.3);
            Telegraph.dangerZone(spot, bandWidth / 2.0);
        }
    }

    private void dropAlongBand(Location centre, double radius, double bandWidth) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        int samples = Math.max(3, (int) (radius / 3));
        for (int i = 0; i < samples; i++) {
            double perp = -radius + (2.0 * radius * i) / (samples - 1.0);
            double jitter = ThreadLocalRandom.current().nextDouble(-bandWidth / 2, bandWidth / 2);
            double along = position + jitter;
            Location ground = axisIsX
                    ? centre.clone().add(along, 0, perp)
                    : centre.clone().add(perp, 0, along);
            ground.setY(world.getHighestBlockYAt(ground.getBlockX(), ground.getBlockZ()));
            Location dropFrom = ground.clone().add(0, 14, 0);
            double damage = fight.config().dbl("avalanche-damage", 7.0);
            Grief.dropAsBlock(fight.griefContext(), dropFrom, Material.PACKED_ICE, 0.0, damage, 2.2,
                    landed -> punchHole(landed));
        }
        Fx.sound(centre, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
    }

    /** Where the ice actually lands: the floor there is gone, replaced with a real powder-snow pit. */
    private void punchHole(Location landed) {
        World world = landed.getWorld();
        if (world == null) {
            return;
        }
        var block = world.getBlockAt(landed.getBlockX(), landed.getBlockY(), landed.getBlockZ());
        Grief.setBlock(fight.griefContext(), block, Material.POWDER_SNOW);
        Fx.burst(landed.clone().add(0.5, 0.3, 0.5), org.bukkit.Particle.SNOWFLAKE, 20, 0.5);
    }
}
