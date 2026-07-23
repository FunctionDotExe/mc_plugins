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

/** Hurls a globule of festering slime at the target, poisoning on impact. */
public final class PlagueBoltAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final int poisonTicks;
    private final int telegraphTicks;

    public PlagueBoltAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.damage = configDouble("plague-bolt-damage", 7.0);
        this.impactPower = (float) configDouble("plague-bolt-impact-power", 0.0);
        this.projectiles = configInt("plague-bolt-projectiles", 1);
        this.poisonTicks = configInt("plague-bolt-poison-ticks", 100);
        this.telegraphTicks = configInt("plague-bolt-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Plague Bolt";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("plague-bolt-cooldown-seconds", 6.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), TOXIC, 1.3f, 13, 0.4);
                },
                () -> {
                    BossAudio.play(origin, "boss.plague_warden.plague_bolt", Sound.ENTITY_WITCH_THROW, 1.1f, 1.0f);
                    Fx.sound(origin, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.7f);
                    Fx.burst(origin.clone().add(0, 1.2, 0), Particle.ITEM_SLIME, 40, 0.5);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), TOXIC, 1.5f, 18, 0.45);
                    for (int i = 0; i < projectiles; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 1.2, 0), ctx.target(), Material.SLIME_BLOCK,
                                damage, impactPower);
                    }
                    // The bolt is aimed at the current target — coat them in a lingering infection on release.
                    ctx.target().addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, 0));
                },
                12, onComplete);
    }
}
