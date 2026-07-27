package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Several cones sweep out from the boss on a stagger, and where two of them overlap the damage does
 * not add — it multiplies. Standing in one is a mistake you walk off; standing in the seam where two
 * meet is what kills people.
 * <p>
 * The reason for the overlap rule is that it punishes the group's <em>shape</em> rather than any one
 * player's positioning. A clumped group occupies a small enough wedge that a single seam catches all
 * of them at once, while a spread group can only ever lose one or two members to the same seam. So
 * the mechanic quietly enforces spacing without ever printing "spread out", and it does it while
 * everyone is still free to fight.
 * <p>
 * Cones telegraph as visible edges before they fire, and they always leave gaps — there is never a
 * moment where the whole arena is covered, so a player who reads the fan always has somewhere to be.
 */
public final class HowlConeMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color color;
    private final int coneCount;
    private final double halfWidthDegrees;
    private final int howlIntervalTicks;
    private final int telegraphTicks;
    private final double range;
    private final double damage;
    private final double overlapMultiplier;

    private final List<Double> bearings = new ArrayList<>();
    private int howlCountdown;
    private int telegraphLeft;

    public HowlConeMechanic(BossInstance instance, String label, Color color, int coneCount,
                             double halfWidthDegrees, int howlIntervalTicks, int telegraphTicks,
                             double range, double damage, double overlapMultiplier) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.coneCount = Math.max(2, coneCount);
        this.halfWidthDegrees = Math.max(10.0, halfWidthDegrees);
        this.howlIntervalTicks = Math.max(60, howlIntervalTicks);
        this.telegraphTicks = Math.max(20, telegraphTicks);
        this.range = range;
        this.damage = damage;
        this.overlapMultiplier = Math.max(1.0, overlapMultiplier);
    }

    @Override
    protected void onStart() {
        howlCountdown = howlIntervalTicks;
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("Where two howls meet, nothing survives — do not bunch up", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        bearings.clear();
    }

    @Override
    protected void tick() {
        if (telegraphLeft > 0) {
            telegraphLeft -= TICK_INTERVAL;
            drawCones(true);
            if (telegraphLeft <= 0) {
                resolve();
            }
            showBars();
            return;
        }

        howlCountdown -= TICK_INTERVAL;
        if (howlCountdown <= 0) {
            wind();
        }
        showBars();
    }

    private void wind() {
        howlCountdown = howlIntervalTicks;
        telegraphLeft = telegraphTicks;
        bearings.clear();
        // Evenly spaced with a random offset, so the gaps are always the same size but never in the
        // same place twice — readable, but not memorisable.
        double offset = ThreadLocalRandom.current().nextDouble(0, 360);
        double spacing = 360.0 / coneCount;
        for (int i = 0; i < coneCount; i++) {
            // A deliberate jitter under half the spacing is what creates the overlap seams.
            double jitter = ThreadLocalRandom.current().nextDouble(-spacing * 0.28, spacing * 0.28);
            bearings.add(offset + i * spacing + jitter);
        }
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_WOLF_GROWL, 1.4f, 0.5f);
    }

    private void drawCones(boolean telegraph) {
        Location centre = instance.entity().getLocation();
        float size = telegraph ? 1.0f + (float) (1.0 - telegraphLeft / (double) telegraphTicks) : 2.0f;
        for (double bearing : bearings) {
            for (int side = -1; side <= 1; side += 2) {
                double edge = Math.toRadians(bearing + side * halfWidthDegrees);
                Location from = centre.clone().add(0, 0.5, 0);
                Location to = centre.clone().add(Math.cos(edge) * range, 0.5, Math.sin(edge) * range);
                Fx.line(from, to, Particle.SOUL_FIRE_FLAME, 14);
            }
            double mid = Math.toRadians(bearing);
            Fx.coloredBurst(centre.clone().add(Math.cos(mid) * range * 0.6, 0.6, Math.sin(mid) * range * 0.6),
                    color, size, 8, 0.4);
        }
    }

    private void resolve() {
        Location centre = instance.entity().getLocation();
        Fx.sound(centre, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.6f);
        drawCones(false);

        boolean anyoneClean = false;
        for (Player player : combatants()) {
            int cones = conesCovering(player, centre);
            if (cones == 0) {
                anyoneClean = true;
                continue;
            }
            double total = damage * cones * (cones > 1 ? overlapMultiplier : 1.0);
            hurt(player, total);
            Fx.coloredBurst(player.getLocation().add(0, 1.2, 0), color,
                    cones > 1 ? 2.4f : 1.4f, cones > 1 ? 36 : 18, 0.5);
            if (cones > 1) {
                Fx.sound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.1f);
            }
        }
        if (anyoneClean) {
            // Somebody found the gap — that is the mechanic being played correctly.
            instance.recordExposure();
        }
        bearings.clear();
    }

    private int conesCovering(Player player, Location centre) {
        double dist = flatDistance(player.getLocation(), centre);
        if (dist > range) {
            return 0;
        }
        double bearing = Math.toDegrees(Math.atan2(
                player.getLocation().getZ() - centre.getZ(),
                player.getLocation().getX() - centre.getX()));
        int count = 0;
        for (double coneBearing : bearings) {
            double delta = Math.abs((bearing - coneBearing + 540) % 360 - 180);
            if (delta <= halfWidthDegrees) {
                count++;
            }
        }
        return count;
    }

    private void showBars() {
        Location centre = instance.entity().getLocation();
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            int cones = telegraphLeft > 0 ? conesCovering(viewer, centre) : 0;
            double charge = telegraphLeft > 0
                    ? 1.0 - telegraphLeft / (double) telegraphTicks
                    : 1.0 - Math.max(0.0, howlCountdown / (double) howlIntervalTicks);
            Component text = Component.text(label + "  ", NamedTextColor.DARK_AQUA)
                    .append(cones > 1
                            ? Component.text("IN A SEAM — MOVE NOW", NamedTextColor.RED)
                            : cones == 1
                                    ? Component.text("in a howl", NamedTextColor.GOLD)
                                    : Component.text(telegraphLeft > 0 ? "in a gap" : "winding up",
                                            NamedTextColor.GREEN));
            return MechanicBar.Readout.of(text, charge,
                    cones > 1 ? BossBar.Color.RED : cones == 1 ? BossBar.Color.YELLOW : BossBar.Color.BLUE);
        });
    }
}
