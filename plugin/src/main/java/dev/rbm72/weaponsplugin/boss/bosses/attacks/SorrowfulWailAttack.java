package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
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

/** A grief-stricken wail rolls out over the arena, slowing and weakening everyone it reaches. */
public final class SorrowfulWailAttack extends BossAttack {

    private static final Color SORROW_BLUE = Color.fromRGB(70, 120, 200);

    private final double radius;
    private final double damage;
    private final int slownessTicks;
    private final int weaknessTicks;
    private final int telegraphTicks;

    public SorrowfulWailAttack(WeaponsPlugin plugin) {
        super(plugin, "weeping_colossus");
        this.radius = configDouble("sorrowful-wail-radius", 8.0);
        this.damage = configDouble("sorrowful-wail-damage", 4.0);
        this.slownessTicks = configInt("sorrowful-wail-slowness-ticks", 70);
        this.weaknessTicks = configInt("sorrowful-wail-weakness-ticks", 70);
        this.telegraphTicks = configInt("sorrowful-wail-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Sorrowful Wail";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("sorrowful-wail-cooldown-seconds", 16.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, SORROW_BLUE, 1.4f, radius * 0.4, 24, 0);
                    Fx.point(origin.clone().add(0, 1, 0), Particle.SPLASH, 5);
                },
                () -> {
                    BossAudio.play(origin, "boss.weeping_colossus.sorrowful_wail", Sound.ENTITY_GHAST_SCREAM, 1.4f, 0.5f);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), SORROW_BLUE, 2.2f, 50, 0.9);
                    Fx.burst(origin.clone().add(0, 1, 0), Particle.SPLASH, 40, 0.8);
                    // The grief actually waterlogs real ground into soul sand, not a color tint.
                    Grief.spread(ctx, origin, Material.SOUL_SAND, radius * 0.6);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            StatusEffectManager.apply(target, PotionEffectType.SLOWNESS, slownessTicks, 1);
                            StatusEffectManager.apply(target, PotionEffectType.WEAKNESS, weaknessTicks, 0);
                        }
                    }
                },
                16, onComplete);
    }
}
