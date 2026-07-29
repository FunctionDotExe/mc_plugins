package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/** An expanding ring of decay cracks the ground open beneath it and rots everything it touches. */
public final class DecayNovaAttack extends BossAttack {

    private static final Color DECAY_GREY = Color.fromRGB(50, 50, 55);

    private final double damagePerPulse;
    private final double maxRadius;
    private final int durationTicks;
    private final int witherTicks;
    private final int telegraphTicks;

    public DecayNovaAttack(WeaponsPlugin plugin) {
        super(plugin, "threefold_bane");
        this.damagePerPulse = configDouble("decay-nova-damage-per-pulse", 5.0);
        this.maxRadius = configDouble("decay-nova-max-radius", 7.0);
        this.durationTicks = configInt("decay-nova-duration-ticks", 40);
        this.witherTicks = configInt("decay-nova-wither-ticks", 70);
        this.telegraphTicks = configInt("decay-nova-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Decay Nova";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("decay-nova-cooldown-seconds", 15.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, DECAY_GREY, 1.4f, 2.5, 24, 0);
                    Fx.point(origin.clone().add(0, 1, 0), Particle.SMOKE, 5);
                },
                () -> {
                    BossAudio.play(origin, "boss.threefold_bane.decay_nova", Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.6f);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), DECAY_GREY, 2.0f, 34, 0.7);
                    Grief.breakCrater(ctx, origin, maxRadius * 0.35);

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            double radius = maxRadius * (ticks + 1) / (double) durationTicks;
                            Fx.coloredRing(origin, DECAY_GREY, 1.2f, radius, 30, ticks * 0.4);
                            Fx.ring(origin, Particle.SOUL, radius, 22, ticks * 0.4);
                            if (ticks % 6 == 0) {
                                for (Entity nearby : ctx.boss().getNearbyEntities(radius + 1.0, 3.0, radius + 1.0)) {
                                    if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                            && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                        double dist = target.getLocation().distance(origin);
                                        if (dist <= radius) {
                                            tickHurt(ctx, target, damagePerPulse);
                                            StatusEffectManager.apply(target, PotionEffectType.WITHER, witherTicks, 1);
                                        }
                                    }
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                14, onComplete);
    }
}
