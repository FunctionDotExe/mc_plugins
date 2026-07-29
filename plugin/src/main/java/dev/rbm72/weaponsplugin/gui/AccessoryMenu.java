package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Accessory screen: the top row is a paginated catalog (ops click to grab a copy, like the weapon
 * menu — the roster long since outgrew a single fixed row), the bottom row holds the prev/next page
 * controls, and the middle band holds the player's equip slots. Clicking an accessory in your own
 * inventory equips it; clicking an equipped one unequips.
 */
public final class AccessoryMenu {

    public static final Component TITLE = Component.text("Accessories", NamedTextColor.AQUA, TextDecoration.BOLD);
    private static final int SIZE = 54;
    private static final int LABEL_SLOT = 19;
    /** The player's equip slots, one per {@link AccessoryManager#MAX_SLOTS}. */
    public static final int[] EQUIP_SLOTS = {20, 21, 22, 23, 24};

    private static final int PAGE_SIZE = 9;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int PAGE_INDICATOR_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;

    private AccessoryMenu() {
    }

    private static NamespacedKey pageDeltaKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, "accessory_menu_page_delta");
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        AccessoryMenuHolder holder = new AccessoryMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, holder);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, AccessoryMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();

        boolean unlocked = viewer.hasPermission("weaponsplugin.give");
        List<Accessory> all = new ArrayList<>(plugin.accessoryRegistry().all());

        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(holder.page(), totalPages - 1));
        holder.setPage(page);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, unlocked ? all.get(i).createItem() : MenuStyle.lockedIcon());
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

        inventory.setItem(PREV_PAGE_SLOT, page > 0 ? MenuStyle.prevPageButton(pageDeltaKey(plugin)) : filler);
        inventory.setItem(PAGE_INDICATOR_SLOT, MenuStyle.pageIndicator(page, totalPages));
        inventory.setItem(NEXT_PAGE_SLOT, page < totalPages - 1 ? MenuStyle.nextPageButton(pageDeltaKey(plugin)) : filler);
    }

    public static boolean isPageButton(WeaponsPlugin plugin, ItemStack clicked) {
        return clicked != null && clicked.hasItemMeta()
                && clicked.getItemMeta().getPersistentDataContainer().has(pageDeltaKey(plugin), PersistentDataType.INTEGER);
    }

    public static int readPageDelta(WeaponsPlugin plugin, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) {
            return 0;
        }
        Integer delta = clicked.getItemMeta().getPersistentDataContainer()
                .get(pageDeltaKey(plugin), PersistentDataType.INTEGER);
        return delta == null ? 0 : delta;
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
