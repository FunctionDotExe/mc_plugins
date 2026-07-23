package dev.rbm72.weaponsplugin.boss.telegraph;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Shared wind-up indicator shapes, built entirely on the existing
 * {@code fx.Fx} helpers — no new particle plumbing. Every {@code BossAttack}
 * calls one of these once per telegraph tick so players get a clear, honest
 * warning before anything actually hits.
 */
public final class Telegraph {

    private Telegraph() {
    }

    private static final Color DANGER_RED = Color.fromRGB(180, 0, 0);
    private static final Color DANGER_EMBER = Color.fromRGB(255, 90, 0);
    private static final Color SAFE_BLUE = Color.fromRGB(60, 140, 255);

    public static void groundRing(Location center, double radius, Particle particle) {
        Fx.ring(center, particle, radius, 24);
    }

    /**
     * Pulsing red-dust danger zone — "don't stand here". A crisp {@code DANGER_EMBER} ring is
     * layered exactly on {@code radius} so the boundary reads as an exact line, not just a guess
     * at where the fuzzy dust cloud thins out — the fill alone left players unsure if they were
     * one step outside it or not.
     */
    public static void dangerZone(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(DANGER_RED, 2.56f);
        world.spawnParticle(Particle.DUST, center, 90, radius * 0.8, 0.24, radius * 0.8, 0, dust);
        Fx.coloredRing(center, DANGER_EMBER, 1.6f, radius, 28, 0);
    }

    /**
     * Same shape as {@link #dangerZone} but its ring/fill intensity ramps up as {@code progress}
     * (0 at telegraph start, 1 at the tick it lands) approaches 1 — a static-looking warning read as
     * "somewhere in this window" where a tightening one reads as "any moment now, get out". Opt-in:
     * existing call sites keep the flat-intensity {@link #dangerZone} untouched.
     */
    public static void dangerZone(Location center, double radius, double progress) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double clamped = Math.max(0, Math.min(1, progress));
        int points = (int) Math.round(20 + clamped * 20);
        float size = (float) (1.6 + clamped * 1.2);
        Particle.DustOptions dust = new Particle.DustOptions(DANGER_RED, (float) (2.0 + clamped * 1.2));
        world.spawnParticle(Particle.DUST, center, (int) (60 + clamped * 60), radius * 0.8, 0.24, radius * 0.8, 0, dust);
        Fx.coloredRing(center, DANGER_EMBER, size, radius, points, 0);
    }

    /** Pulsing blue-dust safe zone — "stand here instead" (paired with {@link #dangerZone} for attacks that leave shelters). */
    public static void safeZone(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(SAFE_BLUE, 2.56f);
        world.spawnParticle(Particle.DUST, center, 70, radius * 0.8, 0.24, radius * 0.8, 0, dust);
        Fx.coloredRing(center, SAFE_BLUE, 1.4f, radius, 20, 0);
        Fx.ring(center, Particle.END_ROD, radius, 16);
    }

    public static void line(Location from, Location to, Particle particle) {
        Fx.line(from, to, particle, 14);
    }

    /**
     * A fan of particles ahead of {@code origin} along {@code direction}, {@code angleDegrees} wide.
     * The two edge rays are traced denser than the interior fill so the cone's exact boundary — the
     * line between "safe" and "about to get hit" — stays readable instead of blurring into one
     * indistinct wedge of dots.
     */
    public static void cone(Location origin, Vector direction, double angleDegrees, double range, Particle particle) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Vector flat = direction.clone().setY(0);
        // Aiming straight up/down zeroes the horizontal component — can't normalize that.
        Vector flatDirection = flat.lengthSquared() > 1.0E-6 ? flat.normalize() : new Vector(1, 0, 0);
        double half = angleDegrees / 2;
        double step = Math.max(5.0, angleDegrees / 4);
        for (double d = 1; d <= range; d += 1.0) {
            for (double angle = -half; angle <= half; angle += step) {
                Vector rotated = rotateY(flatDirection, Math.toRadians(angle));
                Location point = origin.clone().add(rotated.multiply(d));
                spawnConePoint(world, particle, point);
            }
            // Edge rays get an extra point each so the cone's border reads as a solid line even
            // where the angular step above lands short of exactly ±half.
            for (double edge : new double[] {-half, half}) {
                Vector rotated = rotateY(flatDirection, Math.toRadians(edge));
                Location point = origin.clone().add(rotated.multiply(d));
                spawnConePoint(world, particle, point);
            }
        }
    }

    /**
     * {@link Particle#DRAGON_BREATH} (used by Voidwyrm's Void Breath cone) requires a {@code Float}
     * data value per-call — same rule {@code Fx.dragonBreathBurst}/{@code dragonBreathLine} already
     * special-case. Calling it through the plain data-less {@code spawnParticle} overload below threw
     * every single cast, aborting the rest of the attack (damage/nausea/grief) before it ever ran.
     */
    private static void spawnConePoint(World world, Particle particle, Location point) {
        if (particle == Particle.DRAGON_BREATH) {
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0, 1.0f);
        } else {
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    /** A reticle over {@code target}'s head — a ring plus a falling marker beam, easier to spot mid-fight than a bare point burst. */
    public static void targetMarker(LivingEntity target) {
        Location head = target.getLocation().add(0, 2.2, 0);
        Fx.point(head, Particle.END_ROD, 3);
        Fx.ring(head, Particle.END_ROD, 0.6, 10);
        target.getWorld().spawnParticle(Particle.END_ROD, head.clone().add(0, -1.0, 0), 6, 0, 0.5, 0, 0.01);
    }

    private static Vector rotateY(Vector v, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
