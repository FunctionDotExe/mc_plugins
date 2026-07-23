package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Freezing shockwave around the queen: damage + Slowness II to everyone caught in the ring. */
public final class FrostNovaAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(150, 220, 255);

    private final double damage;
    private final double radius;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int telegraphTicks;

    public FrostNovaAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.damage = configDouble("frost-nova-damage", 9.0);
        this.radius = configDouble("frost-nova-radius", 5.5);
        this.slowTicks = configInt("frost-nova-slow-ticks", 80);
        this.slowAmplifier = configInt("frost-nova-slow-amplifier", 1);
        this.telegraphTicks = configInt("frost-nova-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Frost Nova";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("frost-nova-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, FROST, 1.5f, radius, 44, 0);
                    Fx.point(origin.clone().add(0, 0.2, 0), Particle.SNOWFLAKE, 7);
                },
                () -> {
                    Fx.expandingRings(plugin, origin, Particle.SNOWFLAKE, radius, 4, 3L);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), FROST, 1.8f, 60, radius * 0.4);
                    Fx.burst(origin.clone().add(0, 1, 0), Particle.SNOWFLAKE, 30, radius * 0.35);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 0.2, 0), 6, 0, 0, 0, 0);
                    // A ring of real ice spikes punching up around the boss, not just particles.
                    Grief.raiseColumns(ctx, origin, Material.PACKED_ICE, 2, 6, radius * 0.7, 60);
                    BossAudio.play(origin, "boss.frost_queen.frost_nova", Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.7f);
                    Fx.sound(origin, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmplifier));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                12, onComplete);
    }
}
