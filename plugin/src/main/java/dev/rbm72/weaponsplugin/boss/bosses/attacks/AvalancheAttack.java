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

/** Hurls a barrage of snow blocks that crater and concuss on impact. */
public final class AvalancheAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(210, 240, 255);

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final int telegraphTicks;

    public AvalancheAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.damage = configDouble("avalanche-damage", 6.0);
        this.impactPower = (float) configDouble("avalanche-impact-power", 1.5);
        this.projectiles = configInt("avalanche-projectiles", 5);
        this.telegraphTicks = configInt("avalanche-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Avalanche";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("avalanche-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        int[] tick = {0};
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Telegraph.dangerZone(ctx.target().getLocation(), 2.5, tick[0]++ / (double) telegraphTicks);
                    Fx.coloredBurst(origin.clone().add(0, 1.5, 0), FROST, 1.4f, 20, 0.6);
                },
                () -> {
                    BossAudio.play(origin, "boss.frost_queen.avalanche", Sound.ENTITY_GENERIC_BIG_FALL, 1.3f, 0.7f);
                    Fx.sound(origin, Sound.BLOCK_SNOW_BREAK, 1.3f, 0.8f);
                    Fx.burst(origin.clone().add(0, 1.5, 0), Particle.SNOWFLAKE, 50, 0.7);
                    Fx.coloredBurst(origin.clone().add(0, 1.5, 0), FROST, 1.8f, 28, 0.7);
                    origin.getWorld().spawnParticle(Particle.BLOCK, origin.clone().add(0, 1.5, 0), 144, 0.96, 0.96, 0.96, 0.1,
                            Material.SNOW_BLOCK.createBlockData());
                    for (int i = 0; i < projectiles; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 2.0, 0), ctx.target(), Material.SNOW_BLOCK, damage, impactPower);
                    }
                },
                14, onComplete);
    }
}
