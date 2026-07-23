package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Collapses a point in space: drags every arena player inward, then detonates in a void nova. */
public final class SingularityAttack extends BossAttack {

    private static final Color DEEP_VOID = Color.fromRGB(45, 0, 70);
    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);

    private final double damage;
    private final double radius;
    private final double pullStrength;
    private final int pullTicks;
    private final int telegraphTicks;

    public SingularityAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.damage = configDouble("singularity-damage", 10.0);
        this.radius = configDouble("singularity-radius", 6.0);
        this.pullStrength = configDouble("singularity-pull-strength", 0.35);
        this.pullTicks = configInt("singularity-pull-ticks", 40);
        this.telegraphTicks = configInt("singularity-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Singularity";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("singularity-cooldown-seconds", 14.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.bossLocation().clone().add(0, 1, 0);
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(center, radius);
                    Fx.coloredRing(center, DEEP_VOID, 1.6f, radius, 46, 0);
                },
                () -> {
                    BossAudio.play(center, "boss.void_sovereign.singularity", Sound.BLOCK_PORTAL_AMBIENT, 1.15f, 0.5f);
                    Fx.sound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f);
                    Fx.spinningIcon(plugin, center, Material.ECHO_SHARD, 1.0f, pullTicks, 16);

                    // Pull phase: drag every arena player toward the collapse point, then detonate.
                    new BukkitRunnable() {
                        int ticks = 0;
                        double angle = 0;

                        @Override
                        public void run() {
                            if (!ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            if (ticks < pullTicks) {
                                Fx.ring(center, Particle.PORTAL, 2.5, 36, angle);
                                Fx.ring(center.clone().add(0, 0.6, 0), Particle.REVERSE_PORTAL, 1.85, 28, angle + 0.4);
                                angle += 0.5;
                                for (Player player : ctx.arena().playersInside()) {
                                    Vector toCenter = center.toVector().subtract(player.getLocation().toVector());
                                    if (toCenter.lengthSquared() <= 1.0E-6) {
                                        continue;
                                    }
                                    Vector pull = toCenter.normalize().multiply(pullStrength);
                                    player.setVelocity(player.getVelocity().add(pull));
                                }
                                ticks++;
                                return;
                            }

                            Fx.burst(center, Particle.EXPLOSION_EMITTER, 2, 0.1);
                            Fx.burst(center, Particle.PORTAL, 190, radius * 0.6);
                            Fx.coloredBurst(center, DEEP_VOID, 2.4f, 80, radius * 0.6);
                            Fx.coloredBurst(center.clone().add(0, 1, 0), VOID_PURPLE, 2.0f, 50, radius * 0.5);
                            Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.55f, 0.6f);
                            for (Player player : ctx.arena().playersInside()) {
                                if (player.getLocation().distanceSquared(center) <= radius * radius) {
                                    player.damage(damage, ctx.boss());
                                    Vector away = player.getLocation().toVector().subtract(center.toVector());
                                    Vector knock = (away.lengthSquared() > 1.0E-6 ? away.normalize() : new Vector(1, 0, 0)).setY(0.5);
                                    player.setVelocity(player.getVelocity().add(knock.multiply(1.2)));
                                    Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                                }
                            }
                            cancel();
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
