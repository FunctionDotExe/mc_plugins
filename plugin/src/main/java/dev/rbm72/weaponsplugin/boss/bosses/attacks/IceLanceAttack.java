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

/** Volley of packed-ice lances hurled at the current target. */
public final class IceLanceAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(150, 220, 255);

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final int telegraphTicks;

    public IceLanceAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.damage = configDouble("ice-lance-damage", 7.0);
        this.impactPower = (float) configDouble("ice-lance-impact-power", 0.0);
        this.projectiles = configInt("ice-lance-projectiles", 3);
        this.telegraphTicks = configInt("ice-lance-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Ice Lance";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("ice-lance-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Telegraph.line(origin.clone().add(0, 1.2, 0), ctx.target().getLocation().add(0, 1, 0), Particle.SNOWFLAKE);
                    Fx.coloredRing(origin, FROST, 1.2f, 1.8, 26, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.frost_queen.ice_lance", Sound.ENTITY_PLAYER_HURT_FREEZE, 1.1f, 1.2f);
                    Fx.sound(origin, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.4f);
                    Fx.spinningIcon(plugin, origin.clone().add(0, 1.5, 0), Material.PACKED_ICE, 0.7f, 14, 30.0);
                    Fx.coloredBurst(origin.clone().add(0, 1.3, 0), FROST, 1.6f, 40, 0.5);
                    Fx.burst(origin.clone().add(0, 1.3, 0), Particle.SNOWFLAKE, 20, 0.5);
                    for (int i = 0; i < projectiles; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 1.3, 0), ctx.target(), Material.PACKED_ICE, damage, impactPower);
                    }
                },
                12, onComplete);
    }
}
