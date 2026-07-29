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

/** The wyrm climbs high, then dives straight down on the target's position, cratering the ground on landing. */
public final class WyrmDiveAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(110, 20, 160);

    private final double damage;
    private final double radius;
    private final double knockup;
    private final int telegraphTicks;
    private final int climbTicks;

    public WyrmDiveAttack(WeaponsPlugin plugin) {
        super(plugin, "voidwyrm");
        this.damage = configDouble("wyrm-dive-damage", 12.0);
        this.radius = configDouble("wyrm-dive-radius", 4.5);
        this.knockup = configDouble("wyrm-dive-knockup", 0.9);
        this.telegraphTicks = configInt("wyrm-dive-telegraph-ticks", 18);
        this.climbTicks = configInt("wyrm-dive-climb-ticks", 16);
    }

    @Override
    public String name() {
        return "Wyrm Dive";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("wyrm-dive-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location landingSpot = ctx.target().getLocation().clone();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    boss.setVelocity(new Vector(0, 0.6, 0));
                },
                () -> {
                    BossAudio.play(boss.getLocation(), "boss.voidwyrm.dive_climb", Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.7f);
                    Fx.coloredRing(landingSpot, VOID_PURPLE, 1.3f, radius * 0.5, 26, 0);

                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (elapsed >= climbTicks || !boss.isValid()) {
                                cancel();
                                Location above = landingSpot.clone().add(0, 10, 0);
                                if (boss.isValid()) {
                                    boss.teleport(above);
                                }
                                dive(ctx, boss, landingSpot);
                                return;
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                10, onComplete);
    }

    private void dive(AttackContext ctx, LivingEntity boss, Location landingSpot) {
        Fx.burst(boss.getLocation(), Particle.PORTAL, 20, 0.4);
        BossAudio.play(boss.getLocation(), "boss.voidwyrm.dive_swoop", Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 0.9f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !boss.isValid()) {
                    cancel();
                    return;
                }
                if (boss.getLocation().distance(landingSpot) <= 1.5) {
                    cancel();
                    land(ctx, boss, landingSpot);
                    return;
                }
                Vector toLanding = landingSpot.toVector().subtract(boss.getLocation().toVector());
                boss.setVelocity(toLanding.normalize().multiply(1.6));
                Fx.trail(boss.getLocation(), Particle.PORTAL, 6, 0.2, 0.02);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void land(AttackContext ctx, LivingEntity boss, Location landingSpot) {
        Location loc = boss.getLocation();
        Fx.expandingRings(plugin, loc, Particle.PORTAL, radius, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), VOID_PURPLE, 2.0f, 50, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.EXPLOSION, 4, 0.3);
        BossAudio.play(loc, "boss.voidwyrm.dive_land", Sound.ENTITY_GENERIC_BIG_FALL, 1.1f, 0.7f);
        for (Entity nearby : boss.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                    && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                target.damage(damage, boss);
                target.setVelocity(target.getVelocity().add(new Vector(0, knockup, 0)));
                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            }
        }
        Grief.breakCrater(ctx, loc, radius * 0.6);
    }
}
