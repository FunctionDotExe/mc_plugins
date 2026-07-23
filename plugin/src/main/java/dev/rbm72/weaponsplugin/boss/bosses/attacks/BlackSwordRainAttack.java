package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Dark-magic escalation of Sword Rain: more impact points spread across the arena, not just around the target. */
public final class BlackSwordRainAttack extends BossAttack {

    private final double damage;
    private final double impactRadius;
    private final int impactCount;
    private final int telegraphTicks;

    public BlackSwordRainAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("black-sword-rain-damage", 8.0);
        this.impactRadius = configDouble("black-sword-rain-impact-radius", 2.2);
        this.impactCount = configInt("black-sword-rain-impact-count", 6);
        this.telegraphTicks = configInt("black-sword-rain-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Black Sword Rain";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("black-sword-rain-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location arenaCenter = ctx.arena().center();
        double spread = Math.min(ctx.arena().radius(), 8.0);
        List<Location> impactPoints = new ArrayList<>();
        for (int i = 0; i < impactCount; i++) {
            double angle = (2 * Math.PI * i) / impactCount;
            impactPoints.add(arenaCenter.clone().add(Math.cos(angle) * spread * 0.6, 0, Math.sin(angle) * spread * 0.6));
        }
        // One point always lands on the current target so it isn't purely decorative.
        impactPoints.set(0, ctx.target().getLocation().clone());

        sequence(telegraphTicks,
                () -> impactPoints.forEach(point -> {
                    Telegraph.dangerZone(point, impactRadius);
                    Fx.glowPillar(plugin, point.clone().add(0, 0.1, 0), Material.BLACKSTONE, 0.18f, 14f, telegraphTicks);
                }),
                () -> {
                    BossAudio.play(arenaCenter, "boss.fallen_king.black_sword_rain", Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.5f);
                    for (Location point : impactPoints) {
                        Fx.burst(point.clone().add(0, 1, 0), Particle.SQUID_INK, 30, 0.4);
                        Fx.burst(point.clone().add(0, 1, 0), Particle.SMOKE, 18, 0.3);
                        Fx.burst(point.clone().add(0, 1, 0), Particle.ASH, 16, 0.4);
                        Fx.flash(point.clone().add(0, 1, 0), 1);
                        Fx.sound(point, Sound.ITEM_TRIDENT_HIT_GROUND, 1.3f, 0.6f);
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
                12, onComplete);
    }
}
