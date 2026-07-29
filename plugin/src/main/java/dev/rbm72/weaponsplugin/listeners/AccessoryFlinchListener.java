package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.boss.AmbientTickListener;
import dev.rbm72.weaponsplugin.boss.TickDamage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Makes {@link Accessory#negatesTickFlinch()} a guarantee: with the accessory on, damage-over-time
 * cannot roll your camera.
 * <p>
 * The plugin's own DoT already avoids the tilt by going through {@link TickDamage}, and
 * {@link AmbientTickListener} does the same for vanilla's environmental ticks inside a live arena. This
 * is the backstop for everything else — any source that hits the same player repeatedly through the
 * ordinary damage pipeline, anywhere. It classifies by <em>rate</em> rather than by cause, because
 * "this is a tick, not a hit" is a property of how often it lands, and a new attack written next month
 * shouldn't need to be added to a list to be covered.
 * <p>
 * <b>The trap this class exists to avoid.</b> Cancelling an {@link EntityDamageEvent} means vanilla
 * never runs the rest of {@code hurt()}, and that includes setting {@code invulnerableTime}. A boss
 * loop calling {@code player.damage} every tick is normally throttled to one landed hit per 10 ticks by
 * those i-frames; strip the event and every single tick lands, i.e. the same attack silently starts
 * doing ten times its damage. So interception carries its own 10-tick gate, and follows vanilla's rule
 * for a bigger hit arriving inside the window: only the difference is applied.
 * <p>
 * The first hit from a source is deliberately left alone. It is the one the player wants to feel, and
 * the vanilla pipeline is also what pays armor durability and shield wear — worth keeping on the hit
 * that opens an exchange, not worth paying on every tick of a lava pool.
 */
public final class AccessoryFlinchListener implements Listener {

    /**
     * Two hits from the same cause within this window are a tick, not two hits. One second: a DoT loop
     * pulses far faster than this, and a boss rarely lands the same telegraphed attack twice inside it.
     */
    private static final int REPEAT_WINDOW_TICKS = 20;
    /** Vanilla's invulnerability window, which cancelling the event would otherwise throw away. */
    private static final int IFRAME_TICKS = 10;
    /** Prune bookkeeping for anyone untouched for this long, so the maps can't grow without bound. */
    private static final int STALE_TICKS = 200;

    private final AccessoryManager accessories;
    private final Map<UUID, Record> records = new HashMap<>();

    public AccessoryFlinchListener(AccessoryManager accessories) {
        this.accessories = accessories;
    }

    /** Per-player bookkeeping: when each cause was last seen, and what this class last applied. */
    private static final class Record {
        final Map<EntityDamageEvent.DamageCause, Integer> lastSeen = new HashMap<>();
        int lastAppliedTick = Integer.MIN_VALUE;
        double lastAppliedAmount;
        int touchedTick;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!accessories.negatesTickFlinch(player)) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        Record record = records.computeIfAbsent(player.getUniqueId(), key -> new Record());
        record.touchedTick = now;

        Integer previous = record.lastSeen.put(event.getCause(), now);
        boolean recurring = AmbientTickListener.isAmbient(event.getCause())
                || (previous != null && now - previous <= REPEAT_WINDOW_TICKS);
        if (!recurring) {
            prune(now);
            return;
        }

        // Vanilla's i-frames, reimplemented because cancelling the event skips the code that sets them.
        double amount = event.getFinalDamage();
        if (now - record.lastAppliedTick < IFRAME_TICKS) {
            if (amount <= record.lastAppliedAmount) {
                // Vanilla would have ignored this hit outright. Swallow it: no damage, no tilt.
                event.setCancelled(true);
                prune(now);
                return;
            }
            amount -= record.lastAppliedAmount;
        }

        if (TickDamage.applyResolved(player, amount)) {
            record.lastAppliedTick = now;
            record.lastAppliedAmount = event.getFinalDamage();
            event.setCancelled(true);
        }
        prune(now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        records.remove(event.getPlayer().getUniqueId());
    }

    private void prune(int now) {
        records.values().removeIf(record -> now - record.touchedTick > STALE_TICKS);
    }
}
