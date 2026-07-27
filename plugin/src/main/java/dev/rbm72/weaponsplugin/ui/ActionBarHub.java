package dev.rbm72.weaponsplugin.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single owner of every player's action bar.
 *
 * <p>A player's action bar holds exactly one line, and the last write in a tick wins. This plugin
 * has plenty of systems with something to say there — weapon ability cooldowns, movement-stone
 * charges, a mounted saddle's ability, a boss's cast bar, one-off combat notices — and while each
 * one used to send its own line on its own repeating timer, any two of them being active at once
 * meant the bar visibly strobed between them several times a second. That's the flicker you get
 * from using a movement ability while a weapon cooldown is still running: two timers, both correct,
 * both writing, neither aware of the other.
 *
 * <p>So nothing sends its own action bar any more. Persistent displays register as a {@link Source}
 * and are polled once per refresh, then merged into one line in a stable order. Momentary notices
 * ("Parried!", a boss cast bar) go through {@link #flash} and take over the whole bar for a set
 * duration, highest priority winning — a merged line of cooldown timers is not what you need to be
 * reading in the half second after a parry.
 */
public final class ActionBarHub {

    /** Refresh cadence. Matches what the individual display tasks used, so countdowns read just as smoothly. */
    private static final long TICK_INTERVAL = 2L;

    private static final Component SEPARATOR = Component.text("  |  ", NamedTextColor.GRAY);

    /** A persistent contributor to the merged line — polled every refresh, never sends anything itself. */
    public interface Source {
        /**
         * This source's pieces of the bar for {@code player} right now, or an empty list if it has
         * nothing to show. Called on the main thread every refresh, so it must only read state —
         * never advance timers or fire effects.
         */
        List<Component> segments(Player player);
    }

    /** Priority for a routine one-off notice (combo counter, parry, ability feedback). */
    public static final int PRIORITY_NOTICE = 10;
    /** Priority for a continuously re-sent takeover like a boss cast bar — deliberately below one-off notices. */
    public static final int PRIORITY_SUSTAINED = 5;

    /**
     * Copy-on-write because {@link #unregister} exists: a fight-scoped source is dropped from fight
     * teardown, which is the same main thread the refresh runs on but not necessarily outside it — a
     * teardown reached from inside a merge would otherwise be a concurrent modification. Writes happen
     * once per fight, reads several times a second, so the copy is free in practice.
     */
    private final List<Source> sources = new CopyOnWriteArrayList<>();
    private final Map<UUID, Flash> flashes = new HashMap<>();

    private record Flash(Component message, long expiresAtMs, int priority) {
        boolean active(long now) {
            return now < expiresAtMs;
        }
    }

    /**
     * Adds a persistent contributor. Registration order is display order, and it's fixed for the
     * server's lifetime — a segment never jumps position because some other system started or
     * stopped showing something.
     */
    public void register(Source source) {
        sources.add(source);
    }

    /**
     * Drops a contributor that is not meant to last the server's lifetime — a boss fight's meter
     * readout, which exists only while that fight does. Without this every fight would leave a source
     * behind, each one polled for every online player forever, all of them holding their dead fight's
     * state alive.
     */
    public void unregister(Source source) {
        sources.remove(source);
    }

    public void start(Plugin plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 0L, TICK_INTERVAL);
    }

    /**
     * Takes over {@code player}'s whole bar with {@code message} for {@code durationMs}, hiding the
     * merged line underneath for that window.
     *
     * <p>An in-flight flash is only replaced by one of equal or higher priority, so a sustained
     * takeover that re-sends itself every tick (a boss cast bar) can't stomp a brief high-priority
     * notice the moment after it appears. Use {@link #PRIORITY_NOTICE} / {@link #PRIORITY_SUSTAINED}
     * rather than raw numbers.
     */
    public void flash(Player player, Component message, long durationMs, int priority) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Flash current = flashes.get(uuid);
        if (current != null && current.active(now) && current.priority() > priority) {
            return;
        }
        flashes.put(uuid, new Flash(message, now + durationMs, priority));
    }

    /**
     * Ends {@code player}'s current flash early, dropping straight back to the merged line — for a
     * sustained takeover that knows exactly when it's finished (a cast bar the instant its attack
     * lands) rather than waiting out a duration it only guessed at.
     *
     * <p>Only clears a flash at or below {@code priority}, so a cast bar ending can't wipe the parry
     * notice that landed on top of it a moment earlier.
     */
    public void clearFlash(Player player, int priority) {
        UUID uuid = player.getUniqueId();
        Flash current = flashes.get(uuid);
        if (current != null && current.priority() <= priority) {
            flashes.remove(uuid);
        }
    }

    private void refreshAll() {
        long now = System.currentTimeMillis();
        flashes.entrySet().removeIf(entry -> !entry.getValue().active(now));

        for (Player player : Bukkit.getOnlinePlayers()) {
            Flash flash = flashes.get(player.getUniqueId());
            if (flash != null) {
                player.sendActionBar(flash.message());
                continue;
            }
            Component merged = merge(player);
            if (merged != null) {
                player.sendActionBar(merged);
            }
            // Nothing to show: deliberately send nothing rather than an empty line. The client fades
            // the previous message out on its own, and blanking every tick would cut that fade short.
        }
    }

    private Component merge(Player player) {
        Component combined = null;
        for (Source source : sources) {
            for (Component segment : source.segments(player)) {
                combined = combined == null ? segment : combined.append(SEPARATOR).append(segment);
            }
        }
        return combined;
    }
}
