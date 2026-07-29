package dev.rbm72.weaponsplugin.boss.gates;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.TickDamage;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ranged gate: the boss's ward is fed by conduits hanging in the air, well out of reach of any melee
 * swing. The only way to bring them down is to shoot them — bow, crossbow, throwable, or any ranged
 * ability. While a conduit is up the boss takes nothing, and it keeps drawing power, so the longer
 * they stand the harder the arena is punished.
 * <p>
 * This is the one gate a pure melee stack genuinely cannot brute-force by standing in the boss's face,
 * which is exactly the point: it asks a group that has been trading blows all fight to suddenly look
 * up, back off, and aim. Conduits drift so they cannot be pre-aimed from a fixed spot.
 */
public final class SkyshotGate implements PhaseMechanic {

    private static final long NOTICE_MS = 4000;

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Component label;
    private final Color color;
    private final Material conduitMaterial;
    private final int conduitCount;
    private final double conduitHealth;
    private final double heightAboveGround;
    private final double spreadFraction;
    private final int initialDelayTicks;
    private final double exposedMultiplier;
    private final int exposedDurationTicks;
    private final int staggerTicks;
    private final int reformDelayTicks;
    private final int drainIntervalTicks;
    private final double drainDamage;

    private final List<ArenaTotem> live = new ArrayList<>();
    private BukkitTask pendingTask;
    private BukkitTask drainTask;
    private boolean stopped;

    public SkyshotGate(BossInstance instance, Component label, Color color, Material conduitMaterial,
                        int conduitCount, double conduitHealth, double heightAboveGround,
                        double spreadFraction, int initialDelayTicks, double exposedMultiplier,
                        int exposedDurationTicks, int staggerTicks, int reformDelayTicks,
                        int drainIntervalTicks, double drainDamage) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.label = label;
        this.color = color;
        this.conduitMaterial = conduitMaterial;
        this.conduitCount = conduitCount;
        this.conduitHealth = conduitHealth;
        this.heightAboveGround = heightAboveGround;
        this.spreadFraction = spreadFraction;
        this.initialDelayTicks = initialDelayTicks;
        this.exposedMultiplier = exposedMultiplier;
        this.exposedDurationTicks = exposedDurationTicks;
        this.staggerTicks = staggerTicks;
        this.reformDelayTicks = reformDelayTicks;
        this.drainIntervalTicks = drainIntervalTicks;
        this.drainDamage = drainDamage;
    }

    @Override
    public void start() {
        instance.setDamageMultiplier(1.0);
        schedule(this::raise, Math.max(1, initialDelayTicks));
    }

    @Override
    public void stop() {
        stopped = true;
        cancel(pendingTask);
        pendingTask = null;
        cancel(drainTask);
        drainTask = null;
        for (ArenaTotem conduit : live) {
            conduit.discard();
        }
        live.clear();
        instance.setDamageMultiplier(1.0);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
    }

    private void raise() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        live.clear();
        instance.setDamageMultiplier(0.0);

        Location center = instance.entity().getLocation();
        AtomicInteger remaining = new AtomicInteger(conduitCount);
        double spread = instance.arena().radius() * spreadFraction;

        for (int i = 0; i < conduitCount; i++) {
            double angle = 2 * Math.PI * i / conduitCount
                    + ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
            double dist = spread * ThreadLocalRandom.current().nextDouble(0.5, 1.0);
            Location spot = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            // Height is measured from the terrain under the conduit, not from the boss's feet, so a
            // conduit over a rise still hangs out of melee reach instead of resting on the hill.
            var world = spot.getWorld();
            double groundY = world == null ? spot.getY()
                    : world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1;
            spot.setY(groundY + heightAboveGround);

            Fx.coloredBurst(spot.clone(), color, 1.6f, 24, 0.5);
            // snapToGround = false: the whole mechanic is that these hang in the air. Snapping would
            // drop them to the floor and turn this straight back into an ordinary melee weak point.
            ArenaTotem conduit = ArenaTotem.spawn(plugin, instance, spot, conduitMaterial, label,
                    conduitHealth, 20 * 60 * 20,
                    destroyed -> onConduitDown(remaining),
                    expired -> { },
                    false);
            live.add(conduit);
        }

        Component actionBar = label.color(NamedTextColor.GRAY)
                .append(Component.text("  —  out of reach. Shoot them down.", NamedTextColor.DARK_GRAY));
        for (Player player : Arena.combatants(center, instance.arena().radius())) {
            plugin.actionBarHub().flash(player, actionBar, NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
        }
        startDrain();
    }

    /** While conduits stand the boss keeps drawing power, and the arena pays for every beat they last. */
    private void startDrain() {
        cancel(drainTask);
        int interval = Math.max(20, drainIntervalTicks);
        drainTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (stopped || !instance.entity().isValid()) {
                cancel(drainTask);
                drainTask = null;
                return;
            }
            List<Location> standing = new ArrayList<>();
            for (ArenaTotem conduit : live) {
                if (conduit.isValid()) {
                    standing.add(conduit.location());
                }
            }
            if (standing.isEmpty()) {
                return;
            }
            Location bossLoc = instance.entity().getLocation();
            for (Location spot : standing) {
                Fx.line(spot, bossLoc.clone().add(0, 1.2, 0), org.bukkit.Particle.END_ROD, 12);
            }
            Fx.sound(bossLoc, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.7f);
            for (Player player : Arena.combatants(bossLoc, instance.arena().radius())) {
                TickDamage.apply(instance, player, drainDamage * standing.size());
            }
        }, interval, interval);
        instance.trackTask(drainTask);
    }

    private void onConduitDown(AtomicInteger remaining) {
        if (stopped) {
            return;
        }
        if (remaining.decrementAndGet() > 0) {
            Fx.sound(instance.entity().getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.4f);
            return;
        }
        expose();
    }

    private void expose() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        cancel(drainTask);
        drainTask = null;
        live.clear();
        instance.setDamageMultiplier(exposedMultiplier);
        instance.stagger(staggerTicks);
        instance.recordExposure();

        Location loc = instance.entity().getLocation();
        instance.entity().setGlowing(true);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), color, 2.2f, 50, 0.8);
        Fx.flash(loc.clone().add(0, 1.2, 0), 2);
        Fx.sound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.8f);
        instance.showTitle(
                Component.text("CONDUITS DOWN", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                label.color(NamedTextColor.GRAY));

        schedule(() -> {
            if (instance.entity().isValid()) {
                instance.entity().setGlowing(false);
            }
            schedule(this::raise, Math.max(1, reformDelayTicks));
        }, Math.max(1, exposedDurationTicks));
    }

    private void schedule(Runnable action, int delayTicks) {
        cancel(pendingTask);
        pendingTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingTask = null;
            action.run();
        }, delayTicks);
        instance.trackTask(pendingTask);
    }

    private static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
