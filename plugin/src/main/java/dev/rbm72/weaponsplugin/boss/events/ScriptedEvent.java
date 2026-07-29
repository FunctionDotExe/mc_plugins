package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.boss.TickDamage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Level;

/**
 * Shared skeleton for the rework's scripted events, built entirely around Pitfall 1: an event that
 * returns without calling {@code onComplete} freezes the boss for the rest of the fight — no attacks,
 * no chase, forever. That shipped once already, so here every possible outcome (success, failure,
 * timeout, entity gone, a thrown exception anywhere in the event's own code) is routed through a
 * single {@link #finish} that nulls the runnable before invoking it.
 * <p>
 * Subclasses implement {@link #begin}/{@link #tick}/{@link #expire} and never touch the task, the
 * completion callback, or the resolved flag.
 */
public abstract class ScriptedEvent extends BossEvent {

    private final double[] triggers;

    private BukkitTask task;
    private Runnable onComplete;
    private boolean resolved;

    protected ScriptedEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        super(plugin, bossId);
        this.triggers = triggers.clone();
    }

    @Override
    public final double[] triggerFractions() {
        return triggers.clone();
    }

    /** Total length of the event; {@link #expire} fires when the clock runs out. */
    protected abstract int durationTicks();

    /**
     * Sets the event up. Return false when there is nothing to run (nobody in the arena, no valid
     * spot) — the event then ends immediately and cleanly rather than holding the fight hostage.
     */
    protected abstract boolean begin(BossInstance instance);

    /** One tick of the event, {@code ticks} counting from 0. */
    protected abstract void tick(BossInstance instance, int ticks);

    /** The clock ran out with the event unresolved — apply the failure consequence. */
    protected abstract void expire(BossInstance instance);

    /** Subclass teardown: props, adds, player effects. Always runs exactly once per activation. */
    protected void onCleanup(BossInstance instance) {
    }

    @Override
    public final void run(BossInstance instance, Runnable onComplete) {
        this.onComplete = onComplete;
        this.resolved = false;

        boolean armed;
        try {
            armed = begin(instance);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Boss event '" + id() + "' threw while starting — ending it immediately.", e);
            armed = false;
        }
        if (!armed) {
            finish(instance);
            return;
        }

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;

            @Override
            public void run() {
                if (resolved) {
                    return;
                }
                if (!instance.entity().isValid()) {
                    finish(instance);
                    return;
                }
                try {
                    tick(instance, ticks);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Boss event '" + id() + "' threw on tick — ending it early.", e);
                    finish(instance);
                    return;
                }
                ticks++;
                if (!resolved && ticks >= durationTicks()) {
                    try {
                        expire(instance);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Boss event '" + id() + "' threw resolving its timeout.", e);
                    }
                    finish(instance);
                }
            }
        }, 1L, 1L);
        instance.trackTask(task);
    }

    /**
     * Ends the event exactly once and always resumes the fight. Safe to call from anywhere, any number
     * of times — the second call is a no-op because the callback has already been consumed.
     */
    protected final void finish(BossInstance instance) {
        if (resolved && onComplete == null) {
            return;
        }
        resolved = true;
        cleanup(instance);
        Runnable resume = onComplete;
        onComplete = null;
        if (resume != null) {
            resume.run();
        }
    }

    /** True once this activation has been resolved — guards handlers that can fire after the fact. */
    protected final boolean isResolved() {
        return resolved;
    }

    @Override
    public final void cleanup(BossInstance instance) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        try {
            onCleanup(instance);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Boss event '" + id() + "' threw during cleanup.", e);
        }
        // Releases rather than clears, so the phase mechanic underneath gets its own readout back on
        // its next tick instead of the bar staying blank for the rest of the phase.
        instance.mechanicBar().release(MechanicBar.Owner.EVENT);
        instance.setForcedInvulnerable(false);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
    }

    // ---------------------------------------------------------------- helpers

    protected static List<Player> combatants(BossInstance instance) {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    protected static void hurt(BossInstance instance, Player player, double amount) {
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
    protected static void tickHurt(BossInstance instance, Player player, double amount) {
        TickDamage.apply(instance, player, amount);
    }

    /** A reachable point on the arena floor, anchored to the fixed centre rather than the boss. */
    protected static Location surfaceSpot(BossInstance instance, double angleRadians, double fraction) {
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

    protected static double flatDistance(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
