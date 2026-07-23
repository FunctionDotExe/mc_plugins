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
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Flying-sword projectiles reimagined as telegraphed impact zones landing around the target. */
public final class SwordRainAttack extends BossAttack {

    private final double damage;
    private final double impactRadius;
    private final int impactCount;
    private final int telegraphTicks;

    public SwordRainAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("sword-rain-damage", 7.0);
        this.impactRadius = configDouble("sword-rain-impact-radius", 2.0);
        this.impactCount = configInt("sword-rain-impact-count", 4);
        this.telegraphTicks = configInt("sword-rain-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Sword Rain";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("sword-rain-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location targetCenter = ctx.target().getLocation();
        List<Location> impactPoints = new ArrayList<>();
        for (int i = 0; i < impactCount; i++) {
            double angle = (2 * Math.PI * i) / impactCount;
            impactPoints.add(targetCenter.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5));
        }

        sequence(telegraphTicks,
                () -> impactPoints.forEach(point -> {
                    Telegraph.dangerZone(point, impactRadius);
                    // A literal beam of light marking exactly where each sword is about to fall.
                    Fx.glowPillar(plugin, point.clone().add(0, 0.1, 0), Material.QUARTZ_BLOCK, 0.2f, 12f, telegraphTicks);
                }),
                () -> {
                    BossAudio.play(targetCenter, "boss.fallen_king.sword_rain", Sound.ITEM_TRIDENT_THUNDER, 1.15f, 1.2f);
                    for (Location point : impactPoints) {
                        Fx.burst(point.clone().add(0, 1, 0), Particle.CRIT, 34, 0.6);
                        Fx.coloredBurst(point.clone().add(0, 1, 0), Color.fromRGB(200, 200, 210), 1.4f, 20, 0.5);
                        Fx.flash(point.clone().add(0, 1, 0), 2);
                        Fx.sound(point, Sound.ITEM_TRIDENT_HIT_GROUND, 1.1f, 1.1f);
                        World world = point.getWorld();
                        if (world == null) {
                            continue;
                        }
                        for (Entity nearby : world.getNearbyEntities(point, impactRadius, impactRadius, impactRadius)) {
                            if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                    && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                target.damage(damage, ctx.boss());
                                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                            }
                        }
                    }
                },
                10, onComplete);
    }
}
