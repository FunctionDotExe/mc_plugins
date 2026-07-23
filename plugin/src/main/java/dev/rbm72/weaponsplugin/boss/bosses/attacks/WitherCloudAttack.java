package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/** Conjures a lingering cloud of withering miasma over the target's position for several seconds. */
public final class WitherCloudAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final double radius;
    private final int durationTicks;
    private final int witherAmplifier;
    private final int telegraphTicks;

    public WitherCloudAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.radius = configDouble("wither-cloud-radius", 5.0);
        this.durationTicks = configInt("wither-cloud-duration-ticks", 100);
        this.witherAmplifier = configInt("wither-cloud-wither-amplifier", 1);
        this.telegraphTicks = configInt("wither-cloud-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Wither Cloud";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("wither-cloud-cooldown-seconds", 13.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.target().getLocation();
        sequence(telegraphTicks,
                () -> Fx.coloredRing(center, NECROTIC, 1.4f, radius, 36, 0),
                () -> {
                    BossAudio.play(center, "boss.necro_overlord.wither_cloud", Sound.ENTITY_WITHER_SHOOT, 1.15f, 0.5f);
                    Fx.sound(center, Sound.PARTICLE_SOUL_ESCAPE, 1.1f, 0.6f);
                    double r2 = radius * radius;
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            Fx.ring(center, Particle.SOUL, radius * (0.6 + 0.4 * Math.sin(ticks * 0.2)), 30, ticks * 0.15);
                            center.getWorld().spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 0.6, 0),
                                    60, radius * 0.8, 0.64, radius * 0.8, 0.01);
                            if (ticks % 20 == 0) {
                                for (Player player : ctx.arena().playersInside()) {
                                    if (player.getLocation().distanceSquared(center) <= r2) {
                                        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, witherAmplifier));
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
