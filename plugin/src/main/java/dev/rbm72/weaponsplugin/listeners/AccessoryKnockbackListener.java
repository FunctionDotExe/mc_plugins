package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Cancels the velocity change (not the damage) for any player wearing an accessory whose
 * {@link Accessory#negatesKnockback()} is true. Fires for every knockback cause — hit-by-mob,
 * explosion, sweep, etc. — the damage event itself already resolved by the time this fires, so
 * cancelling here removes only the shove, never the hit.
 * <p>
 * The camera jolt is <em>not</em> here. It rides the damage event, not the knockback, so suppressing it
 * is a separate job with its own hazards — see {@link AccessoryFlinchListener}.
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
