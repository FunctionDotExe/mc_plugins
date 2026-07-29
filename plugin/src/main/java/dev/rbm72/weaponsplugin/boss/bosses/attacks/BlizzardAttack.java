package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.TickDamage;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A rotating wall of freezing wind sweeps around the queen — a spinning-laser hazard, not a flat
 * arena-wide tick. Previously this hit every player in the arena every second no matter where they
 * stood, which was a pure DPS race with no positioning decision at all. Now only the narrow sweeping
 * sector deals damage; reading its rotation and staying out of its path avoids it entirely.
 * <p>
 * The sector damage is a tick, not a hit, so it goes through {@link TickDamage}. It used to be a plain
 * {@code player.damage} on a 1-tick timer: vanilla i-frames throttled the health loss to roughly twice
 * a second, but every landed hit still rolled the camera and re-applied knockback for the whole
 * 140-tick sweep, which is the "my screen is tilting and my movement keeps getting eaten" report. The
 * explicit per-player cooldown below reproduces the old effective rate without any of that.
 */
public final class BlizzardAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(210, 240, 255);

    private final double damagePerHit;
    private final int damageIntervalTicks;
    private final int slowAmplifier;
    private final double sectorWidthDegrees;
    private final double revolutions;
    private final int durationTicks;
    private final int telegraphTicks;

    public BlizzardAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.damagePerHit = configDouble("blizzard-damage-per-hit", 3.0);
        this.damageIntervalTicks = configInt("blizzard-damage-interval-ticks", 10);
        this.slowAmplifier = configInt("blizzard-slow-amplifier", 1);
        this.sectorWidthDegrees = configDouble("blizzard-sector-width-degrees", 40.0);
        this.revolutions = configDouble("blizzard-revolutions", 2.5);
        this.durationTicks = configInt("blizzard-duration-ticks", 140);
        this.telegraphTicks = configInt("blizzard-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Blizzard";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("blizzard-cooldown-seconds", 20.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        double arenaRadius = ctx.arena().radius();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(ctx.arena().center(), arenaRadius * 0.9, Particle.SNOWFLAKE);
                    Fx.coloredRing(origin, FROST, 1.4f, 4.0, 38, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.frost_queen.blizzard", Sound.ENTITY_PLAYER_HURT_FREEZE, 1.3f, 0.5f);
                    Fx.sound(origin, Sound.WEATHER_RAIN, 1.3f, 0.6f);
                    Location center = ctx.arena().center();
                    double sweepPerTick = (revolutions * 2 * Math.PI) / durationTicks;
                    // Per-cast, per-player hit cooldown. The sector check still runs every tick, so the
                    // sweep clipping you for six ticks lands exactly one hit rather than six; the cooldown
                    // is what the vanilla i-frames used to be doing implicitly, made explicit because
                    // TickDamage does not set them.
                    Map<UUID, Integer> lastHitTick = new HashMap<>();
                    new BukkitRunnable() {
                        int ticks = 0;
                        double angle = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            // Drifting ambient snow across the whole footprint (cosmetic only, no damage tie-in).
                            center.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 3, 0),
                                    60, arenaRadius * 1.1, 6.4, arenaRadius * 1.1, 0.02);

                            // The actual hazard: a bright sweeping sector rendered as a fan of points from the
                            // boss out to the arena edge, rotating one full sweepPerTick each tick.
                            Vector direction = new Vector(Math.cos(angle), 0, Math.sin(angle));
                            for (double d = 2.0; d <= arenaRadius; d += 1.5) {
                                Location point = center.clone().add(direction.clone().multiply(d));
                                Fx.point(point, Particle.SNOWFLAKE, 3);
                                Fx.coloredBurst(point, FROST, 0.9f, 2, 0.1);
                            }
                            // A couple of real (cosmetic, self-clearing) ice-wall props riding the sweep line.
                            if (ticks % 6 == 0) {
                                for (double d = 4.0; d <= arenaRadius; d += 6.0) {
                                    Location wallPoint = center.clone().add(direction.clone().multiply(d));
                                    Fx.glowPillar(plugin, wallPoint, Material.PACKED_ICE, 1.0f, 2.2f, 10);
                                }
                            }
                            for (Player player : ctx.arena().playersInside()) {
                                Vector toPlayer = player.getLocation().toVector().subtract(center.toVector()).setY(0);
                                if (toPlayer.lengthSquared() < 1.0E-6) {
                                    continue;
                                }
                                double playerAngle = Math.atan2(toPlayer.getZ(), toPlayer.getX());
                                double diff = Math.abs(normalizeAngle(playerAngle - angle));
                                if (Math.toDegrees(diff) > sectorWidthDegrees / 2.0) {
                                    continue;
                                }
                                Integer last = lastHitTick.get(player.getUniqueId());
                                if (last != null && ticks - last < damageIntervalTicks) {
                                    continue;
                                }
                                lastHitTick.put(player.getUniqueId(), ticks);
                                TickDamage.apply(ctx.instance(), player, damagePerHit);
                                player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.SLOWNESS, damageIntervalTicks + 10, slowAmplifier));
                                Fx.coloredBurst(player.getLocation().add(0, 1, 0), FROST, 1.4f, 10, 0.3);
                                Fx.sound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.3f);
                            }
                            angle += sweepPerTick;
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }

    private static double normalizeAngle(double radians) {
        double twoPi = 2 * Math.PI;
        double result = radians % twoPi;
        if (result > Math.PI) {
            result -= twoPi;
        } else if (result < -Math.PI) {
            result += twoPi;
        }
        return result;
    }
}
