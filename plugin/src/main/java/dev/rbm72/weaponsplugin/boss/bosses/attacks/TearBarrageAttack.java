package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** A volley of real spinning prismarine-shard tears streaks toward the target — each one chases and blurs the victim's sight on contact. */
public final class TearBarrageAttack extends BossAttack {

    private static final Color SORROW_BLUE = Color.fromRGB(70, 120, 200);

    private final double damage;
    private final int tears;
    private final double hitRadius;
    private final double speed;
    private final int maxLifeTicks;
    private final int blindnessTicks;
    private final int telegraphTicks;

    public TearBarrageAttack(WeaponsPlugin plugin) {
        super(plugin, "weeping_colossus");
        this.damage = configDouble("tear-barrage-damage", 6.0);
        this.tears = configInt("tear-barrage-count", 4);
        this.hitRadius = configDouble("tear-barrage-hit-radius", 1.5);
        this.speed = configDouble("tear-barrage-speed", 0.75);
        this.maxLifeTicks = configInt("tear-barrage-max-life-ticks", 70);
        this.blindnessTicks = configInt("tear-barrage-blindness-ticks", 40);
        this.telegraphTicks = configInt("tear-barrage-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Tear Barrage";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("tear-barrage-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.point(origin.clone().add(0, 1, 0), Particle.SPLASH, 6);
                },
                () -> {
                    BossAudio.play(origin, "boss.weeping_colossus.tear_barrage", Sound.ENTITY_GHAST_SHOOT, 1.2f, 0.8f);
                    for (int i = 0; i < tears; i++) {
                        launchTear(ctx, i * 3L);
                    }
                },
                12, onComplete);
    }

    private void launchTear(AttackContext ctx, long startDelay) {
        double spawnAngle = Math.random() * Math.PI * 2;
        new BukkitRunnable() {
            int ticks = 0;
            Location pos;
            ItemDisplay icon;

            @Override
            public void run() {
                if (!ctx.boss().isValid() || !ctx.target().isOnline() || ticks >= maxLifeTicks) {
                    removeIcon();
                    cancel();
                    return;
                }
                if (pos == null) {
                    pos = ctx.bossLocation().add(Math.cos(spawnAngle) * 1.3, 0.8, Math.sin(spawnAngle) * 1.3);
                    icon = Fx.spinningIcon(plugin, pos, Material.PRISMARINE_SHARD, 0.6f, maxLifeTicks + 5, 35.0);
                }
                Location targetPoint = ctx.target().getLocation().add(0, 1, 0);
                Vector toTarget = targetPoint.toVector().subtract(pos.toVector());
                if (toTarget.lengthSquared() <= hitRadius * hitRadius) {
                    ctx.target().damage(damage, ctx.boss());
                    StatusEffectManager.apply(ctx.target(), PotionEffectType.BLINDNESS, blindnessTicks, 0);
                    Fx.coloredBurst(pos, SORROW_BLUE, 1.6f, 26, 0.4);
                    Fx.burst(pos, Particle.SPLASH, 20, 0.3);
                    Fx.bloodSpray(targetPoint);
                    removeIcon();
                    cancel();
                    return;
                }
                pos.add(toTarget.normalize().multiply(speed));
                if (icon != null && !icon.isDead()) {
                    icon.teleport(pos);
                }
                Fx.coloredBurst(pos, SORROW_BLUE, 1.0f, 5, 0.1);
                Fx.point(pos, Particle.DRIPPING_WATER, 2);
                ticks++;
            }

            private void removeIcon() {
                if (icon != null && !icon.isDead()) {
                    icon.remove();
                }
            }
        }.runTaskTimer(plugin, startDelay, 1L);
    }
}
