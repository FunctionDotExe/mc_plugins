package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** Warden-style directional sonic blast: a telegraphed line, then a devastating beam that damages, knocks back, and craters the ground along its path. */
public final class WardenSonicBoomAttack extends BossAttack {

    private static final Color SONIC = Color.fromRGB(30, 200, 200);

    private final double damage;
    private final double range;
    private final double width;
    private final double knockback;
    private final int telegraphTicks;

    public WardenSonicBoomAttack(WeaponsPlugin plugin) {
        super(plugin, "worldender");
        this.damage = configDouble("sonic-boom-damage", 16.0);
        this.range = configDouble("sonic-boom-range", 16.0);
        this.width = configDouble("sonic-boom-width", 2.0);
        this.knockback = configDouble("sonic-boom-knockback", 1.4);
        this.telegraphTicks = configInt("sonic-boom-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Sonic Boom";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("sonic-boom-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location origin = boss.getLocation().add(0, 1.0, 0);
        Vector aimVector = ctx.target().getLocation().toVector().subtract(origin.toVector()).setY(0);
        // A target standing exactly on the boss's X/Z (common right after a fresh spawn) normalizes
        // to a NaN vector instead of throwing — fall back to an arbitrary horizontal direction.
        Vector direction = aimVector.lengthSquared() > 1.0E-6 ? aimVector.normalize() : new Vector(1, 0, 0);
        Location end = origin.clone().add(direction.clone().multiply(range));

        sequence(telegraphTicks,
                () -> {
                    Telegraph.line(origin, end, Particle.SONIC_BOOM);
                    Fx.line(origin, end, Particle.ELECTRIC_SPARK, 40);
                    Fx.coloredRing(boss.getLocation(), SONIC, 1.4f, 2.3, 30, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.worldender.sonic_boom", Sound.ENTITY_WARDEN_SONIC_BOOM, 1.55f, 0.8f);
                    Fx.sound(origin, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.15f, 0.7f);
                    // The beam itself: a bright core, a spark trail and a crackling shockline.
                    Fx.line(origin, end, Particle.SONIC_BOOM, 34);
                    Fx.line(origin, end, Particle.END_ROD, 64);
                    Fx.coloredBurst(end, SONIC, 1.6f, 24, 0.5);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, end, 6, 0, 0, 0, 0);

                    for (Entity nearby : boss.getNearbyEntities(range, range, range)) {
                        if (!(nearby instanceof LivingEntity target) || nearby.equals(boss)
                                || ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            continue;
                        }
                        Vector toTarget = target.getLocation().toVector().subtract(origin.toVector());
                        double along = toTarget.clone().setY(0).dot(direction);
                        if (along < 0 || along > range) {
                            continue;
                        }
                        Location closest = origin.clone().add(direction.clone().multiply(along));
                        if (target.getLocation().toVector().setY(0)
                                .distance(closest.toVector().setY(0)) > width) {
                            continue;
                        }
                        target.damage(damage, boss);
                        target.setVelocity(target.getVelocity().add(
                                direction.clone().multiply(knockback).setY(0.5)));
                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                    }

                    // Real terrain gouge along the beam.
                    for (double d = 1; d <= range; d += 2.0) {
                        Grief.breakCrater(ctx, origin.clone().add(direction.clone().multiply(d)), width);
                    }
                },
                14, onComplete);
    }
}
