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

/** A single sun-fragment plummets from high above the target and detonates on impact. */
public final class MeteorAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 150, 40);

    private final double damage;
    private final double height;
    private final float impactPower;
    private final int telegraphTicks;

    public MeteorAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("meteor-damage", 16.0);
        this.height = configDouble("meteor-height", 25.0);
        this.impactPower = (float) configDouble("meteor-impact-power", 3.5);
        this.telegraphTicks = configInt("meteor-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Meteor";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("meteor-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location impact = ctx.target().getLocation().clone();
        int[] tick = {0};
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(impact, 3.0, tick[0]++ / (double) telegraphTicks);
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredRing(impact, EMBER, 1.6f, 3.3, 38, 0);
                },
                () -> {
                    Location from = impact.clone().add(0, height, 0);
                    Fx.trail(from, Particle.FLAME, 20, 0.5, 0.02);
                    Fx.coloredBurst(from, EMBER, 1.8f, 34, 0.5);
                    Fx.burst(from, Particle.LAVA, 14, 0.4);
                    BossAudio.play(impact, "boss.solar_colossus.meteor", Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
                    Fx.sound(impact, Sound.ITEM_FIRECHARGE_USE, 1.1f, 0.6f);
                    Grief.throwBlock(ctx, from, ctx.target(), Material.MAGMA_BLOCK, damage, impactPower);
                },
                14, onComplete);
    }
}
