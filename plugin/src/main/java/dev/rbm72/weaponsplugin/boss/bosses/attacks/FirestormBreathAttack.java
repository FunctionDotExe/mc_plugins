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

/** A sweeping cone of dragonfire breathed down at the target — scorching and igniting everyone caught in it. */
public final class FirestormBreathAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 110, 0);
    private static final Color DEEP_FIRE = Color.fromRGB(200, 30, 0);

    private final double damage;
    private final double range;
    private final double angleDegrees;
    private final int fireTicks;
    private final int telegraphTicks;

    public FirestormBreathAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.damage = configDouble("firestorm-breath-damage", 10.0);
        this.range = configDouble("firestorm-breath-range", 9.0);
        this.angleDegrees = configDouble("firestorm-breath-angle-degrees", 70.0);
        this.fireTicks = configInt("firestorm-breath-fire-ticks", 100);
        this.telegraphTicks = configInt("firestorm-breath-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Firestorm Breath";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("firestorm-breath-cooldown-seconds", 11.0);
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
                    Telegraph.cone(origin, facing, angleDegrees, range, Particle.FLAME);
                    Fx.coloredRing(ctx.bossLocation(), EMBER, 1.5f, 2.5, 32, 0);
                    Fx.point(origin.clone().add(0, 0.5, 0), Particle.LAVA, 5);
                },
                () -> {
                    BossAudio.play(origin, "boss.dragon_elder.firestorm_breath", Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.6f, 0.5f);
                    Fx.sound(origin, Sound.ITEM_FIRECHARGE_USE, 1.3f, 0.6f);
                    for (double d = 1; d <= range; d += 1.0) {
                        Location p = origin.clone().add(facing.clone().multiply(d));
                        Fx.burst(p, Particle.FLAME, 26, 0.6);
                        Fx.burst(p, Particle.LAVA, 5, 0.5);
                        Fx.coloredBurst(p, DEEP_FIRE, 1.7f, 17, 0.6);
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
                            target.setFireTicks(fireTicks);
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                            Fx.coloredBurst(target.getLocation().add(0, 1, 0), EMBER, 2.0f, 26, 0.4);
                            Fx.coloredBurst(target.getLocation().add(0, 1, 0), DEEP_FIRE, 1.5f, 16, 0.5);
                        }
                    }
                },
                12, onComplete);
    }
}
