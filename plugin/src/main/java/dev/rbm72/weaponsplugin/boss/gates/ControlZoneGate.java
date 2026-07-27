package dev.rbm72.weaponsplugin.boss.gates;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Footwork gate: a marked circle drifts continuously around the arena, and the boss's ward is only
 * down while at least one player is standing inside it. Nobody in the circle, nobody does damage — no
 * matter how hard they hit.
 * <p>
 * Standing in it is not free. The zone burns whoever holds it on an interval, so a group cannot park
 * one person there and forget about it; holders have to rotate, which means the whole group is moving
 * with the circle for the entire phase while the boss keeps swinging. Nothing to break, nothing to
 * kill, no timing window — it is purely a test of whether the group can keep repositioning together
 * under pressure, which no other gate asks for.
 * <p>
 * Deliberately never fully seals: the zone is always somewhere reachable, so this gate cannot stall a
 * fight the way a broken prop can. If it is not open, it is because nobody walked into it.
 */
public final class ControlZoneGate implements PhaseMechanic {

    private static final long NOTICE_MS = 1200;
    /** Zone geometry refresh rate. Fast enough that the ring reads as sliding, not teleporting. */
    private static final long TICK_INTERVAL = 2L;

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Component label;
    private final Color color;
    private final double zoneRadius;
    private final double orbitFraction;
    private final double degreesPerSecond;
    private final int initialDelayTicks;
    private final double heldMultiplier;
    private final int holdBurnIntervalTicks;
    private final double holdBurnDamage;

    private BukkitTask pendingTask;
    private BukkitTask zoneTask;
    private double angleRadians;
    private int ticks;
    private boolean everHeld;
    private boolean open;
    private boolean stopped;

    public ControlZoneGate(BossInstance instance, Component label, Color color, double zoneRadius,
                            double orbitFraction, double degreesPerSecond, int initialDelayTicks,
                            double heldMultiplier, int holdBurnIntervalTicks, double holdBurnDamage) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.label = label;
        this.color = color;
        this.zoneRadius = zoneRadius;
        this.orbitFraction = orbitFraction;
        this.degreesPerSecond = degreesPerSecond;
        this.initialDelayTicks = initialDelayTicks;
        this.heldMultiplier = heldMultiplier;
        this.holdBurnIntervalTicks = holdBurnIntervalTicks;
        this.holdBurnDamage = holdBurnDamage;
    }

    @Override
    public void start() {
        instance.setDamageMultiplier(1.0);
        pendingTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingTask = null;
            begin();
        }, Math.max(1, initialDelayTicks));
        instance.trackTask(pendingTask);
    }

    @Override
    public void stop() {
        stopped = true;
        if (pendingTask != null && !pendingTask.isCancelled()) {
            pendingTask.cancel();
        }
        pendingTask = null;
        if (zoneTask != null && !zoneTask.isCancelled()) {
            zoneTask.cancel();
        }
        zoneTask = null;
        instance.setDamageMultiplier(1.0);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
    }

    private void begin() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        instance.setDamageMultiplier(0.0);
        instance.showTitle(
                label.color(NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("Hold the moving ground to drop its ward", NamedTextColor.GRAY));

        zoneTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse,
                TICK_INTERVAL, TICK_INTERVAL);
        instance.trackTask(zoneTask);
    }

    private void pulse() {
        if (stopped || !instance.entity().isValid()) {
            stop();
            return;
        }
        ticks += TICK_INTERVAL;
        angleRadians += Math.toRadians(degreesPerSecond) * (TICK_INTERVAL / 20.0);

        Location zone = zoneCenter();
        drawZone(zone);

        List<Player> holders = Arena.combatants(zone, zoneRadius);
        boolean held = !holders.isEmpty();
        if (held != open) {
            open = held;
            onOpenChanged(zone, holders);
        }
        instance.setDamageMultiplier(held ? heldMultiplier : 0.0);

        if (held) {
            if (!everHeld) {
                everHeld = true;
                // Satisfies the phase floor the first time the group actually works the mechanic, so
                // the fight can progress past this band exactly once they have shown they can hold it.
                instance.recordExposure();
            }
            if (holdBurnIntervalTicks > 0 && ticks % holdBurnIntervalTicks < TICK_INTERVAL) {
                for (Player holder : holders) {
                    holder.damage(holdBurnDamage, instance.entity());
                    Fx.coloredBurst(holder.getLocation().add(0, 1, 0), color, 1.0f, 12, 0.3);
                }
            }
            for (Player holder : holders) {
                plugin.actionBarHub().flash(holder,
                        Component.text("Holding the ground — its ward is down", NamedTextColor.GREEN),
                        NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
            }
        }
    }

    private void onOpenChanged(Location zone, List<Player> holders) {
        Location bossLoc = instance.entity().getLocation();
        instance.entity().setGlowing(open);
        if (open) {
            Fx.coloredBurst(bossLoc.clone().add(0, 1.2, 0), color, 1.8f, 40, 0.7);
            Fx.sound(bossLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.3f);
        } else {
            Fx.sound(bossLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.7f);
            for (Player player : Arena.combatants(bossLoc, instance.arena().radius())) {
                plugin.actionBarHub().flash(player,
                        Component.text("Its ward closes — get back on the ground", NamedTextColor.RED),
                        NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
            }
        }
    }

    /**
     * Orbits the arena's fixed spawn center rather than the boss. Anchoring to the boss would let it
     * drag the zone around with it and park the answer permanently under everyone's feet, which is the
     * one thing this gate must never do.
     */
    private Location zoneCenter() {
        Location center = instance.arena().center();
        double orbit = instance.arena().radius() * orbitFraction;
        Location spot = center.clone().add(Math.cos(angleRadians) * orbit, 0, Math.sin(angleRadians) * orbit);
        World world = spot.getWorld();
        if (world != null) {
            spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        }
        return spot;
    }

    private void drawZone(Location zone) {
        Fx.coloredRing(zone, color, 1.3f, zoneRadius, 28, 0);
        if (ticks % 10 < TICK_INTERVAL) {
            Fx.coloredBurst(zone.clone().add(0, 0.4, 0), color, 1.0f, 10, zoneRadius * 0.4);
        }
    }
}
