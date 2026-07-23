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

import java.util.concurrent.ThreadLocalRandom;

/** A shower of flaming magma boulders rains down around the target, exploding and setting fire on impact. */
public final class MeteorRainAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);

    private final double damage;
    private final int meteors;
    private final double spread;
    private final double height;
    private final float impactPower;
    private final int telegraphTicks;

    public MeteorRainAttack(WeaponsPlugin plugin) {
        super(plugin, "inferno_warlord");
        this.damage = configDouble("meteor-rain-damage", 10.0);
        this.meteors = configInt("meteor-rain-meteors", 5);
        this.spread = configDouble("meteor-rain-spread", 5.0);
        this.height = configDouble("meteor-rain-height", 20.0);
        this.impactPower = (float) configDouble("meteor-rain-impact-power", 3.0);
        this.telegraphTicks = configInt("meteor-rain-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Meteor Rain";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("meteor-rain-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location targetLoc = ctx.target().getLocation().clone();
        int[] tick = {0};
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(targetLoc, spread, tick[0]++ / (double) telegraphTicks);
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredRing(targetLoc, EMBER, 1.6f, spread, 48, 0);
                },
                () -> {
                    BossAudio.play(targetLoc, "boss.inferno_warlord.meteor_rain", Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.4f);
                    Fx.sound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.6f);
                    Fx.point(targetLoc.clone().add(0, 3, 0), Particle.LAVA, 34);
                    Fx.coloredBurst(targetLoc.clone().add(0, 2, 0), EMBER, 1.8f, 24, spread * 0.3);
                    for (int i = 0; i < meteors; i++) {
                        double ox = ThreadLocalRandom.current().nextDouble(-spread, spread);
                        double oz = ThreadLocalRandom.current().nextDouble(-spread, spread);
                        Location from = targetLoc.clone().add(ox, height, oz);
                        Fx.trail(from, Particle.FLAME, 16, 0.4, 0.02);
                        Grief.throwBlock(ctx, from, ctx.target(), Material.MAGMA_BLOCK, damage, impactPower);
                    }
                },
                14, onComplete);
    }
}
