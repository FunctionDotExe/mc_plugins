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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seeds several drifting miasma pools across the arena, each poisoning and eroding anyone who lingers
 * in it — real {@link AreaEffectCloud} entities (§0.1) carrying the poison and the persistent visual,
 * same split as {@link PoisonCloudAttack}: a light tick loop on top applies the attack's own exactly
 * configured damage-per-second.
 */
public final class MiasmaAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);
    private static final Color SICKLY = Color.fromRGB(150, 200, 80);

    private final int zoneCount;
    private final double radius;
    private final double damagePerSecond;
    private final int poisonAmplifier;
    private final int durationTicks;
    private final int telegraphTicks;

    public MiasmaAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.zoneCount = configInt("miasma-zone-count", 3);
        this.radius = configDouble("miasma-radius", 3.5);
        this.damagePerSecond = configDouble("miasma-damage-per-second", 2.0);
        this.poisonAmplifier = configInt("miasma-poison-amplifier", 1); // POISON II
        this.durationTicks = configInt("miasma-duration-ticks", 120); // 6s
        this.telegraphTicks = configInt("miasma-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Miasma";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("miasma-cooldown-seconds", 14.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();
        List<Location> zones = new ArrayList<>();
        for (int i = 0; i < zoneCount; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(0, arenaRadius * 0.7);
            zones.add(center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist));
        }
        sequence(telegraphTicks,
                () -> {
                    for (Location zone : zones) {
                        Telegraph.dangerZone(zone, radius);
                        Fx.coloredRing(zone, TOXIC, 1.3f, radius, 34, 0);
                    }
                },
                () -> {
                    BossAudio.play(center, "boss.plague_warden.miasma", Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.5f);
                    Fx.sound(center, Sound.BLOCK_SCULK_SPREAD, 1.0f, 0.6f);
                    for (Location zone : zones) {
                        if (zone.getWorld() == null) {
                            continue;
                        }
                        AreaEffectCloud cloud = zone.getWorld().spawn(zone, AreaEffectCloud.class, entity -> {
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
                                        if (player.getLocation().distanceSquared(zone) <= r2) {
                                            tickHurt(ctx, player, damagePerSecond);
                                            Fx.coloredBurst(player.getLocation().add(0, 1, 0), SICKLY, 1.0f, 10, 0.3);
                                        }
                                    }
                                }
                                ticks++;
                            }
                        }.runTaskTimer(plugin, 0L, 1L);
                    }
                },
                12, onComplete);
    }
}
