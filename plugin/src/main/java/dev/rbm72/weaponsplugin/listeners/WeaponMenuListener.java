package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.gui.WeaponMenu;
import dev.rbm72.weaponsplugin.gui.WeaponMenuHolder;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class WeaponMenuListener implements Listener {

    private final WeaponsPlugin plugin;

    public WeaponMenuListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WeaponMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        if (WeaponMenu.isFilterButton(plugin, clicked)) {
            Rarity newFilter = WeaponMenu.readFilter(plugin, clicked);
            holder.setFilter(newFilter);
            holder.setPage(0);
            WeaponMenu.render(plugin, player, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (WeaponMenu.isPageButton(plugin, clicked)) {
            holder.setPage(holder.page() + WeaponMenu.readPageDelta(plugin, clicked));
            WeaponMenu.render(plugin, player, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (!player.hasPermission("weaponsplugin.give")) {
            return;
        }

        plugin.weaponRegistry().identify(clicked).ifPresent(weapon -> giveWeapon(player, weapon));
    }

    private void giveWeapon(Player player, Weapon weapon) {
        player.getInventory().addItem(weapon.createItem());
        player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(weapon.displayName()));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WeaponMenuHolder) {
            event.setCancelled(true);
        }
    }
}
