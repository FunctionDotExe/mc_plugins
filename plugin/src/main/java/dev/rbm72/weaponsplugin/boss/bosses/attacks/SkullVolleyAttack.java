package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Three homing skull-shaped bolts, one per head, that chase the target down and detonate on contact. */
public final class SkullVolleyAttack extends BossAttack {

    private static final Color SOUL_BLUE = Color.fromRGB(30, 200, 210);

    private final double damage;
    private final int skulls;
    private final double hitRadius;
    private final double speed;
    private final int maxLifeTicks;
    private final int telegraphTicks;

    public SkullVolleyAttack(WeaponsPlugin plugin) {
        super(plugin, "threefold_bane");
        this.damage = configDouble("skull-volley-damage", 7.0);
        this.skulls = configInt("skull-volley-count", 3);
        this.hitRadius = configDouble("skull-volley-hit-radius", 1.6);
        this.speed = configDouble("skull-volley-speed", 0.7);
        this.maxLifeTicks = configInt("skull-volley-max-life-ticks", 70);
        this.telegraphTicks = configInt("skull-volley-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Skull Volley";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("skull-volley-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.point(origin.clone().add(0, 1.5, 0), Particle.SMOKE, 5);
                },
                () -> {
                    BossAudio.play(origin, "boss.threefold_bane.skull_volley", Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.8f);
                    for (int i = 0; i < skulls; i++) {
                        launchSkull(ctx, i * 4L, i);
                    }
                },
                12, onComplete);
    }

    private void launchSkull(AttackContext ctx, long startDelay, int headIndex) {
        double spawnAngle = (Math.PI * 2 / 3) * headIndex;
        new BukkitRunnable() {
            int ticks = 0;
            Location pos;

            @Override
            public void run() {
                if (!ctx.boss().isValid() || !ctx.target().isOnline() || ticks >= maxLifeTicks) {
                    cancel();
                    return;
                }
                if (pos == null) {
                    pos = ctx.bossLocation().add(Math.cos(spawnAngle) * 1.4, 1.6, Math.sin(spawnAngle) * 1.4);
                }
                Location targetPoint = ctx.target().getLocation().add(0, 1, 0);
                Vector toTarget = targetPoint.toVector().subtract(pos.toVector());
                if (toTarget.lengthSquared() <= hitRadius * hitRadius) {
                    ctx.target().damage(damage, ctx.boss());
                    Fx.coloredBurst(pos, SOUL_BLUE, 1.8f, 30, 0.4);
                    Fx.burst(pos, Particle.EXPLOSION, 3, 0.2);
                    Fx.sound(pos, Sound.ENTITY_WITHER_HURT, 0.8f, 1.2f);
                    Fx.bloodSpray(targetPoint);
                    // Each impact actually cracks the ground, not just a burst of particles.
                    Grief.breakCrater(ctx, pos, 1.2);
                    cancel();
                    return;
                }
                pos.add(toTarget.normalize().multiply(speed));
                Fx.coloredBurst(pos, SOUL_BLUE, 1.0f, 6, 0.1);
                Fx.point(pos, Particle.SMOKE, 3);
                ticks++;
            }
        }.runTaskTimer(plugin, startDelay, 1L);
    }
}
