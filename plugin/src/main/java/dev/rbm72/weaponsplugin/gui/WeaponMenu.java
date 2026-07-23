package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * A 2-row menu: top row is the weapon grid for the current filter (paginated,
 * 36 per page), bottom row is the rarity filter bar plus prev/next page
 * buttons. Weapon icons are the real items (via {@link Weapon#createItem()})
 * so their lore/stats never drift from what `/giveweapon` actually hands out.
 */
public final class WeaponMenu {

    public static final Component TITLE = Component.text("Weapons", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);
    private static final int SIZE = 54;
    private static final int[] FILTER_SLOTS = {36, 37, 38, 39, 40, 41};
    private static final int PAGE_SIZE = 36;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int PAGE_INDICATOR_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;

    private static NamespacedKey filterKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, "menu_filter");
    }

    private static NamespacedKey pageDeltaKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, "menu_page_delta");
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        WeaponMenuHolder holder = new WeaponMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, holder);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, WeaponMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();

        boolean unlocked = viewer.hasPermission("weaponsplugin.give");
        Rarity filter = holder.filter();

        List<Weapon> filtered = new ArrayList<>();
        for (Weapon weapon : plugin.weaponRegistry().all()) {
            if (filter == null || weapon.rarity() == filter) {
                filtered.add(weapon);
            }
        }

        int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(holder.page(), totalPages - 1));
        holder.setPage(page);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, unlocked ? filtered.get(i).createItem() : MenuStyle.lockedIcon());
        }

        Rarity[] rarities = Rarity.values();
        for (int i = 0; i < FILTER_SLOTS.length; i++) {
            Rarity buttonRarity = i == 0 ? null : rarities[i - 1];
            inventory.setItem(FILTER_SLOTS[i], filterButton(plugin, buttonRarity, buttonRarity == filter));
        }

        ItemStack filler = MenuStyle.filler();
        for (int i = 42; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(PREV_PAGE_SLOT, page > 0 ? MenuStyle.prevPageButton(pageDeltaKey(plugin)) : filler);
        inventory.setItem(PAGE_INDICATOR_SLOT, MenuStyle.pageIndicator(page, totalPages));
        inventory.setItem(NEXT_PAGE_SLOT, page < totalPages - 1 ? MenuStyle.nextPageButton(pageDeltaKey(plugin)) : filler);
    }

    public static Rarity readFilter(WeaponsPlugin plugin, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) {
            return null;
        }
        String value = clicked.getItemMeta().getPersistentDataContainer()
                .get(filterKey(plugin), PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        return "ALL".equals(value) ? null : Rarity.valueOf(value);
    }

    public static boolean isFilterButton(WeaponsPlugin plugin, ItemStack clicked) {
        return clicked != null && clicked.hasItemMeta()
                && clicked.getItemMeta().getPersistentDataContainer().has(filterKey(plugin), PersistentDataType.STRING);
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

    private static ItemStack filterButton(WeaponsPlugin plugin, Rarity rarity, boolean active) {
        Material material = rarity == null ? Material.NETHER_STAR : woolFor(rarity);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String label = rarity == null ? "All Weapons" : rarity.name().charAt(0) + rarity.name().substring(1).toLowerCase();
        NamedTextColor color = rarity == null ? NamedTextColor.WHITE : rarity.color();
        meta.displayName(Component.text((active ? "» " : "") + label + (active ? " «" : ""), color)
                .decoration(TextDecoration.BOLD, active)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MenuStyle.border(color),
                Component.text(active ? "Currently selected" : "Click to filter", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));

        meta.getPersistentDataContainer().set(filterKey(plugin), PersistentDataType.STRING,
                rarity == null ? "ALL" : rarity.name());

        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Material woolFor(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> Material.WHITE_WOOL;
            case RARE -> Material.LIGHT_BLUE_WOOL;
            case EPIC -> Material.PURPLE_WOOL;
            case LEGENDARY -> Material.YELLOW_WOOL;
            case MYTHIC -> Material.RED_WOOL;
        };
    }
}
