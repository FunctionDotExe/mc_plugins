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

/** A high-pressure jet of real water blocks fired in a straight line toward the target: line damage + knockback. */
public final class WaterJetAttack extends BossAttack {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);

    private final double damage;
    private final double range;
    private final double hitRadius;
    private final double knockback;
    private final int telegraphTicks;

    public WaterJetAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.damage = configDouble("water-jet-damage", 10.0);
        this.range = configDouble("water-jet-range", 12.0);
        this.hitRadius = configDouble("water-jet-hit-radius", 2.0);
        this.knockback = configDouble("water-jet-knockback", 1.2);
        this.telegraphTicks = configInt("water-jet-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Water Jet";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("water-jet-cooldown-seconds", 7.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation().add(0, 1, 0);
        Vector dir = ctx.target().getLocation().add(0, 1, 0).toVector().subtract(origin.toVector()).setY(0);
        if (dir.lengthSquared() < 1.0e-4) {
            dir = ctx.boss().getLocation().getDirection().setY(0);
        }
        if (dir.lengthSquared() < 1.0e-4) {
            dir = new Vector(1, 0, 0);
        }
        final Vector direction = dir.normalize();
        Location end = origin.clone().add(direction.clone().multiply(range));

        sequence(telegraphTicks,
                () -> {
                    Telegraph.line(origin, end, Particle.BUBBLE);
                    Fx.coloredRing(origin, TEAL, 1.2f, 1.15, 18, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.tide_leviathan.water_jet", Sound.ITEM_TRIDENT_RIPTIDE_2, 1.15f, 0.8f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_SPLASH, 1.1f, 0.7f);
                    Fx.line(origin, end, Particle.BUBBLE, 76);
                    Fx.line(origin, end, Particle.SPLASH, 76);
                    Fx.coloredBurst(end, TEAL, 1.6f, 40, 0.5);
                    end.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, end, 96, 0.96, 0.96, 0.96, 0.1);
                    // Real water-block props along the jet's path instead of splash particles alone.
                    // A cosmetic BlockDisplay (not a placed liquid) — a real WATER source here would
                    // spread and permanently flood the arena on every cast.
                    for (double d = 1.0; d <= range; d += 1.5) {
                        Location base = origin.clone().add(direction.clone().multiply(d));
                        Fx.glowPillar(plugin, base, Material.WATER, 1.0f, 1.3f, 16);
                    }
                    for (Player player : ctx.arena().playersInside()) {
                        Vector toPlayer = player.getLocation().add(0, 1, 0).toVector().subtract(origin.toVector()).setY(0);
                        double proj = toPlayer.dot(direction);
                        if (proj < 0 || proj > range) {
                            continue;
                        }
                        double lateral = toPlayer.clone().subtract(direction.clone().multiply(proj)).length();
                        if (lateral > hitRadius) {
                            continue;
                        }
                        player.damage(damage, ctx.boss());
                        player.setVelocity(player.getVelocity().add(direction.clone().multiply(knockback).setY(0.35)));
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                    }
                },
                12, onComplete);
    }
}
