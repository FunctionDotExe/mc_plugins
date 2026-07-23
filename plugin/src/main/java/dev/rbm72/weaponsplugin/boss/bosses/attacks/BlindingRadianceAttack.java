package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The Solar Colossus's signature: it doesn't hide behind wards, it turns the arena itself into
 * cover. It plants itself, raises real stone pillars around the ring, and sweeps a blinding beam
 * around the arena — duck behind a pillar as it swings past or take the full hit. Reforms the
 * pillars every sweep, so the same "objects you can use and lose" cover keeps shifting. Survive
 * the whole sweep clean and it's left staggered wide open. An environment-and-timing check, not
 * another totem ring.
 */
public final class BlindingRadianceAttack extends BossAttack {

    private static final Color RADIANT = Color.fromRGB(255, 250, 220);

    private final int telegraphTicks;
    private final int pillarCount;
    private final int pillarHeight;
    private final int sweepDurationTicks;
    private final double beamDamagePerTick;
    private final double beamWidthDegrees;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public BlindingRadianceAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.telegraphTicks = configInt("blinding-radiance-telegraph-ticks", 30);
        this.pillarCount = configInt("blinding-radiance-pillar-count", 6);
        this.pillarHeight = configInt("blinding-radiance-pillar-height", 3);
        this.sweepDurationTicks = configInt("blinding-radiance-sweep-ticks", 100);
        this.beamDamagePerTick = configDouble("blinding-radiance-beam-damage-per-tick", 1.2);
        this.beamWidthDegrees = configDouble("blinding-radiance-beam-width-degrees", 26.0);
        this.exposedStaggerTicks = configInt("blinding-radiance-stagger-ticks", 60);
        this.exposedMultiplier = configDouble("blinding-radiance-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("blinding-radiance-exposed-ticks", 100);
    }

    @Override
    public String name() {
        return "Blinding Radiance";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("blinding-radiance-cooldown-seconds", 50.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), RADIANT, 1.4f, 3.5, 24, 0);
                    Fx.sound(ctx.bossLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.7f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    Location bossLoc = ctx.bossLocation();
                    double radius = ctx.arena().radius();

                    List<Location> pillarSpots = new ArrayList<>(pillarCount);
                    for (int i = 0; i < pillarCount; i++) {
                        double angle = 2 * Math.PI * i / pillarCount;
                        pillarSpots.add(bossLoc.clone().add(Math.cos(angle) * radius * 0.5, 0, Math.sin(angle) * radius * 0.5));
                    }
                    for (Location spot : pillarSpots) {
                        Grief.raiseColumns(ctx, spot, Material.SMOOTH_QUARTZ, pillarHeight, 1, 0.1, sweepDurationTicks + 20);
                    }

                    ctx.instance().showTitle(
                            Component.text("BLINDING RADIANCE", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                            Component.text("Shelter behind the pillars as the beam sweeps", NamedTextColor.GRAY));
                    BossAudio.play(bossLoc, "boss.blinding_radiance", Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f);

                    new BukkitRunnable() {
                        int ticks = 0;
                        double angle = 0;
                        int hits = 0;

                        @Override
                        public void run() {
                            if (ticks >= sweepDurationTicks || !ctx.boss().isValid()) {
                                resolve(hits);
                                cancel();
                                return;
                            }
                            angle += Math.PI / 60;
                            Vector beamDir = new Vector(Math.cos(angle), 0, Math.sin(angle));
                            for (double d = 2; d <= radius; d += 1.5) {
                                Location point = bossLoc.clone().add(beamDir.clone().multiply(d));
                                point.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, point.add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0);
                            }
                            for (Player player : Arena.playersNear(bossLoc, radius)) {
                                Vector toPlayer = player.getLocation().toVector().subtract(bossLoc.toVector());
                                if (toPlayer.lengthSquared() < 1.0E-6) {
                                    continue;
                                }
                                double playerAngle = Math.atan2(toPlayer.getZ(), toPlayer.getX());
                                double diff = Math.abs(normalizeAngle(playerAngle - angle));
                                if (diff <= Math.toRadians(beamWidthDegrees / 2) && !hasCoverBetween(bossLoc, player.getLocation())) {
                                    player.damage(beamDamagePerTick, ctx.boss());
                                    hits++;
                                }
                            }
                            ticks++;
                        }

                        private void resolve(int totalHits) {
                            ctx.instance().setForcedInvulnerable(false);
                            Location loc = ctx.bossLocation();
                            if (totalHits == 0) {
                                ctx.instance().recordExposure();
                                ctx.instance().setDamageMultiplier(exposedMultiplier);
                                ctx.instance().stagger(exposedStaggerTicks);
                                ctx.instance().entity().setGlowing(true);
                                Fx.coloredBurst(loc.clone().add(0, 1.2, 0), RADIANT, 2.2f, 50, 0.8);
                                Fx.flash(loc.clone().add(0, 1.2, 0), 2);
                                Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
                                ctx.instance().showTitle(
                                        Component.text("OVERLOADED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                                        Component.text("The light burns back inward", NamedTextColor.GRAY));
                                ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                                    if (ctx.boss().isValid()) {
                                        ctx.instance().entity().setGlowing(false);
                                        ctx.instance().setDamageMultiplier(1.0);
                                    }
                                }, exposedTicks);
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                sweepDurationTicks + 20, onComplete);
    }

    private static double normalizeAngle(double angle) {
        double a = angle % (2 * Math.PI);
        if (a > Math.PI) {
            a -= 2 * Math.PI;
        } else if (a < -Math.PI) {
            a += 2 * Math.PI;
        }
        return a;
    }

    /** Cheap line-of-sight approximation: is there a solid, non-air block roughly between the two points? */
    private boolean hasCoverBetween(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 1.0E-6) {
            return false;
        }
        direction.multiply(1.0 / distance);
        Location cursor = from.clone().add(0, 1, 0);
        for (double d = 1; d < distance; d += 1.0) {
            cursor.add(direction);
            Block block = cursor.getBlock();
            if (!block.getType().isAir() && block.getType().isSolid()) {
                return true;
            }
        }
        return false;
    }
}
