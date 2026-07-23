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
import org.bukkit.util.Vector;

/** A cone of corrosive void breath — dragon breath particles that nauseate and gnaw at anything caught in it. */
public final class VoidBreathAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(110, 20, 160);

    private final double damage;
    private final double range;
    private final double angleDegrees;
    private final int nauseaTicks;
    private final int telegraphTicks;

    public VoidBreathAttack(WeaponsPlugin plugin) {
        super(plugin, "voidwyrm");
        this.damage = configDouble("void-breath-damage", 6.0);
        this.range = configDouble("void-breath-range", 8.0);
        this.angleDegrees = configDouble("void-breath-angle-degrees", 50.0);
        this.nauseaTicks = configInt("void-breath-nausea-ticks", 60);
        this.telegraphTicks = configInt("void-breath-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Void Breath";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("void-breath-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation().add(0, 1.2, 0);
        Vector rawDirection = ctx.target().getLocation().toVector().subtract(origin.toVector());
        Vector direction = rawDirection.lengthSquared() > 1.0E-6 ? rawDirection.normalize() : new Vector(1, 0, 0);

        sequence(telegraphTicks,
                () -> Telegraph.cone(origin, direction, angleDegrees, range, Particle.DRAGON_BREATH),
                () -> {
                    BossAudio.play(origin, "boss.voidwyrm.void_breath", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.3f, 0.7f);
                    Telegraph.cone(origin, direction, angleDegrees, range, Particle.DRAGON_BREATH);
                    Fx.coloredBurst(origin, VOID_PURPLE, 1.8f, 30, 0.5);
                    // Corrodes real ground into end stone along the breath, not just a particle haze.
                    Grief.spread(ctx, origin.clone().add(direction.clone().multiply(range * 0.6)), Material.END_STONE, range * 0.4);
                    for (Entity nearby : ctx.boss().getNearbyEntities(range, range, range)) {
                        if (!(nearby instanceof LivingEntity target) || nearby.equals(ctx.boss())
                                || ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            continue;
                        }
                        Vector toTarget = target.getLocation().toVector().subtract(origin.toVector()).normalize();
                        if (direction.dot(toTarget) < Math.cos(Math.toRadians(angleDegrees / 2))) {
                            continue;
                        }
                        target.damage(damage, ctx.boss());
                        StatusEffectManager.apply(target, PotionEffectType.NAUSEA, nauseaTicks, 0);
                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                    }
                },
                14, onComplete);
    }
}
