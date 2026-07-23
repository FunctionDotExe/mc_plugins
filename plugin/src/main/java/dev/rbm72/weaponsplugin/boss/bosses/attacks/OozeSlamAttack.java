package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** A vertical bounce that slams down in place, splattering sticky ooze that mires everyone nearby. */
public final class OozeSlamAttack extends BossAttack {

    private static final Color OOZE_GREEN = Color.fromRGB(90, 170, 70);

    private final double damage;
    private final double radius;
    private final int slownessTicks;
    private final int telegraphTicks;
    private final int airTicks;

    public OozeSlamAttack(WeaponsPlugin plugin) {
        super(plugin, "amalgamated_bulk");
        this.damage = configDouble("ooze-slam-damage", 8.0);
        this.radius = configDouble("ooze-slam-radius", 4.5);
        this.slownessTicks = configInt("ooze-slam-slowness-ticks", 70);
        this.telegraphTicks = configInt("ooze-slam-telegraph-ticks", 14);
        this.airTicks = configInt("ooze-slam-air-ticks", 10);
    }

    @Override
    public String name() {
        return "Ooze Slam";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("ooze-slam-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location origin = boss.getLocation();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(origin, radius, Particle.ITEM_SLIME);
                    boss.setVelocity(new Vector(0, 0.7, 0));
                },
                () -> {
                    BossAudio.play(origin, "boss.amalgamated_bulk.ooze_bounce", Sound.ENTITY_SLIME_JUMP, 1.1f, 0.6f);

                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (elapsed >= airTicks || !boss.isValid()) {
                                cancel();
                                Location loc = boss.getLocation();
                                Fx.coloredBurst(loc.clone().add(0, 0.5, 0), OOZE_GREEN, 2.0f, 44, 0.7);
                                Fx.burst(loc.clone().add(0, 0.5, 0), Particle.ITEM_SLIME, 30, 0.6);
                                BossAudio.play(loc, "boss.amalgamated_bulk.ooze_land", Sound.ENTITY_SLIME_SQUISH, 1.3f, 0.6f);
                                // Leaves a real bouncy slime patch, not just a splatter of particles.
                                Grief.raiseColumns(ctx, loc, Material.SLIME_BLOCK, 1, 4, radius * 0.7, 200);
                                for (Entity nearby : boss.getNearbyEntities(radius, radius, radius)) {
                                    if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                            && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                        target.damage(damage, boss);
                                        StatusEffectManager.apply(target, PotionEffectType.SLOWNESS, slownessTicks, 1);
                                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                    }
                                }
                                return;
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                10, onComplete);
    }
}
