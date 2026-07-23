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

/** The warlord tears a chunk of magma from the ground and hurls it at the target. */
public final class MagmaThrowAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);

    private final double damage;
    private final int projectiles;
    private final float impactPower;
    private final int telegraphTicks;

    public MagmaThrowAttack(WeaponsPlugin plugin) {
        super(plugin, "inferno_warlord");
        this.damage = configDouble("magma-throw-damage", 10.0);
        this.projectiles = configInt("magma-throw-projectiles", 2);
        this.impactPower = (float) configDouble("magma-throw-impact-power", 3.0);
        this.telegraphTicks = configInt("magma-throw-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Magma Throw";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("magma-throw-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), EMBER, 1.5f, 17, 0.5);
                    Fx.point(origin.clone().add(0, 1.5, 0), Particle.LAVA, 7);
                },
                () -> {
                    BossAudio.play(origin, "boss.inferno_warlord.magma_throw", Sound.ENTITY_BLAZE_SHOOT, 1.3f, 0.6f);
                    Fx.sound(origin, Sound.ENTITY_GHAST_SHOOT, 1.1f, 0.7f);
                    Fx.burst(origin.clone().add(0, 1.2, 0), Particle.FLAME, 44, 0.6);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), EMBER, 1.6f, 30, 0.5);
                    for (int i = 0; i < projectiles; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 1.4, 0), ctx.target(), Material.MAGMA_BLOCK, damage, impactPower);
                    }
                },
                12, onComplete);
    }
}
