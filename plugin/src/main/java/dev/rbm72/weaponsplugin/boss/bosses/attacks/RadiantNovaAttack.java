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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** A blinding pulse of radiance bursts outward from the colossus. */
public final class RadiantNovaAttack extends BossAttack {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);
    private static final Color RADIANT_WHITE = Color.fromRGB(255, 250, 220);

    private final double damage;
    private final double radius;
    private final int blindTicks;
    private final int telegraphTicks;

    public RadiantNovaAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("radiant-nova-damage", 10.0);
        this.radius = configDouble("radiant-nova-radius", 6.0);
        this.blindTicks = configInt("radiant-nova-blind-ticks", 30);
        this.telegraphTicks = configInt("radiant-nova-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Radiant Nova";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("radiant-nova-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, RADIANT_WHITE, 1.6f, radius, 48, 0);
                },
                () -> {
                    Fx.expandingRings(plugin, origin, Particle.END_ROD, radius, 6, 2L);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), SOLAR_GOLD, 1.8f, 85, 0.7);
                    Fx.burst(origin.clone().add(0, 1.2, 0), Particle.END_ROD, 30, 0.6);
                    Fx.flash(origin.clone().add(0, 1.2, 0), 3);
                    BossAudio.play(origin, "boss.solar_colossus.radiant_nova", Sound.BLOCK_BEACON_POWER_SELECT, 1.4f, 1.2f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.4f);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
