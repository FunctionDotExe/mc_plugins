package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** A grounded sweep of the elder's tail — a wide cone that batters and flings back everyone in front of it. */
public final class TailSweepAttack extends BossAttack {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);

    private final double damage;
    private final double range;
    private final double angleDegrees;
    private final double knockback;
    private final int telegraphTicks;

    public TailSweepAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.damage = configDouble("tail-sweep-damage", 12.0);
        this.range = configDouble("tail-sweep-range", 6.0);
        this.angleDegrees = configDouble("tail-sweep-angle-degrees", 120.0);
        this.knockback = configDouble("tail-sweep-knockback", 1.2);
        this.telegraphTicks = configInt("tail-sweep-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Tail Sweep";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("tail-sweep-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation().add(0, 1, 0);
        Vector direction = ctx.target().getLocation().toVector().subtract(origin.toVector()).setY(0);
        if (direction.lengthSquared() < 0.01) {
            direction = ctx.boss().getLocation().getDirection().setY(0);
        }
        Vector facing = direction.clone().normalize();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.cone(origin, facing, angleDegrees, range, Particle.CRIT);
                    Fx.coloredRing(ctx.bossLocation(), DRAGON_RED, 1.4f, 2.9, 30, 0);
                },
                () -> {
                    // Grounded-only: while airborne the tail whiffs harmlessly overhead.
                    if (!ctx.boss().isOnGround()) {
                        Fx.point(origin, Particle.SMOKE, 8);
                        Fx.sound(origin, Sound.ENTITY_PHANTOM_FLAP, 0.7f, 0.6f);
                        return;
                    }
                    BossAudio.play(origin, "boss.dragon_elder.tail_sweep", Sound.ENTITY_ENDER_DRAGON_HURT, 1.35f, 0.6f);
                    Fx.sound(origin, Sound.ENTITY_RAVAGER_ATTACK, 1.1f, 0.7f);
                    for (double d = 1; d <= range; d += 1.0) {
                        Location p = origin.clone().add(facing.clone().multiply(d));
                        Fx.burst(p, Particle.CRIT, 17, 0.5);
                        Fx.coloredBurst(p, DRAGON_RED, 1.3f, 14, 0.5);
                    }
                    double halfAngle = angleDegrees / 2.0;
                    for (Entity nearby : ctx.boss().getNearbyEntities(range, range, range)) {
                        if (!(nearby instanceof LivingEntity target) || nearby.equals(ctx.boss())
                                || ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            continue;
                        }
                        Vector to = target.getLocation().toVector().subtract(origin.toVector()).setY(0);
                        if (to.lengthSquared() > range * range || to.lengthSquared() < 0.01) {
                            continue;
                        }
                        if (Math.toDegrees(to.angle(facing)) <= halfAngle) {
                            target.damage(damage, ctx.boss());
                            Vector push = to.clone().normalize().multiply(knockback).setY(0.5);
                            target.setVelocity(target.getVelocity().add(push));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                12, onComplete);
    }
}
