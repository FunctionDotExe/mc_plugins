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

/** The king rips chunks of ground from beneath him and hurls them at the target. */
public final class SiegeHurlAttack extends BossAttack {

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final int telegraphTicks;

    public SiegeHurlAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("siege-hurl-damage", 9.0);
        this.impactPower = (float) configDouble("siege-hurl-impact-power", 2.0);
        this.projectiles = configInt("siege-hurl-projectiles", 3);
        this.telegraphTicks = configInt("siege-hurl-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Siege Hurl";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("siege-hurl-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredBurst(origin.clone().add(0, 0.3, 0), Color.fromRGB(90, 60, 30), 1.4f, 14, 0.7);
                },
                () -> {
                    BossAudio.play(origin, "boss.fallen_king.siege_hurl", Sound.ENTITY_RAVAGER_ROAR, 1.15f, 0.8f);
                    Fx.blockBurst(origin.clone().add(0, 0.5, 0), Material.DEEPSLATE, 50, 0.9);
                    Fx.coloredBurst(origin.clone().add(0, 0.5, 0), Color.fromRGB(120, 90, 50), 1.6f, 22, 0.7);
                    for (int i = 0; i < projectiles; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 1.2, 0), ctx.target(), Material.DEEPSLATE, damage, impactPower);
                    }
                },
                12, onComplete);
    }
}
