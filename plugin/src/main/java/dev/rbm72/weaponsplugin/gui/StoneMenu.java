package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.stone.Stone;
import dev.rbm72.weaponsplugin.stone.StoneManager;
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
 * Movement stone screen: same shape as {@link AccessoryMenu} (catalog row up top, equip slots in
 * the middle band) but its own dedicated slots — stones don't compete with accessory slots for
 * room, so socketing a mobility stone never costs you a damage/utility accessory.
 */
public final class StoneMenu {

    public static final Component TITLE = Component.text("Movement Stones", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
    private static final int SIZE = 45;
    private static final int LABEL_SLOT = 22;
    /**
     * Last catalog slot. Two full rows rather than one: the roster outgrew nine stones, and the old
     * single-row cap silently stopped rendering at the ninth — a registered stone nobody could see or take.
     */
    private static final int LAST_CATALOG_SLOT = 17;
    /** The player's equip slots, one per {@link StoneManager#MAX_SLOTS}. */
    public static final int[] EQUIP_SLOTS = {30, 31, 32};

    private StoneMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        StoneMenuHolder holder = new StoneMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, inventory);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, Inventory inventory) {
        inventory.clear();

        boolean unlocked = viewer.hasPermission("weaponsplugin.give");
        int slot = 0;
        for (Stone stone : plugin.stoneRegistry().all()) {
            if (slot > LAST_CATALOG_SLOT) {
                break;
            }
            inventory.setItem(slot, unlocked ? stone.createItem() : MenuStyle.lockedIcon());
            slot++;
        }

        inventory.setItem(LABEL_SLOT, label());

        StoneManager manager = plugin.stoneManager();
        List<Stone> equipped = manager.equipped(viewer);
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
        return rawSlot >= 0 && rawSlot <= LAST_CATALOG_SLOT;
    }

    private static ItemStack equippedIcon(Stone stone) {
        ItemStack item = stone.createItem();
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
        meta.displayName(Component.text("◇ Empty Socket", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Click a stone in your", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("inventory to socket it here.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack label() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Your Movement Stones", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MenuStyle.border(NamedTextColor.GOLD),
                Component.text("Socket up to " + StoneManager.MAX_SLOTS + " stones.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Their movement perks apply", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("while socketed.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                MenuStyle.border(NamedTextColor.GOLD)));
        item.setItemMeta(meta);
        return item;
    }
}
