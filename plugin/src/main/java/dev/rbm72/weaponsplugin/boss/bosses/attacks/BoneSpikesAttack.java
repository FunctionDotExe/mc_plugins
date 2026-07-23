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

/** Signature grief: jagged bone-block columns erupt from the ground beneath the target, goring anyone near them. */
public final class BoneSpikesAttack extends BossAttack {

    private static final Color BONE = Color.fromRGB(235, 235, 210);

    private final double damage;
    private final double hitRadius;
    private final int telegraphTicks;

    public BoneSpikesAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.damage = configDouble("bone-spikes-damage", 9.0);
        this.hitRadius = configDouble("bone-spikes-hit-radius", 5.0);
        this.telegraphTicks = configInt("bone-spikes-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Bone Spikes";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("bone-spikes-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location target = ctx.target().getLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(target, hitRadius);
                    Fx.coloredRing(target, BONE, 1.4f, hitRadius, 42, 0);
                    Fx.point(target.clone().add(0, 0.2, 0), Particle.SCULK_SOUL, 7);
                },
                () -> {
                    BossAudio.play(target, "boss.necro_overlord.bone_spikes", Sound.ENTITY_WITHER_SKELETON_HURT, 1.3f, 0.6f);
                    Fx.sound(target, Sound.BLOCK_BONE_BLOCK_BREAK, 1.3f, 0.7f);
                    Grief.raiseColumns(ctx, target, Material.BONE_BLOCK, 3, 5, 3, 100);
                    Fx.burst(target.clone().add(0, 1, 0), Particle.SCULK_SOUL, 50, 1.2);
                    Fx.coloredBurst(target.clone().add(0, 1, 0), BONE, 1.8f, 32, 1.0);
                    Fx.expandingRings(plugin, target, Particle.SOUL, hitRadius, 5, 3L);
                    for (Entity nearby : ctx.boss().getWorld().getNearbyEntities(target, hitRadius, hitRadius, hitRadius)) {
                        if (nearby instanceof LivingEntity victim && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            victim.damage(damage, ctx.boss());
                            Fx.bloodSpray(victim.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
