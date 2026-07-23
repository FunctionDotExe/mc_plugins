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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Signature grief move: the elder folds its wings and plummets onto the target,
 * detonating the ground on impact — a real explosion plus a cratering slam. The
 * dragon stays grounded briefly afterward (via recovery), its vulnerable window.
 */
public final class DiveBombAttack extends BossAttack {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);
    private static final Color ELDER_BLACK = Color.fromRGB(30, 5, 5);

    private final double damage;
    private final double radius;
    private final float explosionPower;
    private final double diveSpeed;
    private final int diveTicks;
    private final int telegraphTicks;
    private final int recoveryTicks;

    public DiveBombAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.damage = configDouble("dive-bomb-damage", 14.0);
        this.radius = configDouble("dive-bomb-radius", 5.0);
        this.explosionPower = (float) configDouble("dive-bomb-explosion-power", 3.0);
        this.diveSpeed = configDouble("dive-bomb-dive-speed", 1.8);
        this.diveTicks = configInt("dive-bomb-dive-ticks", 20);
        this.telegraphTicks = configInt("dive-bomb-telegraph-ticks", 20);
        this.recoveryTicks = configInt("dive-bomb-recovery-ticks", 80);
    }

    @Override
    public String name() {
        return "Dive Bomb";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("dive-bomb-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location landingSpot = ctx.target().getLocation().clone();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(landingSpot, radius);
                    Fx.coloredRing(landingSpot, DRAGON_RED, 1.6f, radius, 48, 0);
                    Fx.spinningIcon(plugin, landingSpot.clone().add(0, 3.0, 0), org.bukkit.Material.MAGMA_BLOCK,
                            1.0f, telegraphTicks + 4, 20.0);
                },
                () -> {
                    Vector toLanding = landingSpot.toVector().subtract(boss.getLocation().toVector());
                    Vector dive = (toLanding.lengthSquared() > 1.0E-6 ? toLanding.normalize() : new Vector(0, -1, 0)).multiply(diveSpeed);
                    dive.setY(Math.min(dive.getY(), -0.9));
                    boss.setVelocity(dive);
                    BossAudio.play(boss.getLocation(), "boss.dragon_elder.dive_bomb", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.3f, 0.7f);
                    Fx.sound(boss.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 0.5f);

                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (!boss.isValid()) {
                                cancel();
                                return;
                            }
                            Fx.trail(boss.getLocation().add(0, 0.5, 0), Particle.FLAME, 12, 0.3, 0.02);
                            Fx.coloredBurst(boss.getLocation().add(0, 0.5, 0), DRAGON_RED, 1.2f, 10, 0.3);
                            if (boss.isOnGround() || elapsed >= diveTicks) {
                                cancel();
                                Location landing = boss.getLocation();
                                Fx.expandingRings(plugin, landing, Particle.FLAME, radius, 5, 3L);
                                Fx.coloredBurst(landing.clone().add(0, 1, 0), ELDER_BLACK, 2.4f, 64, 0.7);
                                Fx.coloredBurst(landing.clone().add(0, 1, 0), DRAGON_RED, 1.8f, 36, 0.8);
                                landing.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, landing.clone().add(0, 0.3, 0), 6, 0, 0, 0, 0);
                                BossAudio.play(landing, "boss.dragon_elder.dive_impact", Sound.ENTITY_GENERIC_EXPLODE, 1.7f, 0.6f);
                                Fx.sound(landing, Sound.ENTITY_GENERIC_BIG_FALL, 1.3f, 0.6f);
                                for (Entity nearby : boss.getNearbyEntities(radius, radius, radius)) {
                                    if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                            && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                        target.damage(damage, boss);
                                        target.setFireTicks(60);
                                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                    }
                                }
                                Grief.explosion(ctx, landing, explosionPower);
                                Grief.breakCrater(ctx, landing, radius * 0.7);
                                return;
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                recoveryTicks, onComplete);
    }
}
