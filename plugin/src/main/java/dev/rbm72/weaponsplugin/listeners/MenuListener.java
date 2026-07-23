package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.gui.AccessoryMenu;
import dev.rbm72.weaponsplugin.gui.AccessoryMenuHolder;
import dev.rbm72.weaponsplugin.gui.ArmorMenu;
import dev.rbm72.weaponsplugin.gui.ArmorMenuHolder;
import dev.rbm72.weaponsplugin.gui.BossMenu;
import dev.rbm72.weaponsplugin.gui.HubMenu;
import dev.rbm72.weaponsplugin.gui.HubMenuHolder;
import dev.rbm72.weaponsplugin.gui.ShieldMenu;
import dev.rbm72.weaponsplugin.gui.ShieldMenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Handles clicks in the hub menu (routing to ender chest / accessories) and the accessory equip menu. */
public final class MenuListener implements Listener {

    private final WeaponsPlugin plugin;

    public MenuListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof HubMenuHolder) {
            event.setCancelled(true);
            handleHub(player, event.getRawSlot());
        } else if (event.getInventory().getHolder() instanceof AccessoryMenuHolder) {
            event.setCancelled(true);
            handleAccessory(player, event);
        } else if (event.getInventory().getHolder() instanceof ArmorMenuHolder) {
            event.setCancelled(true);
            handleArmorCatalog(player, event.getCurrentItem());
        } else if (event.getInventory().getHolder() instanceof ShieldMenuHolder) {
            event.setCancelled(true);
            handleShieldCatalog(player, event.getCurrentItem());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HubMenuHolder
                || event.getInventory().getHolder() instanceof AccessoryMenuHolder
                || event.getInventory().getHolder() instanceof ArmorMenuHolder
                || event.getInventory().getHolder() instanceof ShieldMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleHub(Player player, int rawSlot) {
        if (rawSlot == HubMenu.ENDER_CHEST_SLOT) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.0f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(player.getEnderChest()));
        } else if (rawSlot == HubMenu.ACCESSORIES_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(AccessoryMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.ARMOR_SLOT && player.hasPermission("weaponsplugin.give")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(ArmorMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.SHIELDS_SLOT && player.hasPermission("weaponsplugin.give")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(ShieldMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.BOSSES_SLOT && player.hasPermission("weaponsplugin.boss.spawn")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(BossMenu.open(plugin, player)));
        }
    }

    private void handleArmorCatalog(Player player, ItemStack clicked) {
        if (!player.hasPermission("weaponsplugin.give")) {
            return;
        }
        plugin.armorRegistry().identifyPiece(clicked).ifPresent(piece -> {
            giveOrDrop(player, piece.createItem());
            player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(piece.displayName()));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        });
    }

    private void handleShieldCatalog(Player player, ItemStack clicked) {
        if (!player.hasPermission("weaponsplugin.give")) {
            return;
        }
        plugin.shieldRegistry().identify(clicked).ifPresent(shield -> {
            giveOrDrop(player, shield.createItem());
            player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(shield.displayName()));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        });
    }

    private void handleAccessory(Player player, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory clickedInv = event.getClickedInventory();
        AccessoryManager manager = plugin.accessoryManager();

        // Click inside the menu itself: catalog grab (op) or unequip an equipped slot.
        if (clickedInv == top) {
            int rawSlot = event.getRawSlot();
            int equipIndex = AccessoryMenu.equipIndexOf(rawSlot);
            if (equipIndex >= 0) {
                Accessory removed = manager.unequip(player, equipIndex);
                if (removed != null) {
                    giveOrDrop(player, removed.createItem());
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 0.8f);
                    AccessoryMenu.render(plugin, player, top);
                }
                return;
            }
            if (AccessoryMenu.isCatalogSlot(rawSlot) && player.hasPermission("weaponsplugin.give")) {
                plugin.accessoryRegistry().identify(event.getCurrentItem()).ifPresent(accessory -> {
                    giveOrDrop(player, accessory.createItem());
                    player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(accessory.displayName()));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                });
            }
            return;
        }

        // Click in the player's own inventory: equip the accessory they clicked, if any.
        if (clickedInv == player.getInventory()) {
            ItemStack clicked = event.getCurrentItem();
            plugin.accessoryRegistry().identify(clicked).ifPresent(accessory -> {
                if (manager.equip(player, accessory)) {
                    int amount = clicked.getAmount();
                    if (amount <= 1) {
                        clickedInv.setItem(event.getSlot(), null);
                    } else {
                        clicked.setAmount(amount - 1);
                    }
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.2f);
                    AccessoryMenu.render(plugin, player, top);
                } else {
                    player.sendMessage(Component.text("No free accessory slot, or it's already equipped.", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                }
            });
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
