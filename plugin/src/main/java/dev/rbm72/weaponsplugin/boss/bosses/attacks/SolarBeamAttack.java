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

/** A searing line-AoE beam of sunlight: telegraphs a lane, then scorches everyone standing along it. */
public final class SolarBeamAttack extends BossAttack {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);

    private final double damage;
    private final double range;
    private final double width;
    private final int telegraphTicks;

    public SolarBeamAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("solar-beam-damage", 14.0);
        this.range = configDouble("solar-beam-range", 18.0);
        this.width = configDouble("solar-beam-width", 2.6);
        this.telegraphTicks = configInt("solar-beam-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Solar Beam";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("solar-beam-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location from = ctx.bossLocation().add(0, 1.2, 0);
        Vector dir = ctx.target().getLocation().add(0, 1.0, 0).toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1.0e-4) {
            dir = ctx.boss().getLocation().getDirection();
        }
        dir = dir.normalize();
        final Vector beamDir = dir;
        final Location beamFrom = from.clone();
        final Location beamTo = from.clone().add(dir.clone().multiply(range));

        sequence(telegraphTicks,
                () -> {
                    Telegraph.line(beamFrom, beamTo, Particle.END_ROD);
                    Fx.coloredRing(ctx.bossLocation(), SOLAR_GOLD, 1.6f, 2.3, 30, 0);
                },
                () -> {
                    Fx.line(beamFrom, beamTo, Particle.END_ROD, 64);
                    Fx.line(beamFrom, beamTo, Particle.FLAME, 40);
                    Fx.coloredBurst(beamTo, SOLAR_GOLD, 1.8f, 50, 0.8);
                    Fx.burst(beamTo, Particle.FLAME, 24, 0.5);
                    Fx.flash(beamTo, 2);
                    // Solid scorch props punched along the beam lane.
                    for (double d = 2.0; d <= range; d += 5.0) {
                        Location base = beamFrom.clone().add(beamDir.clone().multiply(d));
                        Fx.glowPillar(plugin, base, Material.GLOWSTONE, 0.4f, 6f, 16);
                    }
                    BossAudio.play(beamFrom, "boss.solar_colossus.solar_beam", Sound.BLOCK_BEACON_ACTIVATE, 1.35f, 0.7f);
                    Fx.sound(beamFrom, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.4f);
                    for (Player player : ctx.arena().playersInside()) {
                        if (nearSegment(player.getLocation().add(0, 1, 0), beamFrom, beamDir, range, width)) {
                            player.damage(damage, ctx.boss());
                            player.setFireTicks(60);
                            Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }

    /** True when {@code p} lies within {@code width} of the segment [from, from + dir*length] (dir unit). */
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
