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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/** A short thunderstorm: several bolts crash down at random points across the arena. */
public final class ThunderstormAttack extends BossAttack {

    private static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);

    private final double damage;
    private final double strikeRadius;
    private final int strikes;
    private final int durationTicks;
    private final int telegraphTicks;

    public ThunderstormAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.damage = configDouble("thunderstorm-damage", 10.0);
        this.strikeRadius = configDouble("thunderstorm-strike-radius", 3.0);
        this.strikes = configInt("thunderstorm-strikes", 4);
        this.durationTicks = configInt("thunderstorm-duration-ticks", 100);
        this.telegraphTicks = configInt("thunderstorm-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Thunderstorm";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("thunderstorm-cooldown-seconds", 18.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        double arenaRadius = ctx.arena().radius();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(ctx.arena().center(), arenaRadius * 0.9, Particle.ELECTRIC_SPARK);
                    Fx.coloredRing(origin, STORM_YELLOW, 1.4f, 4.6, 36, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.storm_tyrant.thunderstorm", Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.35f, 0.6f);
                    Fx.sound(origin, Sound.WEATHER_RAIN, 1.1f, 0.7f);
                    Location center = ctx.arena().center();
                    // Spread the strikes evenly across the duration so they land as a rolling storm.
                    int interval = Math.max(1, durationTicks / Math.max(1, strikes));
                    // Each strike now marks its landing spot warningTicks ahead of time instead of
                    // hitting the instant it's picked — a random bolt with zero warning isn't
                    // dodgeable, it's a coin flip.
                    int warningTicks = Math.min(interval - 1, 15);
                    new BukkitRunnable() {
                        int fired = 0;
                        int ticks = 0;
                        Location pending;
                        int pendingTicksLeft;

                        @Override
                        public void run() {
                            if (fired >= strikes || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            if (ticks % interval == 0) {
                                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                                double dist = ThreadLocalRandom.current().nextDouble(arenaRadius * 0.85);
                                pending = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                                pending.setY(center.getWorld().getHighestBlockYAt(pending) + 1);
                                pendingTicksLeft = warningTicks;
                            }
                            if (pending != null) {
                                Telegraph.dangerZone(pending, strikeRadius);
                                Fx.point(pending.clone().add(0, 3.0, 0), Particle.ELECTRIC_SPARK, 4);
                                pendingTicksLeft--;
                                if (pendingTicksLeft <= 0) {
                                    strike(ctx, pending);
                                    pending = null;
                                    fired++;
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                14, onComplete);
    }

    private void strike(AttackContext ctx, Location strike) {
        if (Grief.enabled(ctx)) {
            strike.getWorld().strikeLightning(strike);
        } else {
            strike.getWorld().strikeLightningEffect(strike);
        }
        Fx.coloredBurst(strike.clone().add(0, 1, 0), STORM_YELLOW, 1.8f, 40, strikeRadius * 0.4);
        Fx.burst(strike.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 20, 0.5);
        Fx.point(strike.clone().add(0, 1, 0), Particle.EXPLOSION_EMITTER, 2);
        Fx.sound(strike, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.15f, 1.0f);
        for (Entity nearby : strike.getWorld().getNearbyEntities(strike, strikeRadius, strikeRadius, strikeRadius)) {
            if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                    && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                target.damage(damage, ctx.boss());
                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            }
        }
    }
}
