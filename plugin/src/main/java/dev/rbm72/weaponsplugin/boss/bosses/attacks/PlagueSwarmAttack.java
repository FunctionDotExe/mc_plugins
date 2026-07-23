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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Silverfish;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/** A bursting cloud of real biting silverfish erupts from the warden, sickening and starving everyone nearby. */
public final class PlagueSwarmAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);
    private static final Color SICKLY = Color.fromRGB(150, 200, 80);

    private final double damage;
    private final double radius;
    private final int poisonAmplifier;
    private final int effectTicks;
    private final int telegraphTicks;

    public PlagueSwarmAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.damage = configDouble("plague-swarm-damage", 9.0);
        this.radius = configDouble("plague-swarm-radius", 6.0);
        this.poisonAmplifier = configInt("plague-swarm-poison-amplifier", 2); // POISON III
        this.effectTicks = configInt("plague-swarm-effect-ticks", 120);
        this.telegraphTicks = configInt("plague-swarm-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Plague Swarm";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("plague-swarm-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, TOXIC, 1.5f, radius, 48, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.plague_warden.plague_swarm", Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1.3f, 0.6f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.4f);
                    Fx.expandingRings(plugin, origin, Particle.SPORE_BLOSSOM_AIR, radius, 4, 3L);
                    Fx.expandingRings(plugin, origin, Particle.ITEM_SLIME, radius * 0.6, 3, 4L);
                    origin.getWorld().spawnParticle(Particle.SNEEZE, origin.clone().add(0, 1, 0), 195, radius * 0.64, 1.6, radius * 0.64, 0.05);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), SICKLY, 2.0f, 65, radius * 0.35);
                    Fx.burst(origin.clone().add(0, 1, 0), Particle.ITEM_SLIME, 30, radius * 0.3);
                    // Real swarm: purely cosmetic, self-removing silverfish instead of a particle cloud.
                    for (int i = 0; i < 8; i++) {
                        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                        double dist = ThreadLocalRandom.current().nextDouble(0.5, radius * 0.6);
                        Location spot = origin.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                        Silverfish fly = origin.getWorld().spawn(spot, Silverfish.class, entity -> {
                            entity.setAI(false);
                            entity.setInvulnerable(true);
                            entity.setSilent(true);
                            entity.setPersistent(false);
                        });
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!fly.isDead()) {
                                    fly.remove();
                                }
                            }
                        }.runTaskLater(plugin, 40L);
                    }
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, effectTicks, poisonAmplifier));
                            target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, effectTicks, 0));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                12, onComplete);
    }
}
