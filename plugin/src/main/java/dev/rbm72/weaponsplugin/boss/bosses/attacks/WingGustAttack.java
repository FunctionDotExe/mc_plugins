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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** A thunderous downward wing-beat that hurls nearby players skyward, slows them, and rakes them for damage. */
public final class WingGustAttack extends BossAttack {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);

    private final double damage;
    private final double radius;
    private final double launch;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int telegraphTicks;

    public WingGustAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.damage = configDouble("wing-gust-damage", 7.0);
        this.radius = configDouble("wing-gust-radius", 6.0);
        this.launch = configDouble("wing-gust-launch", 1.0);
        this.slowTicks = configInt("wing-gust-slow-ticks", 60);
        this.slowAmplifier = configInt("wing-gust-slow-amplifier", 1);
        this.telegraphTicks = configInt("wing-gust-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Wing Gust";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("wing-gust-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, DRAGON_RED, 1.5f, radius, 46, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.dragon_elder.wing_gust", Sound.ENTITY_ENDER_DRAGON_FLAP, 1.45f, 0.6f);
                    Fx.sound(origin, Sound.ENTITY_PHANTOM_FLAP, 1.15f, 0.5f);
                    Fx.expandingRings(plugin, origin, Particle.CLOUD, radius, 4, 3L);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), DRAGON_RED, 1.8f, 50, 0.7);
                    Fx.burst(origin.clone().add(0, 1, 0), Particle.CLOUD, 26, 0.6);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 0.2, 0), 6, 0, 0, 0, 0);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Movement.launchTarget(target, launch);
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmplifier));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                12, onComplete);
    }
}
