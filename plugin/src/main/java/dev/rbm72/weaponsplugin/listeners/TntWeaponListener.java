package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Weapon;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Strips block destruction from any {@link TNTPrimed} tagged with a weapon id (currently just
 * Blastcaller) while leaving the rest of the explosion untouched. Entity damage and knockback are
 * computed by vanilla before this event ever fires, so clearing the block list here gives a
 * genuinely real explosion against players/mobs without letting a player-thrown weapon crater the
 * map the way a boss's grief-gated explosion is allowed to.
 */
public final class TntWeaponListener implements Listener {

    private final WeaponsPlugin plugin;

    public TntWeaponListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        if (!tnt.getPersistentDataContainer().has(Weapon.idKey(plugin), PersistentDataType.STRING)) {
            return;
        }
        event.blockList().clear();
    }
}
