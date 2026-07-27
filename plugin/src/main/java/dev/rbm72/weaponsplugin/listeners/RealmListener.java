package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.realm.RealmCrystalItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking a Realm Crystal steps the player through into that realm's boss arena. Uses a plain
 * interact (like every weapon/the hub star) rather than {@code PlayerItemConsumeEvent}: chorus fruit
 * runs its own short vanilla eat-and-teleport sequence on consume, and cancelling that event mid
 * sequence left the first attempt each session silently swallowed — the second always worked once
 * the client's use-item state had reset. Intercepting the interact instead never lets the vanilla
 * sequence start at all, so it's a single reliable click every time.
 */
public final class RealmListener implements Listener {

    private final WeaponsPlugin plugin;

    public RealmListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        boolean offHand = event.getHand() == EquipmentSlot.OFF_HAND;
        ItemStack held = offHand ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();

        String realmId = RealmCrystalItem.readRealmId(plugin, held);
        if (realmId == null) {
            return;
        }
        event.setCancelled(true);

        plugin.realmRegistry().get(realmId).ifPresent(realm -> {
            // Charge only once the player is actually inside. Consuming first meant any failure during
            // realm creation destroyed the crystal and left them standing exactly where they were.
            if (plugin.realmManager().enter(player, realm)) {
                held.setAmount(held.getAmount() - 1);
            }
        });
    }
}
