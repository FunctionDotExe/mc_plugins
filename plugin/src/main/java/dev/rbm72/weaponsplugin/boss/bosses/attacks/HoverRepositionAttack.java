package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

/**
 * The dragon's flight model — expressed entirely as a short-cooldown attack so
 * it re-issues through the standard attack loop with no long-lived task. Each
 * telegraph tick nudges the dragon's vertical position toward a point ~8 blocks
 * above the target (the arena tick loop already handles horizontal chasing via
 * the pathfinder), keeping it airborne and menacing between its real attacks.
 */
public final class HoverRepositionAttack extends BossAttack {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);

    private final double hoverHeight;
    private final double nudgeStrength;
    private final double maxSpeed;
    private final int telegraphTicks;

    public HoverRepositionAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.hoverHeight = configDouble("hover-height", 8.0);
        this.nudgeStrength = configDouble("hover-nudge-strength", 0.18);
        this.maxSpeed = configDouble("hover-max-speed", 0.9);
        this.telegraphTicks = configInt("hover-telegraph-ticks", 10);
    }

    @Override
    public String name() {
        return "Hover";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("hover-cooldown-seconds", 2.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    if (!ctx.boss().isValid()) {
                        return;
                    }
                    Location bossLoc = ctx.boss().getLocation();
                    Location desired = ctx.target().getLocation().clone().add(0, hoverHeight, 0);
                    Vector toDesired = desired.toVector().subtract(bossLoc.toVector()).multiply(nudgeStrength);
                    if (toDesired.length() > maxSpeed) {
                        toDesired = toDesired.normalize().multiply(maxSpeed);
                    }
                    ctx.boss().setVelocity(toDesired);
                    // Faint downdraft under the beating wings.
                    Fx.point(bossLoc.clone().add(0, -0.5, 0), Particle.FLAME, 3);
                    Fx.coloredBurst(bossLoc.clone().add(0, 0.5, 0), DRAGON_RED, 1.0f, 5, 0.6);
                },
                () -> {
                    Location bossLoc = ctx.boss().getLocation();
                    Fx.ring(bossLoc, Particle.SMOKE, 2.0, 18);
                    BossAudio.play(bossLoc, "boss.dragon_elder.wingbeat", Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 0.9f);
                    Fx.sound(bossLoc, Sound.ENTITY_PHANTOM_FLAP, 0.6f, 0.7f);
                },
                0, onComplete);
    }
}
