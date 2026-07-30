package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.boss.TickDamage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Makes {@link Accessory#negatesTickFlinch()} total: with the accessory on, damage costs health and
 * nothing else. No camera roll, no hop, no movement stall, no hurt sound — the health bar moves and
 * that is the entire tell.
 *
 * <h2>Why cancelling the event is the whole fix</h2>
 * Every symptom players describe as "it stuns me" is one thing: vanilla's {@code hurt()}. Receiving a
 * damage event is what rolls the client's camera, applies the knockback that lifts you slightly and
 * kills your momentum, and burns the i-frames. They are not separate effects to suppress one by one and
 * there is no vanilla switch for "damage but no reaction" — so the only way to have health drop without
 * any of it is for the damage event to never resolve. This cancels it and writes the health off directly
 * through {@link TickDamage}.
 *
 * <h2>Why this is unconditional, and what that replaced</h2>
 * This class used to classify by <em>rate</em>: two hits from the same cause inside a second were a
 * "tick" and got silenced, while the first hit of any exchange was deliberately left alone as feedback.
 * That was wrong for an item whose entire promise is that hits do not move you. A damage-over-time
 * source pulsing every 5 ticks lands on i-frames every 10, and under the old rule the first pulse of
 * every new source still tilted, still hopped, and still stalled — once a second, forever, which is
 * exactly the complaint. There is no "this one is feedback you wanted" case for an anchor. Every hit on
 * the wearer is intercepted.
 *
 * <h2>The trap this class exists to avoid</h2>
 * Cancelling an {@link EntityDamageEvent} means vanilla never runs the rest of {@code hurt()}, and that
 * includes setting {@code invulnerableTime}. A boss loop calling {@code player.damage} every tick is
 * normally throttled to one landed hit per 10 ticks by those i-frames; strip the event and every single
 * tick lands, i.e. the same attack silently starts doing ten times its damage. So interception carries
 * its own 10-tick gate, and follows vanilla's rule for a bigger hit arriving inside the window: only the
 * difference is applied.
 *
 * <h2>What still gets through</h2>
 * A lethal hit — vanilla owns dying, because {@code setHealth(0)} skips totems, the death message and
 * kill credit. And the causes in {@link #ALWAYS_VANILLA}. One hurt tilt on the hit that kills you is not
 * the complaint this class exists to fix.
 */
public final class AccessoryFlinchListener implements Listener {

    /** Vanilla's invulnerability window, which cancelling the event would otherwise throw away. */
    private static final int IFRAME_TICKS = 10;
    /** Prune bookkeeping for anyone untouched for this long, so the maps can't grow without bound. */
    private static final int STALE_TICKS = 200;
    /**
     * Causes this class must never intercept.
     * <p>
     * {@code VOID} and {@code KILL} must reach the player at all costs — they are the engine's and an
     * operator's last word, and an accessory that could swallow them would make a player unkillable in
     * the one place that matters. {@code FALL} is excluded for a different reason: cancelling it leaves
     * the player's accumulated fall distance in place, so the server re-runs the same fall next tick and
     * keeps re-running it — a landing sound per attempt and a player who cannot jump. Fall damage inside
     * an arena belongs to {@code ArenaSafetyListener} anyway (§0.3: no death by falling), and that class
     * clears the distance when it cancels.
     */
    private static final Set<EntityDamageEvent.DamageCause> ALWAYS_VANILLA = EnumSet.of(
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.KILL);

    private final AccessoryManager accessories;
    private final Map<UUID, Record> records = new HashMap<>();

    public AccessoryFlinchListener(AccessoryManager accessories) {
        this.accessories = accessories;
    }

    /** Per-player bookkeeping: the i-frame window this class has to keep for itself. */
    private static final class Record {
        int lastAppliedTick = Integer.MIN_VALUE;
        double lastAppliedAmount;
        int touchedTick;
    }

    /**
     * HIGHEST rather than MONITOR: every reduction — armor, enchantments, Resistance, absorption — is
     * already folded into {@code getFinalDamage()} by the time this runs, so the number here is exactly
     * what vanilla was about to take off the health bar, while cancelling is still legal.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!accessories.negatesTickFlinch(player)) {
            return;
        }
        if (ALWAYS_VANILLA.contains(event.getCause())) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        Record record = records.computeIfAbsent(player.getUniqueId(), key -> new Record());
        record.touchedTick = now;

        double amount = event.getFinalDamage();
        if (amount <= 0.0) {
            // Nothing left to take: armor, Resistance or a shield already ate the whole hit. What remains
            // of the event is purely the reaction — camera, hop, stall — so drop it and take nothing.
            //
            // Unless absorption is what ate it. A resolved zero hides how much absorption was spent
            // getting there, and cancelling would spend none of it — an accessory that made a golden
            // apple permanent. Vanilla keeps that one; it lasts seconds.
            if (player.getAbsorptionAmount() <= 0) {
                event.setCancelled(true);
            }
            prune(now);
            return;
        }

        // Vanilla's i-frames, reimplemented because cancelling the event skips the code that sets them.
        double toApply = amount;
        if (now - record.lastAppliedTick < IFRAME_TICKS) {
            if (amount <= record.lastAppliedAmount) {
                // Vanilla would have ignored this hit outright. Swallow it: no damage, no reaction.
                event.setCancelled(true);
                prune(now);
                return;
            }
            toApply = amount - record.lastAppliedAmount;
        }

        if (TickDamage.applyResolved(player, toApply)) {
            record.lastAppliedTick = now;
            record.lastAppliedAmount = amount;
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
