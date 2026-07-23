package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Signature grief move: rotting corruption creeps outward across the ground
 * (blocks turn to mud), festering wider with every cast, poisoning anyone
 * caught in the bloom.
 */
public final class CorruptionSpreadAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);
    private static final Color SICKLY = Color.fromRGB(150, 200, 80);

    private final double startRadius;
    private final double maxRadius;
    private final double growthPerCast;
    private final double damage;
    private final int telegraphTicks;

    private double currentRadius;

    public CorruptionSpreadAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.startRadius = configDouble("corruption-spread-start-radius", 3.0);
        this.maxRadius = configDouble("corruption-spread-max-radius", 6.0);
        this.growthPerCast = configDouble("corruption-spread-growth-per-cast", 1.0);
        this.damage = configDouble("corruption-spread-damage", 6.0);
        this.telegraphTicks = configInt("corruption-spread-telegraph-ticks", 18);
        this.currentRadius = startRadius;
    }

    @Override
    public String name() {
        return "Corruption Spread";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("corruption-spread-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        double radius = currentRadius;
        currentRadius = Math.min(maxRadius, currentRadius + growthPerCast);
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, TOXIC, 1.5f, radius, 48, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.plague_warden.corruption_spread", Sound.BLOCK_SCULK_SPREAD, 1.5f, 0.5f);
                    Fx.sound(origin, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.3f, 0.6f);
                    Fx.expandingRings(plugin, origin, Particle.SPORE_BLOSSOM_AIR, radius, 5, 3L);
                    Fx.coloredBurst(origin.clone().add(0, 0.3, 0), SICKLY, 2.1f, 68, radius * 0.4);
                    Fx.coloredBurst(origin.clone().add(0, 0.3, 0), TOXIC, 1.7f, 40, radius * 0.3);
                    Grief.spread(ctx, origin, Material.MUD, radius);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
