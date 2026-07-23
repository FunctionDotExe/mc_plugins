package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Two perpendicular sunbeams (real glowing block props along each arm) sweep out from the colossus, carving a burning cross across the arena. */
public final class RadiantBeamCrossAttack extends BossAttack {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);

    private final double damage;
    private final double range;
    private final double width;
    private final int telegraphTicks;

    public RadiantBeamCrossAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("radiant-cross-damage", 13.0);
        this.range = configDouble("radiant-cross-range", 16.0);
        this.width = configDouble("radiant-cross-width", 2.6);
        this.telegraphTicks = configInt("radiant-cross-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Radiant Beam Cross";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("radiant-cross-cooldown-seconds", 13.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.bossLocation().add(0, 1.2, 0);
        Vector aim = ctx.target().getLocation().toVector().subtract(ctx.bossLocation().toVector()).setY(0);
        if (aim.lengthSquared() < 1.0e-4) {
            aim = ctx.boss().getLocation().getDirection().setY(0);
        }
        final Vector axisA = aim.normalize();
        final Vector axisB = new Vector(-axisA.getZ(), 0, axisA.getX()).normalize();
        final Vector[] arms = {axisA, axisA.clone().multiply(-1), axisB, axisB.clone().multiply(-1)};
        final Location beamOrigin = center.clone();

        sequence(telegraphTicks,
                () -> {
                    for (Vector arm : arms) {
                        Telegraph.line(beamOrigin, beamOrigin.clone().add(arm.clone().multiply(range)), Particle.END_ROD);
                    }
                    Fx.coloredRing(ctx.bossLocation(), SOLAR_GOLD, 1.6f, 2.2, 32, 0);
                },
                () -> {
                    for (Vector arm : arms) {
                        Location end = beamOrigin.clone().add(arm.clone().multiply(range));
                        Fx.line(beamOrigin, end, Particle.END_ROD, 54);
                        Fx.line(beamOrigin, end, Particle.FLAME, 30);
                        Fx.coloredBurst(end, SOLAR_GOLD, 1.6f, 34, 0.5);
                        // Solid glowing beam props along each arm instead of particles alone.
                        for (double d = 2.0; d <= range; d += 5.0) {
                            Location base = beamOrigin.clone().add(arm.clone().multiply(d));
                            Fx.glowPillar(plugin, base, Material.GLOWSTONE, 0.4f, 6f, 16);
                        }
                    }
                    origin(beamOrigin);
                    BossAudio.play(beamOrigin, "boss.solar_colossus.radiant_cross", Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.9f);
                    Fx.sound(beamOrigin, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
                    for (Player player : ctx.arena().playersInside()) {
                        Location p = player.getLocation().add(0, 1, 0);
                        boolean hit = false;
                        for (Vector arm : arms) {
                            if (nearSegment(p, beamOrigin, arm, range, width)) {
                                hit = true;
                                break;
                            }
                        }
                        if (hit) {
                            player.damage(damage, ctx.boss());
                            player.setFireTicks(60);
                            Fx.coloredBurst(player.getLocation().add(0, 1, 0), SOLAR_GOLD, 1.3f, 14, 0.4);
                            Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                        }
                    }
                },
                16, onComplete);
    }

    private static void origin(Location center) {
        Fx.flash(center, 2);
    }

    private static boolean nearSegment(Location p, Location from, Vector dir, double length, double width) {
        Vector ap = p.toVector().subtract(from.toVector());
        double t = ap.dot(dir);
        if (t < 0 || t > length) {
            return false;
        }
        Vector closest = from.toVector().add(dir.clone().multiply(t));
        return closest.distanceSquared(p.toVector()) <= width * width;
    }
}
