package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Accessory screen: the top row is a catalog (ops click to grab a copy, like
 * the weapon menu), the middle band holds the player's equip slots. Clicking an
 * accessory in your own inventory equips it; clicking an equipped one unequips.
 */
public final class AccessoryMenu {

    public static final Component TITLE = Component.text("Accessories", NamedTextColor.AQUA, TextDecoration.BOLD);
    private static final int SIZE = 54;
    private static final int LABEL_SLOT = 19;
    /** The player's equip slots, one per {@link AccessoryManager#MAX_SLOTS}. */
    public static final int[] EQUIP_SLOTS = {21, 22, 23, 24};

    private AccessoryMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        AccessoryMenuHolder holder = new AccessoryMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, inventory);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, Inventory inventory) {
        inventory.clear();

        boolean unlocked = viewer.hasPermission("weaponsplugin.give");
        int slot = 0;
        for (Accessory accessory : plugin.accessoryRegistry().all()) {
            if (slot > 8) {
                break;
            }
            inventory.setItem(slot, unlocked ? accessory.createItem() : MenuStyle.lockedIcon());
            slot++;
        }

        inventory.setItem(LABEL_SLOT, label());

        AccessoryManager manager = plugin.accessoryManager();
        List<Accessory> equipped = manager.equipped(viewer);
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (i < equipped.size()) {
                inventory.setItem(EQUIP_SLOTS[i], equippedIcon(equipped.get(i)));
            } else {
                inventory.setItem(EQUIP_SLOTS[i], emptySlotIcon());
            }
        }

        ItemStack filler = MenuStyle.filler();
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /** Which equip index a raw slot corresponds to, or -1 if the slot isn't an equip slot. */
    public static int equipIndexOf(int rawSlot) {
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (EQUIP_SLOTS[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isCatalogSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot <= 8;
    }

    private static ItemStack equippedIcon(Accessory accessory) {
        ItemStack item = accessory.createItem();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("▶ Click to unequip", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack emptySlotIcon() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("◇ Empty Slot", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Click an accessory in your", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("inventory to equip it here.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack label() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Your Accessory Slots", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MenuStyle.border(NamedTextColor.GOLD),
                Component.text("Equip up to " + AccessoryManager.MAX_SLOTS + " accessories.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Their buffs apply while equipped.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                MenuStyle.border(NamedTextColor.GOLD)));
        item.setItemMeta(meta);
        return item;
    }

}
