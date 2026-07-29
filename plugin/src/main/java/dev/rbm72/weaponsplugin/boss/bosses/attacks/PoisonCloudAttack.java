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
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Drops a lingering toxic cloud on the target: poison + damage over time to anyone standing in it.
 * <p>
 * A real {@link AreaEffectCloud} (§0.1 — a real Minecraft object, not particles standing in for one)
 * carries the poison and the persistent visual; a light tick loop on top applies the attack's own
 * exactly-configured damage-per-second, since vanilla's instant-damage potion only comes in coarse,
 * amplifier-sized steps that can't track an arbitrary config value.
 */
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
                    if (center.getWorld() == null) {
                        return;
                    }
                    BossAudio.play(center, "boss.plague_warden.poison_cloud", Sound.ENTITY_WITCH_THROW, 1.0f, 0.7f);
                    Fx.sound(center, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.6f);

                    AreaEffectCloud cloud = center.getWorld().spawn(center, AreaEffectCloud.class, entity -> {
                        entity.setRadius((float) radius);
                        entity.setDuration(durationTicks);
                        entity.setReapplicationDelay(20);
                        entity.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 60, poisonAmplifier), true);
                        entity.setParticle(Particle.SPORE_BLOSSOM_AIR);
                        entity.setColor(TOXIC);
                    });
                    ctx.instance().trackEntity(cloud);

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid() || !cloud.isValid()) {
                                cancel();
                                return;
                            }
                            if (ticks % 20 == 0) {
                                double r2 = radius * radius;
                                for (Player player : ctx.arena().playersInside()) {
                                    if (player.getLocation().distanceSquared(center) <= r2) {
                                        tickHurt(ctx, player, damagePerSecond);
                                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), SICKLY, 1.0f, 10, 0.3);
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
