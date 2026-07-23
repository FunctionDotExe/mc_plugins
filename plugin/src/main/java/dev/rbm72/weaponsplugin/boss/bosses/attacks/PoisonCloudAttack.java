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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/** Drops a lingering toxic cloud on the target: poison + damage over time to anyone standing in it. */
public final class PoisonCloudAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);
    private static final Color SICKLY = Color.fromRGB(150, 200, 80);

    private final double damagePerSecond;
    private final double radius;
    private final int poisonAmplifier;
    private final int durationTicks;
    private final int telegraphTicks;

    public PoisonCloudAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.damagePerSecond = configDouble("poison-cloud-damage-per-second", 2.0);
        this.radius = configDouble("poison-cloud-radius", 4.0);
        this.poisonAmplifier = configInt("poison-cloud-poison-amplifier", 1); // POISON II
        this.durationTicks = configInt("poison-cloud-duration-ticks", 100); // 5s
        this.telegraphTicks = configInt("poison-cloud-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Poison Cloud";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("poison-cloud-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.target().getLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(center, radius);
                    Fx.coloredRing(center, TOXIC, 1.4f, radius, 42, 0);
                },
                () -> {
                    BossAudio.play(center, "boss.plague_warden.poison_cloud", Sound.ENTITY_WITCH_THROW, 1.0f, 0.7f);
                    Fx.sound(center, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.6f);
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            if (center.getWorld() != null) {
                                center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.clone().add(0, 0.5, 0),
                                        54, radius * 0.96, 0.64, radius * 0.96, 0.01);
                            }
                            Fx.coloredBurst(center.clone().add(0, 0.4, 0), TOXIC, 1.1f, 10, radius * 0.5);
                            if (ticks % 20 == 0) {
                                double r2 = radius * radius;
                                for (Player player : ctx.arena().playersInside()) {
                                    if (player.getLocation().distanceSquared(center) <= r2) {
                                        player.damage(damagePerSecond, ctx.boss());
                                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, poisonAmplifier));
                                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), SICKLY, 1.0f, 10, 0.3);
                                        Fx.burst(player.getLocation().add(0, 1, 0), Particle.SPORE_BLOSSOM_AIR, 8, 0.3);
                                        Fx.sound(player.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.5f, 1.3f);
                                    }
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
