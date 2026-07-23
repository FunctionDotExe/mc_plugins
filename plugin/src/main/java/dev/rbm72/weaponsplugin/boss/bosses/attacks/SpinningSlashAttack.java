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
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Wider 360-degree AoE than Shockwave Slam, pushes outward instead of up — the armor-broken phase's opener. */
public final class SpinningSlashAttack extends BossAttack {

    private final double damage;
    private final double radius;
    private final int telegraphTicks;

    public SpinningSlashAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("spinning-slash-damage", 9.0);
        this.radius = configDouble("spinning-slash-radius", 5.5);
        this.telegraphTicks = configInt("spinning-slash-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Spinning Slash";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("spinning-slash-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();

        // A ring of orbiting "blades" spins up around the boss during the wind-up — the visual tell
        // that this is a wide spin, not a normal swing.
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= telegraphTicks + 2) {
                    cancel();
                    return;
                }
                Fx.helixFrame(ctx.bossLocation(), Particle.SWEEP_ATTACK, radius * 0.7, 6, ticks * 0.6, 0.2);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        sequence(telegraphTicks,
                () -> Telegraph.groundRing(origin, radius, Particle.CRIT),
                () -> {
                    BossAudio.play(origin, "boss.fallen_king.spin", Sound.ITEM_TRIDENT_HIT, 1.15f, 0.7f);
                    Fx.coloredRing(origin, Color.fromRGB(200, 50, 50), 1.6f, radius, 48, 0);
                    Fx.ring(origin, Particle.SWEEP_ATTACK, radius * 0.5, 26);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), Color.fromRGB(220, 70, 70), 1.8f, 26, 0.6);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Vector outward = target.getLocation().toVector().subtract(origin.toVector()).setY(0.2).normalize();
                            target.setVelocity(outward.multiply(0.8));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
