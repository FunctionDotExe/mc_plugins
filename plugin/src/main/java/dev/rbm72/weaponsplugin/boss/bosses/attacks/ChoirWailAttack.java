package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

/** A dissonant chorus of every voice it's ever swallowed — no damage on its own, but it leaves everyone worse for it. */
public final class ChoirWailAttack extends BossAttack {

    private static final Color PALE_VIOLET = Color.fromRGB(160, 130, 220);

    private final double radius;
    private final double damage;
    private final int weaknessTicks;
    private final int slownessTicks;
    private final int darknessTicks;
    private final int telegraphTicks;

    public ChoirWailAttack(WeaponsPlugin plugin) {
        super(plugin, "hollow_choir");
        this.radius = configDouble("choir-wail-radius", 7.0);
        this.damage = configDouble("choir-wail-damage", 4.0);
        this.weaknessTicks = configInt("choir-wail-weakness-ticks", 100);
        this.slownessTicks = configInt("choir-wail-slowness-ticks", 60);
        this.darknessTicks = configInt("choir-wail-darkness-ticks", 40);
        this.telegraphTicks = configInt("choir-wail-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Choir Wail";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("choir-wail-cooldown-seconds", 22.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, PALE_VIOLET, 1.4f, radius * 0.4, 24, 0);
                    Fx.point(origin.clone().add(0, 1.4, 0), Particle.SOUL, 4);
                },
                () -> {
                    BossAudio.play(origin, "boss.hollow_choir.wail", Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.3f, 0.5f);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), PALE_VIOLET, 2.2f, 50, 0.9);
                    Fx.burst(origin.clone().add(0, 1.2, 0), Particle.SOUL, 40, 0.8);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            StatusEffectManager.apply(target, PotionEffectType.WEAKNESS, weaknessTicks, 0);
                            StatusEffectManager.apply(target, PotionEffectType.SLOWNESS, slownessTicks, 0);
                            StatusEffectManager.apply(target, PotionEffectType.DARKNESS, darknessTicks, 0);
                        }
                    }
                },
                18, onComplete);
    }
}
