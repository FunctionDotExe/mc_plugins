package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Player-summoned mobs (necromancer skeletons/vexes/guardian, etc.) are vanilla hostile
 * entities under the hood, so their stock AI happily targets the nearest player — including
 * their own owner. This blanket-cancels any such summon from ever targeting a player, so they
 * only ever go after the boss/hostile mobs they were raised to fight.
 */
public final class PlayerSummonTargetListener implements Listener {

    public static final String KEY_NAME = "player_summon";

    private final NamespacedKey key;

    public PlayerSummonTargetListener(WeaponsPlugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public NamespacedKey key() {
        return key;
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player && event.getEntity().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
