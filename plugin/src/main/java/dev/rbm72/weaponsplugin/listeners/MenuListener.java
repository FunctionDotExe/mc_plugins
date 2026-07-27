package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.gui.AccessoryMenu;
import dev.rbm72.weaponsplugin.gui.AccessoryMenuHolder;
import dev.rbm72.weaponsplugin.gui.ArmorMenu;
import dev.rbm72.weaponsplugin.gui.ArmorMenuHolder;
import dev.rbm72.weaponsplugin.gui.BossMenu;
import dev.rbm72.weaponsplugin.gui.ConsumableMenu;
import dev.rbm72.weaponsplugin.gui.ConsumableMenuHolder;
import dev.rbm72.weaponsplugin.gui.HubMenu;
import dev.rbm72.weaponsplugin.gui.HubMenuHolder;
import dev.rbm72.weaponsplugin.gui.ShieldMenu;
import dev.rbm72.weaponsplugin.gui.ShieldMenuHolder;
import dev.rbm72.weaponsplugin.gui.RidableMenu;
import dev.rbm72.weaponsplugin.gui.RidableMenuHolder;
import dev.rbm72.weaponsplugin.gui.RealmsMenu;
import dev.rbm72.weaponsplugin.gui.RealmsMenuHolder;
import dev.rbm72.weaponsplugin.gui.StoneMenu;
import dev.rbm72.weaponsplugin.gui.StoneMenuHolder;
import dev.rbm72.weaponsplugin.gui.WeaponMenu;
import dev.rbm72.weaponsplugin.realm.RealmCrystalItem;
import dev.rbm72.weaponsplugin.ridable.Ridable;
import dev.rbm72.weaponsplugin.ridable.RidableManager;
import dev.rbm72.weaponsplugin.stone.Stone;
import dev.rbm72.weaponsplugin.stone.StoneManager;
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
        } else if (event.getInventory().getHolder() instanceof StoneMenuHolder) {
            event.setCancelled(true);
            handleStone(player, event);
        } else if (event.getInventory().getHolder() instanceof ArmorMenuHolder) {
            event.setCancelled(true);
            handleArmorCatalog(player, event.getCurrentItem());
        } else if (event.getInventory().getHolder() instanceof ShieldMenuHolder) {
            event.setCancelled(true);
            handleShieldCatalog(player, event.getCurrentItem());
        } else if (event.getInventory().getHolder() instanceof RidableMenuHolder) {
            event.setCancelled(true);
            handleRidable(player, event);
        } else if (event.getInventory().getHolder() instanceof RealmsMenuHolder) {
            event.setCancelled(true);
            handleRealms(player, event.getCurrentItem());
        } else if (event.getInventory().getHolder() instanceof ConsumableMenuHolder) {
            event.setCancelled(true);
            handleConsumableCatalog(player, event.getCurrentItem());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HubMenuHolder
                || event.getInventory().getHolder() instanceof AccessoryMenuHolder
                || event.getInventory().getHolder() instanceof StoneMenuHolder
                || event.getInventory().getHolder() instanceof ArmorMenuHolder
                || event.getInventory().getHolder() instanceof ShieldMenuHolder
                || event.getInventory().getHolder() instanceof RidableMenuHolder
                || event.getInventory().getHolder() instanceof RealmsMenuHolder
                || event.getInventory().getHolder() instanceof ConsumableMenuHolder) {
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
        } else if (rawSlot == HubMenu.STONES_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(StoneMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.ARMOR_SLOT && player.hasPermission("weaponsplugin.give")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(ArmorMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.SHIELDS_SLOT && player.hasPermission("weaponsplugin.give")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(ShieldMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.BOSSES_SLOT && player.hasPermission("weaponsplugin.boss.spawn")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(BossMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.RIDABLES_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(RidableMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.REALMS_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(RealmsMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.CONSUMABLES_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(ConsumableMenu.open(plugin, player)));
        } else if (rawSlot == HubMenu.WEAPONS_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(WeaponMenu.open(plugin, player)));
        }
    }

    private void handleRealms(Player player, ItemStack clicked) {
        String realmId = RealmCrystalItem.readRealmId(plugin, clicked);
        if (realmId == null) {
            return;
        }
        plugin.realmRegistry().get(realmId).ifPresent(realm -> {
            giveOrDrop(player, RealmCrystalItem.create(plugin, realm));
            player.sendMessage(Component.text("You received a Realm Crystal for ", NamedTextColor.LIGHT_PURPLE)
                    .append(realm.displayName()));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        });
    }

    private void handleConsumableCatalog(Player player, ItemStack clicked) {
        if (!player.hasPermission("weaponsplugin.give")) {
            return;
        }
        plugin.consumableRegistry().identify(clicked).ifPresent(consumable -> {
            giveOrDrop(player, consumable.createItem());
            player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(consumable.displayName()));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        });
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

    private void handleRidable(Player player, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory clickedInv = event.getClickedInventory();
        RidableManager manager = plugin.ridableManager();

        // Click inside the menu itself: catalog grab (op) or unequip the saddle slot.
        if (clickedInv == top) {
            int rawSlot = event.getRawSlot();
            if (RidableMenu.isEquipSlot(rawSlot)) {
                Ridable removed = manager.unequip(player);
                if (removed != null) {
                    giveOrDrop(player, removed.createItem());
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 0.8f);
                    RidableMenu.render(plugin, player, top);
                }
                return;
            }
            if (RidableMenu.isCatalogSlot(rawSlot) && player.hasPermission("weaponsplugin.give")) {
                plugin.ridableRegistry().identify(event.getCurrentItem()).ifPresent(ridable -> {
                    giveOrDrop(player, ridable.createItem());
                    player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(ridable.displayName()));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                });
            }
            return;
        }

        // Click in the player's own inventory: equip the saddle they clicked, if any.
        if (clickedInv == player.getInventory()) {
            ItemStack clicked = event.getCurrentItem();
            plugin.ridableRegistry().identify(clicked).ifPresent(ridable -> {
                if (manager.equip(player, ridable)) {
                    int amount = clicked.getAmount();
                    if (amount <= 1) {
                        clickedInv.setItem(event.getSlot(), null);
                    } else {
                        clicked.setAmount(amount - 1);
                    }
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.2f);
                    RidableMenu.render(plugin, player, top);
                } else {
                    player.sendMessage(Component.text("Unequip your current saddle first.", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                }
            });
        }
    }

    private void handleStone(Player player, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory clickedInv = event.getClickedInventory();
        StoneManager manager = plugin.stoneManager();

        // Click inside the menu itself: catalog grab (op) or unequip a socketed stone.
        if (clickedInv == top) {
            int rawSlot = event.getRawSlot();
            int equipIndex = StoneMenu.equipIndexOf(rawSlot);
            if (equipIndex >= 0) {
                Stone removed = manager.unequip(player, equipIndex);
                if (removed != null) {
                    giveOrDrop(player, removed.createItem());
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 0.8f);
                    StoneMenu.render(plugin, player, top);
                }
                return;
            }
            if (StoneMenu.isCatalogSlot(rawSlot) && player.hasPermission("weaponsplugin.give")) {
                plugin.stoneRegistry().identify(event.getCurrentItem()).ifPresent(stone -> {
                    giveOrDrop(player, stone.createItem());
                    player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(stone.displayName()));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                });
            }
            return;
        }

        // Click in the player's own inventory: socket the stone they clicked, if any.
        if (clickedInv == player.getInventory()) {
            ItemStack clicked = event.getCurrentItem();
            plugin.stoneRegistry().identify(clicked).ifPresent(stone -> {
                if (manager.equip(player, stone)) {
                    int amount = clicked.getAmount();
                    if (amount <= 1) {
                        clickedInv.setItem(event.getSlot(), null);
                    } else {
                        clicked.setAmount(amount - 1);
                    }
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.2f);
                    StoneMenu.render(plugin, player, top);
                } else {
                    player.sendMessage(Component.text("No free stone socket, or it's already socketed.", NamedTextColor.RED));
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
