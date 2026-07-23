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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** A whirling storm of real bone-block shrapnel bursts from the overlord, shredding and briefly rooting everything around him. */
public final class BoneStormAttack extends BossAttack {

    private static final Color BONE = Color.fromRGB(235, 235, 210);

    private final double damage;
    private final double radius;
    private final int rootTicks;
    private final int telegraphTicks;

    public BoneStormAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.damage = configDouble("bone-storm-damage", 11.0);
        this.radius = configDouble("bone-storm-radius", 6.0);
        this.rootTicks = configInt("bone-storm-root-ticks", 20);
        this.telegraphTicks = configInt("bone-storm-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Bone Storm";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("bone-storm-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, BONE, 1.5f, radius, 44, 0);
                    Fx.helixFrame(origin, Particle.SOUL, 1.3, 8, System.currentTimeMillis() * 0.02, 1.2);
                },
                () -> {
                    BossAudio.play(origin, "boss.necro_overlord.bone_storm", Sound.ENTITY_WITHER_SKELETON_HURT, 1.3f, 0.7f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 1.1f);
                    Fx.expandingRings(plugin, origin, Particle.SCULK_SOUL, radius, 5, 3L);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 0.2, 0), 6, 0, 0, 0, 0);
                    origin.getWorld().spawnParticle(Particle.SOUL, origin.clone().add(0, 1, 0), 240, radius * 0.8, 0.8, radius * 0.8, 0.05);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), BONE, 1.8f, 36, radius * 0.4);
                    for (Entity debris : Fx.shatterDebris(plugin, origin.clone().add(0, 1, 0), Material.BONE_BLOCK, 20, 1.3, 30)) {
                        ctx.instance().trackEntity(debris);
                    }
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, rootTicks, 4));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
