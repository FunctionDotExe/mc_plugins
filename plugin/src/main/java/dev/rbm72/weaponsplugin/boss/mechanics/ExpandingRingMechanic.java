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

import java.util.concurrent.ThreadLocalRandom;

/**
 * A wall of fire leaves the boss and travels outward, and the only way through it is one of a handful
 * of gaps torn in the ring. The gaps rotate as the wave travels, and every wave has fewer of them
 * than the last.
 * <p>
 * This is a timing-and-lane mechanic rather than a "stand somewhere" one: the danger is a moving
 * line, so what matters is where you will be when it reaches you, not where you are now. That makes
 * it read completely differently from a floor hazard even though both are about position — players
 * have to plan a step ahead and commit, and being wrong is obvious the instant it happens.
 * <p>
 * The gap count decays toward a floor rather than to zero, so there is always a way through and the
 * phase escalates without ever becoming unsolvable. The boss is fully hittable the entire time, so
 * every wave is a decision about whether to give up melee range to make the gap comfortably.
 */
public final class ExpandingRingMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    /**
     * Points around the wall. Kept low deliberately: each one is a dust burst, and at 56 points this
     * was drawing well past {@code maxParticlesPerTick} every other tick on its own, before any of the
     * boss's actual attacks had drawn anything.
     */
    private static final int RING_POINTS = 40;

    private final String label;
    private final Color color;
    private final int waveIntervalTicks;
    private final double waveSpeed;
    private final double bandThickness;
    private final int startingGaps;
    private final int minimumGaps;
    private final double gapHalfWidthDegrees;
    private final double gapDriftDegreesPerSecond;
    private final double damage;
    private final int burnTicks;

    private Location origin;
    private double radius;
    private boolean waveActive;
    private int gaps;
    private double gapPhase;
    private int waveCountdown;
    private int wavesSurvived;
    private int wavesTaken;
    /** Everyone the current wave has already resolved against, so one wave can only hit each player once. */
    private final java.util.Set<java.util.UUID> struckThisWave = new java.util.HashSet<>();

    public ExpandingRingMechanic(BossInstance instance, String label, Color color, int waveIntervalTicks,
                                  double waveSpeed, double bandThickness, int startingGaps, int minimumGaps,
                                  double gapHalfWidthDegrees, double gapDriftDegreesPerSecond,
                                  double damage, int burnTicks) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.waveIntervalTicks = Math.max(40, waveIntervalTicks);
        this.waveSpeed = Math.max(0.15, waveSpeed);
        this.bandThickness = Math.max(1.0, bandThickness);
        this.startingGaps = Math.max(1, startingGaps);
        this.minimumGaps = Math.max(1, Math.min(minimumGaps, startingGaps));
        this.gapHalfWidthDegrees = Math.max(6.0, gapHalfWidthDegrees);
        this.gapDriftDegreesPerSecond = gapDriftDegreesPerSecond;
        this.damage = damage;
        this.burnTicks = burnTicks;
    }

    @Override
    protected void onStart() {
        gaps = startingGaps;
        waveCountdown = 40;
        instance.showTitle(
                Component.text(label, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Find a gap before the wall reaches you", NamedTextColor.GRAY));
        Fx.sound(instance.entity().getLocation(), Sound.BLOCK_LAVA_AMBIENT, 1.4f, 0.6f);
    }

    @Override
    protected void onStop() {
        waveActive = false;
        struckThisWave.clear();
    }

    @Override
    protected void tick() {
        if (!waveActive) {
            waveCountdown -= TICK_INTERVAL;
            if (waveCountdown <= 0) {
                launch();
            }
            showBars();
            return;
        }

        gapPhase += gapDriftDegreesPerSecond * (TICK_INTERVAL / 20.0);
        radius += waveSpeed * TICK_INTERVAL;
        draw();
        resolveBand();

        if (radius > instance.arena().radius() + 2.0) {
            endWave();
        }
        showBars();
    }

    private void launch() {
        origin = instance.entity().getLocation().clone();
        radius = 1.0;
        waveActive = true;
        struckThisWave.clear();
        gapPhase = ThreadLocalRandom.current().nextDouble(0, 360);
        // Each wave is a little meaner than the last, down to a floor that always leaves a way out.
        gaps = Math.max(minimumGaps, gaps - (wavesSurvived + wavesTaken > 0 ? 1 : 0));

        Fx.coloredBurst(origin.clone().add(0, 1.0, 0), color, 2.4f, 50, 0.8);
        Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
    }

    private void endWave() {
        waveActive = false;
        waveCountdown = waveIntervalTicks;
        if (struckThisWave.isEmpty()) {
            wavesSurvived++;
            // A clean wave is the group doing exactly what the phase asks.
            instance.recordExposure();
        } else {
            wavesTaken++;
        }
        struckThisWave.clear();
    }

    /** Draws the wall as a dotted arc, skipping the gaps — the gaps are the whole message. */
    private void draw() {
        if (origin == null) {
            return;
        }
        org.bukkit.World world = origin.getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < RING_POINTS; i++) {
            double bearing = 360.0 * i / RING_POINTS;
            if (inGap(bearing)) {
                continue;
            }
            double radians = Math.toRadians(bearing);
            double x = origin.getX() + Math.cos(radians) * radius;
            double z = origin.getZ() + Math.sin(radians) * radius;
            double y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1.0;
            Fx.coloredBurst(new Location(world, x, y, z), color, 1.5f, 1, 0.12);
        }
        if (elapsedTicks % 6 == 0) {
            for (int gap = 0; gap < gaps; gap++) {
                double bearing = gapCentre(gap);
                double radians = Math.toRadians(bearing);
                Location marker = origin.clone().add(Math.cos(radians) * radius, 0.6, Math.sin(radians) * radius);
                Fx.burst(marker, Particle.END_ROD, 4, 0.3);
            }
        }
    }

    private void resolveBand() {
        for (Player player : combatants()) {
            if (struckThisWave.contains(player.getUniqueId())) {
                continue;
            }
            double dist = flatDistance(player.getLocation(), origin);
            if (Math.abs(dist - radius) > bandThickness / 2.0) {
                continue;
            }
            double bearing = Math.toDegrees(Math.atan2(
                    player.getLocation().getZ() - origin.getZ(),
                    player.getLocation().getX() - origin.getX()));
            if (inGap(bearing)) {
                continue;
            }
            struckThisWave.add(player.getUniqueId());
            hurt(player, damage);
            if (player.isValid() && !player.isDead() && burnTicks > 0) {
                player.setFireTicks(Math.max(player.getFireTicks(), burnTicks));
            }
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 2.0f, 30, 0.5);
            Fx.sound(player.getLocation(), Sound.ENTITY_GENERIC_BURN, 1.1f, 0.9f);
        }
    }

    private double gapCentre(int index) {
        return gapPhase + 360.0 * index / gaps;
    }

    private boolean inGap(double bearing) {
        for (int gap = 0; gap < gaps; gap++) {
            double delta = Math.abs((bearing - gapCentre(gap) + 540) % 360 - 180);
            if (delta <= gapHalfWidthDegrees) {
                return true;
            }
        }
        return false;
    }

    private void showBars() {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            if (!waveActive) {
                Component idle = Component.text(label + "  ", NamedTextColor.GOLD)
                        .append(Component.text("next wave in " + Math.max(0, waveCountdown / 20) + "s",
                                NamedTextColor.GRAY))
                        .append(Component.text("   " + gaps + " gap(s)", NamedTextColor.DARK_GRAY));
                return MechanicBar.Readout.of(idle, 0.0, BossBar.Color.WHITE);
            }
            double bearing = Math.toDegrees(Math.atan2(
                    viewer.getLocation().getZ() - origin.getZ(),
                    viewer.getLocation().getX() - origin.getX()));
            boolean lined = inGap(bearing);
            double dist = flatDistance(viewer.getLocation(), origin);
            double approach = Math.max(0.0, Math.min(1.0, radius / Math.max(1.0, dist)));
            Component text = Component.text(label + "  ", NamedTextColor.GOLD)
                    .append(lined
                            ? Component.text("lined up with a gap", NamedTextColor.GREEN)
                            : Component.text("NOT IN A GAP — move sideways", NamedTextColor.RED))
                    .append(Component.text("   " + gaps + " gap(s)", NamedTextColor.DARK_GRAY));
            return MechanicBar.Readout.of(text, approach, lined ? BossBar.Color.GREEN : BossBar.Color.RED);
        });
    }
}
