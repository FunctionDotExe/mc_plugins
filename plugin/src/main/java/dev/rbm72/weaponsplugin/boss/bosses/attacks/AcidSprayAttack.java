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

/** A cone of corrosive ooze spat forward — eats through armor as much as health. */
public final class AcidSprayAttack extends BossAttack {

    private static final Color OOZE_GREEN = Color.fromRGB(90, 170, 70);

    private final double damage;
    private final double range;
    private final double angleDegrees;
    private final int poisonTicks;
    private final int telegraphTicks;

    public AcidSprayAttack(WeaponsPlugin plugin) {
        super(plugin, "amalgamated_bulk");
        this.damage = configDouble("acid-spray-damage", 6.0);
        this.range = configDouble("acid-spray-range", 6.0);
        this.angleDegrees = configDouble("acid-spray-angle-degrees", 45.0);
        this.poisonTicks = configInt("acid-spray-poison-ticks", 60);
        this.telegraphTicks = configInt("acid-spray-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Acid Spray";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("acid-spray-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation().add(0, 0.6, 0);
        Vector rawDirection = ctx.target().getLocation().toVector().subtract(origin.toVector());
        Vector direction = rawDirection.lengthSquared() > 1.0E-6 ? rawDirection.normalize() : new Vector(1, 0, 0);

        sequence(telegraphTicks,
                () -> Telegraph.cone(origin, direction, angleDegrees, range, Particle.ITEM_SLIME),
                () -> {
                    BossAudio.play(origin, "boss.amalgamated_bulk.acid_spray", Sound.ENTITY_SLIME_ATTACK, 1.2f, 0.6f);
                    Telegraph.cone(origin, direction, angleDegrees, range, Particle.SPORE_BLOSSOM_AIR);
                    Fx.coloredBurst(origin, OOZE_GREEN, 1.6f, 26, 0.4);
                    // Real corroded mud where the spray lands, not just a green tint.
                    Grief.spread(ctx, origin.clone().add(direction.clone().multiply(range * 0.6)), Material.MUD, range * 0.35);
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
                        StatusEffectManager.apply(target, PotionEffectType.POISON, poisonTicks, 1);
                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                    }
                },
                12, onComplete);
    }
}
