package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.Arena;
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
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A shadow crosses the arena floor, and a moment later the thing casting it arrives along exactly
 * that line. The impacts are never marked — only the shadow is — so the information the group needs
 * arrives before the danger does, and only for as long as they are watching the ground.
 * <p>
 * Every other telegraph in the roster marks the place that is about to be dangerous. This one marks
 * a place that is dangerous <em>soon</em>, moving, and asks players to extrapolate. That is a
 * genuinely different read: you are not reacting to a circle appearing under you, you are working out
 * where a line is going and stepping off it early. Players who only react at the last instant get
 * clipped by the run; players tracking the shadow walk out of the whole thing at leisure.
 * <p>
 * Runs cross the arena rather than targeting individuals, so the mechanic never feels personal or
 * unavoidable, and the boss stays hittable throughout — this changes where the fight happens, not
 * whether it happens.
 */
public final class ShadowBombardmentMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color shadowColor;
    private final Color impactColor;
    private final int runIntervalTicks;
    private final int impactDelayTicks;
    private final double shadowSpeed;
    private final double impactRadius;
    private final double damage;
    private final int impactsPerRun;

    private final List<Impact> pending = new ArrayList<>();
    private Location shadowAt;
    private org.bukkit.util.Vector shadowStep;
    private int shadowTicksLeft;
    private int nextImpactIn;
    private int runCountdown;
    private int clean;
    private int clipped;

    private static final class Impact {
        final Location at;
        int fuse;

        Impact(Location at, int fuse) {
            this.at = at;
            this.fuse = fuse;
        }
    }

    public ShadowBombardmentMechanic(BossInstance instance, String label, Color shadowColor, Color impactColor,
                                      int runIntervalTicks, int impactDelayTicks, double shadowSpeed,
                                      double impactRadius, double damage, int impactsPerRun) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.shadowColor = shadowColor;
        this.impactColor = impactColor;
        this.runIntervalTicks = Math.max(60, runIntervalTicks);
        this.impactDelayTicks = Math.max(20, impactDelayTicks);
        this.shadowSpeed = Math.max(0.2, shadowSpeed);
        this.impactRadius = Math.max(1.5, impactRadius);
        this.damage = damage;
        this.impactsPerRun = Math.max(2, impactsPerRun);
    }

    @Override
    protected void onStart() {
        runCountdown = 40;
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Watch the shadow, not the impact", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        pending.clear();
        shadowAt = null;
    }

    @Override
    protected void tick() {
        advanceImpacts();

        if (shadowTicksLeft > 0) {
            advanceShadow();
        } else {
            runCountdown -= TICK_INTERVAL;
            if (runCountdown <= 0) {
                beginRun();
            }
        }
        showBars();
    }

    /** Starts a fresh pass across the arena, entering from one edge and leaving by the other. */
    private void beginRun() {
        runCountdown = runIntervalTicks;
        World world = world();
        if (world == null) {
            return;
        }
        double entryAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        Location start = surfaceSpot(entryAngle, 0.95);
        Location end = surfaceSpot(entryAngle + Math.PI, 0.95);

        shadowAt = start;
        org.bukkit.util.Vector across = end.toVector().subtract(start.toVector());
        across.setY(0);
        double length = across.length();
        if (length < 1.0) {
            shadowAt = null;
            return;
        }
        shadowStep = across.normalize().multiply(shadowSpeed);
        shadowTicksLeft = (int) Math.ceil(length / shadowSpeed);
        // Impacts are spaced evenly along however long the run actually is.
        nextImpactIn = Math.max(1, shadowTicksLeft / impactsPerRun);

        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.7f);
        Fx.sound(start, Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 0.6f);
    }

    private void advanceShadow() {
        shadowTicksLeft -= TICK_INTERVAL;
        if (shadowAt == null || shadowStep == null) {
            shadowTicksLeft = 0;
            return;
        }
        shadowAt = shadowAt.clone().add(shadowStep.clone().multiply(TICK_INTERVAL));
        World world = shadowAt.getWorld();
        if (world != null) {
            shadowAt.setY(world.getHighestBlockYAt(shadowAt.getBlockX(), shadowAt.getBlockZ()) + 1.05);
        }

        // The shadow itself: a dark disc on the floor, and nothing else. No ring where it will hit.
        Fx.coloredRing(shadowAt, shadowColor, 1.6f, impactRadius, 20, elapsedTicks * 0.05);
        Fx.coloredRing(shadowAt, shadowColor, 1.1f, impactRadius * 0.55, 12, -elapsedTicks * 0.08);

        nextImpactIn -= TICK_INTERVAL;
        if (nextImpactIn <= 0) {
            nextImpactIn = Math.max(1, (shadowTicksLeft > 0 ? shadowTicksLeft : 1) / Math.max(1, impactsPerRun - 1));
            pending.add(new Impact(shadowAt.clone(), impactDelayTicks));
        }
        if (shadowTicksLeft <= 0) {
            shadowAt = null;
            shadowStep = null;
        }
    }

    private void advanceImpacts() {
        for (Iterator<Impact> it = pending.iterator(); it.hasNext(); ) {
            Impact impact = it.next();
            impact.fuse -= TICK_INTERVAL;
            if (impact.fuse > 0) {
                if (elapsedTicks % 6 == 0) {
                    Fx.burst(impact.at.clone().add(0, 0.3, 0), Particle.SMOKE, 3, impactRadius * 0.3);
                }
                continue;
            }
            detonate(impact);
            it.remove();
        }
    }

    private void detonate(Impact impact) {
        Fx.coloredBurst(impact.at.clone().add(0, 1, 0), impactColor, 2.4f, 50, impactRadius * 0.4);
        Fx.flash(impact.at.clone().add(0, 1, 0), 1);
        Fx.sound(impact.at, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.8f);

        List<Player> caught = Arena.combatants(impact.at, impactRadius);
        if (caught.isEmpty()) {
            clean++;
            // Reading the shadow and stepping off the line is exactly what this phase wants.
            instance.recordExposure();
            return;
        }
        clipped++;
        for (Player player : caught) {
            hurt(player, damage);
        }
    }

    private void showBars() {
        int total = clean + clipped;
        double cleanRate = total == 0 ? 1.0 : clean / (double) total;
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean underShadow = shadowAt != null
                    && flatDistance(viewer.getLocation(), shadowAt) <= impactRadius;
            boolean overPending = pending.stream()
                    .anyMatch(impact -> flatDistance(viewer.getLocation(), impact.at) <= impactRadius);
            Component text = Component.text(label + "  ", NamedTextColor.DARK_RED)
                    .append(underShadow
                            ? Component.text("THE SHADOW IS ON YOU", NamedTextColor.RED)
                            : overPending
                                    ? Component.text("standing on its line", NamedTextColor.GOLD)
                                    : Component.text("off the line", NamedTextColor.GREEN))
                    .append(Component.text("   clean " + clean + " / " + total, NamedTextColor.DARK_GRAY));
            return MechanicBar.Readout.of(text, cleanRate,
                    underShadow || overPending ? BossBar.Color.RED : BossBar.Color.BLUE);
        });
    }
}
