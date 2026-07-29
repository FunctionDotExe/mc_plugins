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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Enrage finisher: a rolling barrage of lightning that sweeps outward across
 * the whole arena in an expanding ring, jolting every player as it rolls.
 */
public final class StormcallAttack extends BossAttack {

    private static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);
    private static final Color STORM_WHITE = Color.fromRGB(235, 245, 255);

    private final double damage;
    private final double strikeRadius;
    private final int stepsPerRing;
    private final double nudge;
    private final int barrageTicks;
    private final int stepInterval;
    private final int telegraphTicks;

    public StormcallAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.damage = configDouble("stormcall-damage", 14.0);
        this.strikeRadius = configDouble("stormcall-strike-radius", 3.0);
        this.stepsPerRing = configInt("stormcall-steps-per-ring", 8);
        this.nudge = configDouble("stormcall-nudge", 0.25);
        this.barrageTicks = configInt("stormcall-barrage-ticks", 30);
        this.stepInterval = configInt("stormcall-step-interval", 5);
        this.telegraphTicks = configInt("stormcall-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Stormcall";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("stormcall-cooldown-seconds", 24.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, arenaRadius * 0.9, Particle.ELECTRIC_SPARK);
                    Fx.coloredRing(ctx.bossLocation(), STORM_YELLOW, 1.8f, 4.6, 48, 0);
                    Fx.point(ctx.bossLocation().clone().add(0, 3, 0), Particle.ELECTRIC_SPARK, 14);
                },
                () -> {
                    BossAudio.play(center, "boss.storm_tyrant.stormcall", Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.55f, 0.5f);
                    Fx.sound(center, Sound.ENTITY_WITHER_SPAWN, 1.15f, 0.6f);
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= barrageTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            if (ticks % stepInterval == 0) {
                                double progress = ticks / (double) barrageTicks;
                                double ringRadius = arenaRadius * 0.9 * progress + 1.0;
                                for (int i = 0; i < stepsPerRing; i++) {
                                    double angle = (2 * Math.PI * i) / stepsPerRing + progress;
                                    Location strike = center.clone().add(Math.cos(angle) * ringRadius, 0, Math.sin(angle) * ringRadius);
                                    strike.setY(center.getWorld().getHighestBlockYAt(strike) + 1);
                                    if (Grief.enabled(ctx)) {
                                        strike.getWorld().strikeLightning(strike);
                                    } else {
                                        strike.getWorld().strikeLightningEffect(strike);
                                    }
                                    Fx.coloredBurst(strike.clone().add(0, 1, 0), STORM_WHITE, 1.6f, 26, strikeRadius * 0.4);
                                    Fx.burst(strike.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 16, 0.4);
                                    for (Entity nearby : strike.getWorld().getNearbyEntities(strike, strikeRadius, strikeRadius, strikeRadius)) {
                                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                            target.damage(damage, ctx.boss());
                                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                        }
                                    }
                                }
                                // Screen-shake: small vertical nudges to every player in the arena. This is
                                // the one shove in the roster that carries no mechanic — it is feedback for
                                // the barrage, not the barrage's payload — so a knockback-negating accessory
                                // skips it. Displacement that IS an attack's payload (Gale Push, Wing Gust)
                                // deliberately still lands; negating those would make them ignorable (§0.2).
                                for (Player player : ctx.arena().playersInside()) {
                                    if (plugin.accessoryManager().negatesKnockback(player)) {
                                        continue;
                                    }
                                    Movement.launchTarget(player, nudge);
                                }
                                Fx.sound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.35f, 0.9f);
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                16, onComplete);
    }
}
