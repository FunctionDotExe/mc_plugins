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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** All three heads lock onto one spot and unleash a converging beam — devastating if you're still there. */
public final class TripleGazeAttack extends BossAttack {

    private static final Color SOUL_BLUE = Color.fromRGB(30, 200, 210);

    private final double damage;
    private final double impactRadius;
    private final int telegraphTicks;

    public TripleGazeAttack(WeaponsPlugin plugin) {
        super(plugin, "threefold_bane");
        this.damage = configDouble("triple-gaze-damage", 14.0);
        this.impactRadius = configDouble("triple-gaze-impact-radius", 2.5);
        this.telegraphTicks = configInt("triple-gaze-telegraph-ticks", 26);
    }

    @Override
    public String name() {
        return "Triple Gaze";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("triple-gaze-cooldown-seconds", 18.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation().add(0, 1.6, 0);
        Location markedSpot = ctx.target().getLocation().clone();

        sequence(telegraphTicks,
                () -> {
                    Location mark = markedSpot.clone().add(0, 1, 0);
                    Telegraph.dangerZone(markedSpot, impactRadius);
                    for (int head = 0; head < 3; head++) {
                        double angle = (Math.PI * 2 / 3) * head;
                        Location headPos = origin.clone().add(Math.cos(angle) * 1.2, 0.4, Math.sin(angle) * 1.2);
                        Telegraph.line(headPos, mark, Particle.SOUL_FIRE_FLAME);
                    }
                },
                () -> {
                    BossAudio.play(ctx.bossLocation(), "boss.threefold_bane.triple_gaze", Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);
                    Fx.coloredBurst(markedSpot.clone().add(0, 1, 0), SOUL_BLUE, 2.2f, 50, 0.8);
                    Fx.burst(markedSpot.clone().add(0, 1, 0), Particle.EXPLOSION, 4, 0.3);
                    Fx.expandingRings(plugin, markedSpot, Particle.SOUL, impactRadius, 3, 2L);
                    // A real crater where the beams converge, not just a light show.
                    Grief.breakCrater(ctx, markedSpot, impactRadius * 0.7);
                    for (Entity nearby : ctx.boss().getWorld().getNearbyEntities(markedSpot, impactRadius, impactRadius, impactRadius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                16, onComplete);
    }
}
