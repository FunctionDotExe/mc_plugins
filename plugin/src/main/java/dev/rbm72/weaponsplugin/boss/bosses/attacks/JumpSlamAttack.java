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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Boss leaps at the target's position and slams down on landing — relocating version of Shockwave Slam. */
public final class JumpSlamAttack extends BossAttack {

    private final double damage;
    private final double radius;
    private final double knockup;
    private final int telegraphTicks;
    private final int airTicks;

    public JumpSlamAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("jump-slam-damage", 11.0);
        this.radius = configDouble("jump-slam-radius", 4.5);
        this.knockup = configDouble("jump-slam-knockup", 0.8);
        this.telegraphTicks = configInt("jump-slam-telegraph-ticks", 16);
        this.airTicks = configInt("jump-slam-air-ticks", 14);
    }

    @Override
    public String name() {
        return "Jump Slam";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("jump-slam-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location landingSpot = ctx.target().getLocation().clone();

        sequence(telegraphTicks,
                () -> Telegraph.targetMarker(ctx.target()),
                () -> {
                    Vector toTarget = landingSpot.toVector().subtract(boss.getLocation().toVector());
                    double horizontalDistance = Math.sqrt(toTarget.getX() * toTarget.getX() + toTarget.getZ() * toTarget.getZ());
                    Vector horizontal = new Vector(toTarget.getX(), 0, toTarget.getZ());
                    Vector launch = (horizontalDistance > 1.0E-3 ? horizontal.normalize() : new Vector(1, 0, 0))
                            .multiply(Math.min(1.5, horizontalDistance / 6.0)).setY(0.9);
                    boss.setVelocity(launch);
                    BossAudio.play(boss.getLocation(), "boss.fallen_king.jump", Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.7f);
                    // A shadow of the incoming slam hangs over the landing spot for the whole flight.
                    Fx.spinningIcon(plugin, landingSpot.clone().add(0, 3.0, 0), Material.NETHERITE_SWORD, 1.0f, airTicks + 4, 20.0);
                    Fx.coloredRing(landingSpot, Color.fromRGB(150, 20, 20), 1.2f, radius * 0.5, 26, 0);

                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (elapsed >= airTicks || !boss.isValid()) {
                                cancel();
                                Fx.expandingRings(plugin, boss.getLocation(), Particle.CRIT, radius, 4, 3L);
                                Fx.coloredBurst(boss.getLocation().add(0, 1, 0), Color.fromRGB(160, 20, 20), 1.6f, 50, 0.6);
                                Fx.burst(boss.getLocation().add(0, 1, 0), Particle.CRIT, 24, 0.6);
                                boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation().add(0, 0.3, 0), 6, 0, 0, 0, 0);
                                BossAudio.play(boss.getLocation(), "boss.fallen_king.slam_land", Sound.ENTITY_GENERIC_BIG_FALL, 1.0f, 0.7f);
                                for (Entity nearby : boss.getNearbyEntities(radius, radius, radius)) {
                                    if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                            && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                        target.damage(damage, boss);
                                        target.setVelocity(target.getVelocity().add(new Vector(0, knockup, 0)));
                                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                    }
                                }
                                Grief.breakCrater(ctx, boss.getLocation(), radius * 0.6);
                                return;
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                10, onComplete);
    }
}
