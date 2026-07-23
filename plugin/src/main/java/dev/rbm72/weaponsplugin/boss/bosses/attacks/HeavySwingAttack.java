package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** Basic forward cone strike — the boss's bread-and-butter melee opener. */
public final class HeavySwingAttack extends BossAttack {

    private final double damage;
    private final double range;
    private final int telegraphTicks;

    public HeavySwingAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("heavy-swing-damage", 9.0);
        this.range = configDouble("heavy-swing-range", 3.5);
        this.telegraphTicks = configInt("heavy-swing-telegraph-ticks", 14);
    }

    @Override
    public String name() {
        return "Heavy Swing";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("heavy-swing-cooldown-seconds", 3.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        Vector rawDirection = ctx.target().getLocation().toVector().subtract(origin.toVector());
        Vector direction = rawDirection.lengthSquared() > 1.0E-6 ? rawDirection : new Vector(1, 0, 0);

        sequence(telegraphTicks,
                () -> {
                    Telegraph.cone(origin.clone().add(0, 1, 0), direction, 70, range, Particle.CRIT);
                    Fx.coloredRing(origin, Color.fromRGB(180, 30, 30), 1.2f, range * 0.6, 32, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.fallen_king.heavy_swing", Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.1f, 0.8f);
                    Fx.sound(origin, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.1f, 0.7f);
                    Telegraph.cone(origin.clone().add(0, 1, 0), direction, 90, range, Particle.SWEEP_ATTACK);
                    Fx.coloredBurst(origin.clone().add(direction.clone().normalize().multiply(range * 0.5)).add(0, 1, 0),
                            Color.fromRGB(200, 40, 40), 1.4f, 34, 0.5);
                    Fx.burst(origin.clone().add(direction.clone().normalize().multiply(range * 0.5)).add(0, 1, 0),
                            Particle.CRIT, 16, 0.4);
                    for (Entity nearby : ctx.boss().getNearbyEntities(range, range, range)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                10, onComplete);
    }
}
