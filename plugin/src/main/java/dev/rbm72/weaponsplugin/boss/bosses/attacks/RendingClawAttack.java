package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** The Grafted Horror's baseline opener — a forward claw rake that rends tendons, weakening whatever it hits. */
public final class RendingClawAttack extends BossAttack {

    private static final Color RUST = Color.fromRGB(140, 60, 30);

    private final double damage;
    private final double range;
    private final int telegraphTicks;
    private final int weaknessTicks;

    public RendingClawAttack(WeaponsPlugin plugin) {
        super(plugin, "grafted_horror");
        this.damage = configDouble("rending-claw-damage", 8.0);
        this.range = configDouble("rending-claw-range", 3.5);
        this.telegraphTicks = configInt("rending-claw-telegraph-ticks", 12);
        this.weaknessTicks = configInt("rending-claw-weakness-ticks", 60);
    }

    @Override
    public String name() {
        return "Rending Claw";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("rending-claw-cooldown-seconds", 5.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        Vector rawDirection = ctx.target().getLocation().toVector().subtract(origin.toVector());
        Vector direction = rawDirection.lengthSquared() > 1.0E-6 ? rawDirection : new Vector(1, 0, 0);

        sequence(telegraphTicks,
                () -> {
                    Telegraph.cone(origin.clone().add(0, 1, 0), direction, 70, range, Particle.CRIT);
                    Fx.coloredRing(origin, RUST, 1.2f, range * 0.6, 28, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.grafted_horror.rending_claw", Sound.ENTITY_RAVAGER_ATTACK, 1.1f, 0.8f);
                    Fx.sound(origin, Sound.ENTITY_WOLF_GROWL, 1.0f, 0.6f);
                    Telegraph.cone(origin.clone().add(0, 1, 0), direction, 90, range, Particle.SWEEP_ATTACK);
                    Fx.coloredBurst(origin.clone().add(direction.clone().normalize().multiply(range * 0.5)).add(0, 1, 0),
                            RUST, 1.4f, 30, 0.5);
                    for (Entity nearby : ctx.boss().getNearbyEntities(range, range, range)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            StatusEffectManager.apply(target, PotionEffectType.WEAKNESS, weaknessTicks, 0);
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                10, onComplete);
    }
}
