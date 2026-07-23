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

/**
 * Signature grief move: telegraphs the target's position, then calls a real
 * lightning bolt (which naturally sets fire) when grief is enabled, or a
 * cosmetic strike plus manual damage when grief is off.
 */
public final class ThunderstrikeAttack extends BossAttack {

    private static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);
    private static final Color STORM_WHITE = Color.fromRGB(235, 245, 255);

    private final double damage;
    private final double radius;
    private final int telegraphTicks;

    public ThunderstrikeAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.damage = configDouble("thunderstrike-damage", 12.0);
        this.radius = configDouble("thunderstrike-radius", 3.0);
        this.telegraphTicks = configInt("thunderstrike-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Thunderstrike";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("thunderstrike-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        // Snapshot the target's ground position — the bolt lands where they stood when the telegraph started.
        Location strike = ctx.target().getLocation().clone();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(strike, radius);
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredRing(strike, STORM_YELLOW, 1.6f, radius, 42, 0);
                    Fx.point(strike.clone().add(0, 3.0, 0), Particle.ELECTRIC_SPARK, 10);
                },
                () -> {
                    BossAudio.play(strike, "boss.storm_tyrant.thunderstrike", Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.35f, 0.8f);
                    Fx.sound(strike, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.15f, 1.0f);
                    Fx.coloredBurst(strike.clone().add(0, 1, 0), STORM_WHITE, 2.2f, 66, radius * 0.4);
                    Fx.burst(strike.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 24, 0.5);
                    Fx.expandingRings(plugin, strike, Particle.ELECTRIC_SPARK, radius, 4, 3L);
                    strike.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, strike.clone().add(0, 0.5, 0), 6, 0, 0, 0, 0);

                    // Lightning: real bolt (sets fire) when grief is on, else a cosmetic strike.
                    if (Grief.enabled(ctx)) {
                        strike.getWorld().strikeLightning(strike);
                    } else {
                        strike.getWorld().strikeLightningEffect(strike);
                    }
                    // The attack's own AoE damage always lands so the move hits identically in both modes.
                    for (Entity nearby : strike.getWorld().getNearbyEntities(strike, radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
