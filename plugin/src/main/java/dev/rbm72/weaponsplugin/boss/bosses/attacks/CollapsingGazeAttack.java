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

/** A single unblinking stare converges into one devastating beam on a marked spot. */
public final class CollapsingGazeAttack extends BossAttack {

    private static final Color SORROW_BLUE = Color.fromRGB(70, 120, 200);

    private final double damage;
    private final double impactRadius;
    private final int telegraphTicks;

    public CollapsingGazeAttack(WeaponsPlugin plugin) {
        super(plugin, "weeping_colossus");
        this.damage = configDouble("collapsing-gaze-damage", 13.0);
        this.impactRadius = configDouble("collapsing-gaze-impact-radius", 2.5);
        this.telegraphTicks = configInt("collapsing-gaze-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Collapsing Gaze";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("collapsing-gaze-cooldown-seconds", 16.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation().add(0, 0.5, 0);
        Location markedSpot = ctx.target().getLocation().clone();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(markedSpot, impactRadius);
                    Telegraph.line(origin, markedSpot.clone().add(0, 1, 0), Particle.SOUL_FIRE_FLAME);
                },
                () -> {
                    BossAudio.play(origin, "boss.weeping_colossus.collapsing_gaze", Sound.ENTITY_GHAST_SHOOT, 1.4f, 0.5f);
                    Fx.coloredBurst(markedSpot.clone().add(0, 1, 0), SORROW_BLUE, 2.2f, 50, 0.8);
                    Fx.burst(markedSpot.clone().add(0, 1, 0), Particle.EXPLOSION, 4, 0.3);
                    Fx.expandingRings(plugin, markedSpot, Particle.SPLASH, impactRadius, 3, 2L);
                    // A real ghast-fireball-style blast, not just an explosion particle.
                    Grief.explosion(ctx, markedSpot, 1.8f);
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
