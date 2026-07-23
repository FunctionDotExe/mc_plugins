package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.ai.Movement;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** The ground erupts beneath the target — a crater blasts open, flinging everyone nearby skyward in flames. */
public final class EruptionAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);
    private static final Color DEEP_FIRE = Color.fromRGB(200, 40, 0);

    private final double damage;
    private final double radius;
    private final double craterRadius;
    private final double launchUp;
    private final int fireTicks;
    private final int telegraphTicks;

    public EruptionAttack(WeaponsPlugin plugin) {
        super(plugin, "inferno_warlord");
        this.damage = configDouble("eruption-damage", 12.0);
        this.radius = configDouble("eruption-radius", 5.0);
        this.craterRadius = configDouble("eruption-crater-radius", 3.0);
        this.launchUp = configDouble("eruption-launch-up", 0.9);
        this.fireTicks = configInt("eruption-fire-ticks", 80);
        this.telegraphTicks = configInt("eruption-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Eruption";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("eruption-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location target = ctx.target().getLocation().clone();
        int[] tick = {0};
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(target, radius, tick[0]++ / (double) telegraphTicks);
                    Fx.coloredRing(target, DEEP_FIRE, 1.6f, radius, 48, 0);
                    Fx.point(target.clone().add(0, 0.3, 0), Particle.LAVA, 10);
                },
                () -> {
                    BossAudio.play(target, "boss.inferno_warlord.eruption", Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.5f);
                    Fx.sound(target, Sound.BLOCK_LAVA_POP, 1.3f, 0.6f);
                    Fx.expandingRings(plugin, target, Particle.FLAME, radius, 5, 3L);
                    target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.clone().add(0, 0.5, 0), 9, 0.8, 0.8, 0.8, 0);
                    Fx.coloredBurst(target.clone().add(0, 1, 0), EMBER, 2.4f, 68, radius * 0.4);
                    Fx.coloredBurst(target.clone().add(0, 1, 0), DEEP_FIRE, 1.8f, 40, radius * 0.5);
                    for (Entity nearby : target.getWorld().getNearbyEntities(target, radius, radius, radius)) {
                        if (nearby instanceof LivingEntity victim && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            victim.damage(damage, ctx.boss());
                            victim.setFireTicks(fireTicks);
                            Movement.launchTarget(victim, launchUp);
                            Fx.bloodSpray(victim.getLocation().add(0, 1, 0));
                        }
                    }
                    Grief.breakCrater(ctx, target, craterRadius);
                },
                14, onComplete);
    }
}
