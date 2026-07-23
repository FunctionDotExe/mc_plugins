package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.ai.Movement;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
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

/** A world-shattering leap: the Worldender vaults at the target and lands with a maxed real explosion plus a wide crater. */
public final class VoidSlamAttack extends BossAttack {

    private static final Color VOID = Color.fromRGB(120, 20, 160);

    private final double damage;
    private final double radius;
    private final double knockup;
    private final int telegraphTicks;
    private final int airTicks;

    public VoidSlamAttack(WeaponsPlugin plugin) {
        super(plugin, "worldender");
        this.damage = configDouble("void-slam-damage", 15.0);
        this.radius = configDouble("void-slam-radius", 5.5);
        this.knockup = configDouble("void-slam-knockup", 0.9);
        this.telegraphTicks = configInt("void-slam-telegraph-ticks", 18);
        this.airTicks = configInt("void-slam-air-ticks", 16);
    }

    @Override
    public String name() {
        return "Void Slam";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("void-slam-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location landingSpot = ctx.target().getLocation().clone();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Telegraph.dangerZone(landingSpot, radius);
                    Fx.coloredRing(landingSpot, VOID, 1.4f, radius * 0.6, 30, 0);
                },
                () -> {
                    Movement.leap(boss, landingSpot, 0.95, 1.4);
                    BossAudio.play(boss.getLocation(), "boss.worldender.void_leap", Sound.ENTITY_WARDEN_ROAR, 1.35f, 0.7f);
                    Fx.spinningIcon(plugin, landingSpot.clone().add(0, 3.0, 0), org.bukkit.Material.CRYING_OBSIDIAN, 1.0f, airTicks + 4, 20.0);

                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (elapsed >= airTicks || !boss.isValid()) {
                                cancel();
                                if (!boss.isValid()) {
                                    return;
                                }
                                Location impact = boss.getLocation();
                                Fx.expandingRings(plugin, impact, Particle.SQUID_INK, radius, 4, 3L);
                                Fx.coloredBurst(impact.clone().add(0, 1, 0), VOID, 1.8f, 68, 0.7);
                                Fx.burst(impact.clone().add(0, 1, 0), Particle.SQUID_INK, 24, 0.6);
                                impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact.clone().add(0, 0.3, 0), 6, 0, 0, 0, 0);
                                BossAudio.play(impact, "boss.worldender.void_slam", Sound.ENTITY_GENERIC_EXPLODE, 1.55f, 0.6f);
                                Fx.sound(impact, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.15f, 0.5f);

                                for (Entity nearby : boss.getNearbyEntities(radius, radius, radius)) {
                                    if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                            && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                        target.damage(damage, boss);
                                        target.setVelocity(target.getVelocity().add(new Vector(0, knockup, 0)));
                                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                    }
                                }
                                Grief.explosion(ctx, impact, ctx.instance().boss().maxExplosionPower());
                                Grief.breakCrater(ctx, impact, radius * 0.7);
                                return;
                            }
                            Fx.trail(boss.getLocation().add(0, 1, 0), Particle.PORTAL, 10, 0.3, 0.02);
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
