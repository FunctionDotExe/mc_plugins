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

/** Signature move: the colossus drives its fists into the earth, blasting a huge crater and a knock-up shockwave. */
public final class SeismicSlamAttack extends BossAttack {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);

    private final double damage;
    private final double radius;
    private final double craterRadius;
    private final double knockup;
    private final int telegraphTicks;

    public SeismicSlamAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("seismic-slam-damage", 13.0);
        this.radius = configDouble("seismic-slam-radius", 6.0);
        this.craterRadius = configDouble("seismic-slam-crater-radius", 6.0);
        this.knockup = configDouble("seismic-slam-knockup", 0.9);
        this.telegraphTicks = configInt("seismic-slam-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Seismic Slam";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("seismic-slam-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, SOLAR_GOLD, 1.7f, radius, 48, 0);
                    Fx.point(origin.clone().add(0, 3, 0), Particle.END_ROD, 7);
                },
                () -> {
                    Fx.expandingRings(plugin, origin, Particle.END_ROD, radius, 4, 3L);
                    Fx.expandingRings(plugin, origin, Particle.CLOUD, radius * 0.8, 3, 4L);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), SOLAR_GOLD, 1.9f, 75, 0.7);
                    Fx.burst(origin.clone().add(0, 1, 0), Particle.END_ROD, 30, 0.6);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 0.2, 0), 9, 0, 0, 0, 0);
                    BossAudio.play(origin, "boss.solar_colossus.seismic_slam", Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_BIG_FALL, 1.2f, 0.6f);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.setVelocity(target.getVelocity().add(new Vector(0, knockup, 0)));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                    Grief.breakCrater(ctx, origin, craterRadius);
                },
                16, onComplete);
    }
}
