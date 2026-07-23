package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Drags the target in close and swallows them whole for a moment before spitting them back out. */
public final class EngulfAttack extends BossAttack {

    private static final Color OOZE_GREEN = Color.fromRGB(90, 170, 70);

    private final double damage;
    private final double pullRange;
    private final int engulfTicks;
    private final int weaknessTicks;
    private final int telegraphTicks;

    public EngulfAttack(WeaponsPlugin plugin) {
        super(plugin, "amalgamated_bulk");
        this.damage = configDouble("engulf-damage", 11.0);
        this.pullRange = configDouble("engulf-pull-range", 6.0);
        this.engulfTicks = configInt("engulf-engulf-ticks", 30);
        this.weaknessTicks = configInt("engulf-weakness-ticks", 80);
        this.telegraphTicks = configInt("engulf-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Engulf";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("engulf-cooldown-seconds", 14.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        if (ctx.target().getLocation().distance(origin) > pullRange) {
            // Out of pull range — the ooze just lunges without a target to catch, no-op cast.
            sequence(telegraphTicks, () -> {
            }, () -> {
            }, 6, onComplete);
            return;
        }

        sequence(telegraphTicks,
                () -> {
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), OOZE_GREEN, 1.2f, 10, 0.4);
                    Vector pull = origin.toVector().subtract(ctx.target().getLocation().toVector()).setY(0.05);
                    if (pull.lengthSquared() > 1.0) {
                        ctx.target().setVelocity(ctx.target().getVelocity().add(pull.normalize().multiply(0.25)));
                    }
                },
                () -> {
                    BossAudio.play(origin, "boss.amalgamated_bulk.engulf", Sound.ENTITY_SLIME_SQUISH, 1.4f, 0.4f);
                    Fx.coloredBurst(ctx.target().getLocation().add(0, 1, 0), OOZE_GREEN, 2.0f, 40, 0.5);
                    ctx.target().damage(damage, ctx.boss());
                    StatusEffectManager.apply(ctx.target(), PotionEffectType.WEAKNESS, weaknessTicks, 1);
                    Fx.bloodSpray(ctx.target().getLocation().add(0, 1, 0));

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= engulfTicks || !ctx.boss().isValid() || !ctx.target().isOnline()) {
                                cancel();
                                Fx.burst(ctx.target().getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 20, 0.4);
                                return;
                            }
                            Fx.point(ctx.target().getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 2);
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                14, onComplete);
    }
}
