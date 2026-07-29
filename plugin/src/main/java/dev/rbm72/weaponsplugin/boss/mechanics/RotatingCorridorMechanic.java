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
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The breath never stops. It sweeps the entire arena except for one wedge of clear air, and that
 * wedge is turning — so surviving means orbiting the boss at the same rate the corridor does, for as
 * long as the phase lasts.
 * <p>
 * This is the roster's one mechanic where the safe place is defined by <em>angle</em> rather than
 * distance or a marked spot, and that changes how it feels to play completely: the group is not
 * running to somewhere, it is circling, continuously, and anyone who stops to line up a heavy attack
 * gets left behind by the rotation. It pairs deliberately badly with slow, committed abilities, which
 * is the point — it asks for a different rhythm than the rest of the fight.
 * <p>
 * The corridor is drawn as two hard edges rather than a filled shape, both to stay inside the
 * particle budget and because the edges are the information: you only ever need to know which way to
 * move and how much room you have left.
 */
public final class RotatingCorridorMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color safeColor;
    private final Color burnColor;
    private final double halfWidthDegrees;
    private final double degreesPerSecond;
    private final double innerRadius;
    private final double damagePerSecond;
    private final int reverseIntervalTicks;

    private double corridorAngle;
    private int direction = 1;
    private int reverseCountdown;
    private int ticksSinceExposureCredit;

    public RotatingCorridorMechanic(BossInstance instance, String label, Color safeColor, Color burnColor,
                                     double halfWidthDegrees, double degreesPerSecond, double innerRadius,
                                     double damagePerSecond, int reverseIntervalTicks) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.safeColor = safeColor;
        this.burnColor = burnColor;
        this.halfWidthDegrees = Math.max(8.0, halfWidthDegrees);
        this.degreesPerSecond = degreesPerSecond;
        this.innerRadius = Math.max(0.0, innerRadius);
        this.damagePerSecond = damagePerSecond;
        this.reverseIntervalTicks = reverseIntervalTicks;
    }

    @Override
    protected void onStart() {
        corridorAngle = Math.toDegrees(java.util.concurrent.ThreadLocalRandom.current().nextDouble(0, Math.PI * 2));
        reverseCountdown = reverseIntervalTicks;
        instance.showTitle(
                Component.text(label, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("One lane of clear air — stay inside it", NamedTextColor.GRAY));
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.3f, 0.7f);
    }

    @Override
    protected void tick() {
        corridorAngle = wrap(corridorAngle + direction * degreesPerSecond * (TICK_INTERVAL / 20.0));

        if (reverseIntervalTicks > 0) {
            reverseCountdown -= TICK_INTERVAL;
            if (reverseCountdown <= 0) {
                reverseCountdown = reverseIntervalTicks;
                direction = -direction;
                Fx.sound(instance.entity().getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 0.8f);
                instance.showTitle(
                        Component.text("IT TURNS BACK", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                        Component.text("The lane is going the other way", NamedTextColor.GRAY));
            }
        }

        draw();

        boolean anyoneSafe = false;
        for (Player player : combatants()) {
            if (inCorridor(player)) {
                anyoneSafe = true;
                continue;
            }
            if (elapsedTicks % 20 == 0) {
                tickHurt(player, damagePerSecond);
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), burnColor, 1.4f, 14, 0.4);
                Fx.sound(player.getLocation(), Sound.ENTITY_GENERIC_BURN, 0.8f, 0.9f);
            }
        }

        ticksSinceExposureCredit += TICK_INTERVAL;
        if (anyoneSafe && ticksSinceExposureCredit >= 20) {
            ticksSinceExposureCredit = 0;
            instance.recordExposure();
        }
        showBars();
    }

    /** Inside the safe wedge, or close enough to the eye of it that angle stops meaning anything. */
    private boolean inCorridor(Player player) {
        Location centre = instance.entity().getLocation();
        double dist = flatDistance(player.getLocation(), centre);
        if (dist <= innerRadius) {
            return true;
        }
        return Math.abs(angleDelta(bearing(centre, player.getLocation()), corridorAngle)) <= halfWidthDegrees;
    }

    private void draw() {
        Location centre = instance.entity().getLocation();
        double maxRadius = instance.arena().radius();
        for (int side = -1; side <= 1; side += 2) {
            double edge = Math.toRadians(corridorAngle + side * halfWidthDegrees);
            Location from = centre.clone().add(Math.cos(edge) * Math.max(1.0, innerRadius), 0.4,
                    Math.sin(edge) * Math.max(1.0, innerRadius));
            Location to = centre.clone().add(Math.cos(edge) * maxRadius, 0.4, Math.sin(edge) * maxRadius);
            Fx.line(from, to, org.bukkit.Particle.END_ROD, 18);
        }
        if (elapsedTicks % 6 == 0) {
            double mid = Math.toRadians(corridorAngle);
            Location marker = centre.clone().add(Math.cos(mid) * maxRadius * 0.6, 0.5,
                    Math.sin(mid) * maxRadius * 0.6);
            Fx.coloredBurst(marker, safeColor, 1.4f, 10, 0.4);
        }
    }

    private void showBars() {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean safe = inCorridor(viewer);
            double offset = Math.abs(angleDelta(
                    bearing(instance.entity().getLocation(), viewer.getLocation()), corridorAngle));
            double closeness = Math.max(0.0, 1.0 - offset / 180.0);
            // No left/right hint on purpose: which way "left" is depends on the player's facing, not
            // on the geometry, so it would be wrong about half the time. The drawn lane edges and the
            // marker down its centre are the directional cue.
            Component text = Component.text(label + "  ", NamedTextColor.LIGHT_PURPLE)
                    .append(safe
                            ? Component.text("in the lane", NamedTextColor.GREEN)
                            : Component.text("IN THE BREATH — get to the marked lane", NamedTextColor.RED));
            return MechanicBar.Readout.of(text, closeness, safe ? BossBar.Color.GREEN : BossBar.Color.RED);
        });
    }

    private static double bearing(Location from, Location to) {
        return Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
    }

    /** Signed smallest difference between two bearings, in [-180, 180). */
    private static double angleDelta(double a, double b) {
        return (a - b + 540) % 360 - 180;
    }

    private static double wrap(double degrees) {
        double wrapped = degrees % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped;
    }
}
