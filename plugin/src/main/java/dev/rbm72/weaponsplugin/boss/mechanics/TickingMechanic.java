package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.boss.TickDamage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Level;

/**
 * Shared skeleton for every phase mechanic in the rework: one tracked repeating task, an idempotent
 * teardown that can never leave the boss unhittable, and a tick that can throw without killing the
 * fight.
 * <p>
 * Nearly every bug the first pass of this rework shipped was one of the same four: a task that was
 * never tracked and outlived the fight, a {@code stop()} that ran twice and double-cleaned, a
 * {@code setForcedInvulnerable(true)} with no guaranteed release, and a mechanic bar left on screen
 * after the phase ended. All four are handled here once so no subclass can get them wrong —
 * {@link #start()} and {@link #stop()} are deliberately {@code final} and subclasses hook
 * {@link #onStart()}/{@link #onStop()} instead.
 */
public abstract class TickingMechanic implements PhaseMechanic {

    protected final BossInstance instance;
    protected final WeaponsPlugin plugin;

    /** Ticks between {@link #tick()} calls — the mechanic's own resolution, not the server's. */
    private final long intervalTicks;

    private BukkitTask task;
    private boolean started;
    protected boolean stopped;

    /** Elapsed ticks since {@link #start()}, advanced by {@link #intervalTicks} each pulse. */
    protected int elapsedTicks;

    protected TickingMechanic(BossInstance instance, long intervalTicks) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.intervalTicks = Math.max(1L, intervalTicks);
    }

    @Override
    public final void start() {
        if (started || stopped) {
            return;
        }
        started = true;
        // Whatever the previous phase's mechanic left the multiplier on is not this phase's business.
        instance.setDamageMultiplier(1.0);
        try {
            onStart();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Phase mechanic " + getClass().getSimpleName()
                    + " threw while starting — continuing with the fight.", e);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, intervalTicks, intervalTicks);
        instance.trackTask(task);
    }

    private void pulse() {
        if (stopped) {
            return;
        }
        if (!instance.entity().isValid()) {
            return;
        }
        try {
            tick();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Phase mechanic " + getClass().getSimpleName()
                    + " threw on tick — skipping this pulse.", e);
        }
        elapsedTicks += (int) intervalTicks;
    }

    @Override
    public final void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        try {
            onStop();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Phase mechanic " + getClass().getSimpleName()
                    + " threw while stopping — releasing the boss anyway.", e);
        }
        // Unconditional release, whatever onStop() did or failed to do. A mechanic that dies holding
        // the boss invulnerable is an unfinishable fight, which is the single worst failure mode here.
        // Releases rather than clears: an event may be mid-run and holding the bar, and a mechanic
        // shutting down must not wipe the readout the player is currently being asked to act on.
        instance.mechanicBar().release(MechanicBar.Owner.MECHANIC);
        instance.setDamageMultiplier(1.0);
        instance.setForcedInvulnerable(false);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
    }

    /** Arm props, spawn adds, show the opening title. Called once, before the first {@link #tick()}. */
    protected void onStart() {
    }

    /** Release props, adds, and player effects. Called exactly once; never needs a guard of its own. */
    protected void onStop() {
    }

    /** One pulse of the mechanic, every {@code intervalTicks}. Never called after {@link #stop()}. */
    protected abstract void tick();

    // ---------------------------------------------------------------- helpers

    /** Everyone currently in the fight — the population every mechanic actually acts on. */
    protected final List<Player> combatants() {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    /** Seconds elapsed since the phase started, for readouts and ramping difficulty. */
    protected final double elapsedSeconds() {
        return elapsedTicks / 20.0;
    }

    protected final World world() {
        Location center = instance.arena().center();
        return center.getWorld();
    }

    /**
     * A point at {@code fraction} of the arena radius from its <em>fixed</em> centre, dropped onto the
     * surface. Anchored to the centre rather than the boss on purpose: the boss spends most of a fight
     * pressed against the arena wall by its own leash, so anything placed relative to it routinely
     * lands outside the reachable floor (Pitfall 6).
     */
    protected final Location surfaceSpot(double angleRadians, double fraction) {
        Location center = instance.arena().center();
        World world = center.getWorld();
        if (world == null) {
            return center;
        }
        double dist = instance.arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = center.clone().add(Math.cos(angleRadians) * dist, 0, Math.sin(angleRadians) * dist);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }

    /** Horizontal distance only — vertical separation is never what a floor mechanic cares about. */
    protected static double flatDistance(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Damage credited to the boss, skipping anyone already dead or gone since the list was taken. */
    protected final void hurt(Player player, double amount) {
        if (player == null || !player.isOnline() || !player.isValid() || player.isDead() || amount <= 0) {
            return;
        }
        player.damage(amount, instance.entity());
    }

    /**
     * Damage-over-time credited to the boss: health comes off, the camera does not tilt and momentum
     * is not stolen. Use this for anything that pulses on a timer; {@link #hurt} stays the right call
     * for a discrete hit the player is meant to feel land. See {@link TickDamage}.
     */
    protected final void tickHurt(Player player, double amount) {
        TickDamage.apply(instance, player, amount);
    }

    /** True when one player is carrying the whole fight — every mechanic needs a defined solo answer. */
    protected final boolean solo() {
        return combatants().size() <= 1;
    }
}
