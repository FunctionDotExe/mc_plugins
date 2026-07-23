package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Real rotting tendrils (evoker fangs) erupt from the ground, yanking the target back toward the warden and crippling their legs. */
public final class RottingGraspAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);

    private final double damage;
    private final double pullStrength;
    private final int slowAmplifier;
    private final int slowTicks;
    private final int telegraphTicks;

    public RottingGraspAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.damage = configDouble("rotting-grasp-damage", 8.0);
        this.pullStrength = configDouble("rotting-grasp-pull-strength", 1.4);
        this.slowAmplifier = configInt("rotting-grasp-slow-amplifier", 2); // SLOW III
        this.slowTicks = configInt("rotting-grasp-slow-ticks", 60);
        this.telegraphTicks = configInt("rotting-grasp-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Rotting Grasp";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("rotting-grasp-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Player target = ctx.target();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.line(boss.getLocation().add(0, 1, 0), target.getLocation().add(0, 1, 0),
                            Particle.SPORE_BLOSSOM_AIR);
                    Fx.coloredRing(target.getLocation(), TOXIC, 1.2f, 1.8, 26, 0);
                },
                () -> {
                    BossAudio.play(boss.getLocation(), "boss.plague_warden.rotting_grasp", Sound.ENTITY_ZOMBIE_INFECT, 1.1f, 0.6f);
                    Fx.sound(target.getLocation(), Sound.BLOCK_SCULK_SPREAD, 1.1f, 0.7f);
                    Fx.line(target.getLocation().add(0, 1, 0), boss.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 32);
                    Fx.coloredBurst(target.getLocation().add(0, 1, 0), TOXIC, 1.4f, 34, 0.5);
                    Fx.burst(target.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 16, 0.4);
                    // Real tendrils bursting up around the target's feet, not just particles.
                    EvokerFangs fangs = target.getWorld().spawn(target.getLocation(), EvokerFangs.class);
                    fangs.setOwner(boss);
                    // Yank the target back toward the warden.
                    Vector pull = boss.getLocation().toVector().subtract(target.getLocation().toVector());
                    pull.setY(0);
                    if (pull.lengthSquared() > 1.0e-4) {
                        pull.normalize().multiply(pullStrength);
                    }
                    pull.setY(0.3);
                    target.setVelocity(pull);
                    target.damage(damage, boss);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmplifier));
                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                },
                12, onComplete);
    }
}
