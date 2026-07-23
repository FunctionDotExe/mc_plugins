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

/** In-place ground slam: damage + knock-up in a radius around the boss. */
public final class ShockwaveSlamAttack extends BossAttack {

    private final double damage;
    private final double radius;
    private final double knockup;
    private final int telegraphTicks;

    public ShockwaveSlamAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("shockwave-slam-damage", 10.0);
        this.radius = configDouble("shockwave-slam-radius", 5.0);
        this.knockup = configDouble("shockwave-slam-knockup", 0.7);
        this.telegraphTicks = configInt("shockwave-slam-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Shockwave Slam";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("shockwave-slam-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, Color.fromRGB(180, 30, 30), 1.5f, radius, 44, 0);
                },
                () -> {
                    Fx.expandingRings(plugin, origin, Particle.CRIT, radius, 4, 3L);
                    Fx.expandingRings(plugin, origin, Particle.CLOUD, radius * 0.7, 3, 4L);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), Color.fromRGB(200, 40, 40), 1.6f, 32, radius * 0.35);
                    Fx.burst(origin.clone().add(0, 1, 0), Particle.CRIT, 22, radius * 0.3);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 0.2, 0), 6, 0, 0, 0, 0);
                    BossAudio.play(origin, "boss.fallen_king.shockwave", Sound.ENTITY_RAVAGER_ATTACK, 1.1f, 0.6f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_BIG_FALL, 1.1f, 0.7f);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.setVelocity(target.getVelocity().add(new Vector(0, knockup, 0)));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                    Grief.breakCrater(ctx, origin, radius * 0.6);
                },
                14, onComplete);
    }
}
