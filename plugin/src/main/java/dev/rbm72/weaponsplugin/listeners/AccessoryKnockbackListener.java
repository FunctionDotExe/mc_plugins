package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * Cancels the velocity change (not the damage) for any player wearing an accessory whose
 * {@link Accessory#negatesKnockback()} is true.
 * <p>
 * The camera jolt is <em>not</em> here. It rides the damage event, not the knockback, so suppressing it
 * is a separate job with its own hazards — see {@link AccessoryFlinchListener}.
 *
 * <h2>Why this takes three swings at the same thing</h2>
 * "Immune to knockback" turns out to have three separate enforcement points, and the accessory needs all
 * three, because a shove reaches the player by whichever one the source happens to use:
 * <ol>
 *   <li>{@code KNOCKBACK_RESISTANCE}, applied as an attribute by {@code AccessoryManager} — vanilla's own
 *       source-side scalar, which zeroes the shove before it is ever an event.</li>
 *   <li>{@link EntityKnockbackEvent} here, for anything that raises it.</li>
 * </ol>
 *
 * <h2>What is deliberately NOT here: cancelling {@link PlayerVelocityEvent}</h2>
 * That looks like the obvious third point — a boss attack writing {@code setVelocity} directly (there are
 * dozens across the roster) consults neither the attribute nor the knockback event, and reaches the
 * player as a plain velocity packet. It was tried, and it is worse than the problem. Suppressing the
 * packet leaves the server's own copy of the player's motion shoved while the client never hears about
 * it, and the two then disagree about where the player is and whether they are falling: the symptom is a
 * player who cannot jump and who hears their own landing sound over and over. The event also carries the
 * player's <em>own</em> movement tech — wind charges, riptide, weapon leaps, boats — so there is no clean
 * way to tell a shove from a boost at that layer.
 * <p>
 * A direct-velocity shove is fixed at the source instead: the attack asks
 * {@code AccessoryManager#negatesKnockback} before pushing, the way {@code StormcallAttack} already does.
 * {@code DamageTrace}'s {@code SHOVE ... APPLIED} line names which attack needs it.
 */
public final class AccessoryKnockbackListener implements Listener {

    private final AccessoryManager accessories;

    public AccessoryKnockbackListener(AccessoryManager accessories) {
        this.accessories = accessories;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (accessories.negatesKnockback(player)) {
            event.setCancelled(true);
        }
    }

}
