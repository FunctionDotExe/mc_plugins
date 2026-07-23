package dev.rbm72.weaponsplugin.boss.props;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * {@link ArenaTotem} manages its own HP entirely in Java, not through the entity's real vanilla
 * health — every hit is cancelled here so environmental damage, the boss's own AoEs, and fire/void
 * can never break one. Only a hit directly attributable to a player (melee or a player-shot
 * projectile) is forwarded to the totem's own HP pool.
 */
public final class ArenaTotemDamageListener implements Listener {

    private static final String KEY_NAME = "arena_totem";

    static NamespacedKey marker(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    private final NamespacedKey key;

    public ArenaTotemDamageListener(WeaponsPlugin plugin) {
        this.key = marker(plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);

        ArenaTotem totem = ArenaTotem.get(entity.getUniqueId());
        if (totem == null) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker != null) {
            totem.applyPlayerDamage(event.getDamage());
        }
    }

    private Player resolveAttacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
