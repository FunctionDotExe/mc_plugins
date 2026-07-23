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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Hurls a bolt of condensed bone at the target, withering whoever it strikes. */
public final class DeathBoltAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final double damage;
    private final float impactPower;
    private final int witherTicks;
    private final int telegraphTicks;

    public DeathBoltAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.damage = configDouble("death-bolt-damage", 8.0);
        this.impactPower = (float) configDouble("death-bolt-impact-power", 1.0);
        this.witherTicks = configInt("death-bolt-wither-ticks", 80);
        this.telegraphTicks = configInt("death-bolt-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Death Bolt";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("death-bolt-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredBurst(origin.clone().add(0, 1.4, 0), NECROTIC, 1.3f, 13, 0.4);
                    Fx.point(origin.clone().add(0, 1.4, 0), Particle.SCULK_SOUL, 5);
                },
                () -> {
                    BossAudio.play(origin, "boss.necro_overlord.death_bolt", Sound.ENTITY_WITHER_SHOOT, 1.3f, 0.8f);
                    Fx.sound(origin, Sound.PARTICLE_SOUL_ESCAPE, 1.2f, 0.6f);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), NECROTIC, 2.0f, 32, 0.5);
                    Fx.burst(origin.clone().add(0, 1.2, 0), Particle.SOUL, 30, 0.4);
                    Grief.throwBlock(ctx, origin.clone().add(0, 1.2, 0), ctx.target(), Material.BONE_BLOCK, damage, impactPower);
                    ctx.target().addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, 0));
                },
                12, onComplete);
    }
}
