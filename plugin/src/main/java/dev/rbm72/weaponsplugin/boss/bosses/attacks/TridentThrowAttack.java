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

/** The leviathan hurls jagged prismarine spears at the target. */
public final class TridentThrowAttack extends BossAttack {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final int telegraphTicks;

    public TridentThrowAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.damage = configDouble("trident-throw-damage", 9.0);
        this.impactPower = (float) configDouble("trident-throw-impact-power", 1.5);
        this.projectiles = configInt("trident-throw-projectiles", 2);
        this.telegraphTicks = configInt("trident-throw-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Trident Throw";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("trident-throw-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredBurst(origin.clone().add(0, 1.4, 0), TEAL, 1.4f, 17, 0.6);
                },
                () -> {
                    BossAudio.play(origin, "boss.tide_leviathan.trident_throw", Sound.ITEM_TRIDENT_THROW, 1.15f, 0.8f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.0f);
                    Fx.burst(origin.clone().add(0, 1.2, 0), Particle.BUBBLE, 40, 0.5);
                    Fx.coloredBurst(origin.clone().add(0, 1.2, 0), TEAL, 1.5f, 20, 0.5);
                    for (int i = 0; i < projectiles; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 1.4, 0), ctx.target(), Material.PRISMARINE, damage, impactPower);
                    }
                },
                12, onComplete);
    }
}
