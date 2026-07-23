package dev.rbm72.weaponsplugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Shared look-and-feel for every catalog/menu screen — filler panes, locked icons, and
 * pagination controls — so the six menus in {@code gui/} read as one consistent interface
 * instead of each hand-rolling (and subtly drifting from) its own copy.
 */
public final class MenuStyle {

    private MenuStyle() {
    }

    /** Rarity-tooltip-style horizontal rule, reused here to tie menu lore to item lore. */
    public static Component border(NamedTextColor color) {
        return Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬", color)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack lockedIcon() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Locked", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                border(NamedTextColor.DARK_GRAY),
                Component.text("You don't have permission for this.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                border(NamedTextColor.DARK_GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack prevPageButton(NamespacedKey pageDeltaKey) {
        return pageButton(pageDeltaKey, -1, "❮ Previous Page");
    }

    public static ItemStack nextPageButton(NamespacedKey pageDeltaKey) {
        return pageButton(pageDeltaKey, 1, "Next Page ❯");
    }

    private static ItemStack pageButton(NamespacedKey pageDeltaKey, int delta, String label) {
        ItemStack item = new ItemStack(delta > 0 ? Material.SPECTRAL_ARROW : Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(pageDeltaKey, PersistentDataType.INTEGER, delta);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack pageIndicator(int page, int totalPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Page " + (page + 1) + " / " + totalPages, NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
