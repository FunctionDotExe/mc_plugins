package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.ai.Movement;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** A blast of wind that hurls every nearby player skyward and batters them. */
public final class GalePushAttack extends BossAttack {

    private static final Color STORM_WHITE = Color.fromRGB(235, 245, 255);

    private final double damage;
    private final double radius;
    private final double launch;
    private final int telegraphTicks;

    public GalePushAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.damage = configDouble("gale-push-damage", 5.0);
        this.radius = configDouble("gale-push-radius", 6.0);
        this.launch = configDouble("gale-push-launch", 1.1);
        this.telegraphTicks = configInt("gale-push-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Gale Push";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("gale-push-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, STORM_WHITE, 1.4f, radius, 44, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.storm_tyrant.gale_push", Sound.ITEM_ELYTRA_FLYING, 1.1f, 0.8f);
                    Fx.sound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
                    Fx.expandingRings(plugin, origin, Particle.CLOUD, radius, 4, 3L);
                    Fx.burst(origin.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 50, radius * 0.4);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), STORM_WHITE, 1.6f, 26, radius * 0.4);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Movement.launchTarget(target, launch);
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
