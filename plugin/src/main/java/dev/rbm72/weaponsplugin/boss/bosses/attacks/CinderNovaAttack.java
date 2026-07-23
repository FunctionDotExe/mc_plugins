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

/** A bursting ring of cinders erupts from the warlord, scorching everyone around him. */
public final class CinderNovaAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);
    private static final Color DEEP_FIRE = Color.fromRGB(200, 40, 0);

    private final double damage;
    private final double radius;
    private final int fireTicks;
    private final float explosionPower;
    private final int telegraphTicks;

    public CinderNovaAttack(WeaponsPlugin plugin) {
        super(plugin, "inferno_warlord");
        this.damage = configDouble("cinder-nova-damage", 10.0);
        this.radius = configDouble("cinder-nova-radius", 6.0);
        this.fireTicks = configInt("cinder-nova-fire-ticks", 80);
        this.explosionPower = (float) configDouble("cinder-nova-explosion-power", 1.4);
        this.telegraphTicks = configInt("cinder-nova-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Cinder Nova";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("cinder-nova-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, EMBER, 1.5f, radius, 48, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.inferno_warlord.cinder_nova", Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.7f);
                    Fx.expandingRings(plugin, origin, Particle.FLAME, radius, 5, 3L);
                    Fx.expandingRings(plugin, origin, Particle.LAVA, radius * 0.6, 4, 4L);
                    Grief.explosion(ctx, origin.clone().add(0, 0.3, 0), explosionPower);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), DEEP_FIRE, 2.4f, 68, radius * 0.35);
                    Fx.coloredBurst(origin.clone().add(0, 1, 0), EMBER, 1.8f, 40, radius * 0.45);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.setFireTicks(fireTicks);
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                12, onComplete);
    }
}
