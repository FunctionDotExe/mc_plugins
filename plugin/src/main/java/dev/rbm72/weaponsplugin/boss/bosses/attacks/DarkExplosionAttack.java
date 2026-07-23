package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Particle-and-damage AoE burst around the boss. When grief is enabled this now also triggers a real
 * {@link Grief#explosion} (terrain damage); grief disabled keeps the original cosmetic-only behavior.
 */
public final class DarkExplosionAttack extends BossAttack {

    private final double damage;
    private final double radius;
    private final int telegraphTicks;

    public DarkExplosionAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("dark-explosion-damage", 10.0);
        this.radius = configDouble("dark-explosion-radius", 5.0);
        this.telegraphTicks = configInt("dark-explosion-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Dark Explosion";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("dark-explosion-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();

        // The void gathers inward before it releases — a swirling ring collapsing toward the boss.
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= telegraphTicks) {
                    cancel();
                    return;
                }
                double shrink = radius * (1.0 - (double) ticks / telegraphTicks);
                Fx.ring(ctx.bossLocation(), Particle.REVERSE_PORTAL, shrink, 30, ticks * 0.4);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        sequence(telegraphTicks,
                () -> Fx.ring(origin, Particle.SMOKE, radius, 30),
                () -> {
                    World world = origin.getWorld();
                    if (world != null) {
                        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(40, 0, 60), 3.84f);
                        world.spawnParticle(Particle.DUST, origin.clone().add(0, 1, 0), 204, radius * 0.64, 0.96, radius * 0.64, 0, dust);
                        Particle.DustOptions violetDust = new Particle.DustOptions(Color.fromRGB(120, 40, 160), 2.88f);
                        world.spawnParticle(Particle.DUST, origin.clone().add(0, 1, 0), 108, radius * 0.8, 1.12, radius * 0.8, 0, violetDust);
                        world.spawnParticle(Particle.SMOKE, origin.clone().add(0, 1, 0), 144, radius * 0.64, 0.96, radius * 0.64, 0.05);
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 1, 0), 6, 0, 0, 0, 0);
                    }
                    BossAudio.play(origin, "boss.fallen_king.dark_explosion", Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.6f);
                    Fx.sound(origin, Sound.ENTITY_WITHER_HURT, 1.1f, 0.4f);
                    Grief.explosion(ctx, origin.clone().add(0, 1, 0), (float) configDouble("dark-explosion-power", 3.0));
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Vector outward = target.getLocation().toVector().subtract(origin.toVector()).setY(0.3).normalize();
                            target.setVelocity(outward.multiply(1.0));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
