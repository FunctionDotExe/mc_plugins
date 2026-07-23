package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The nether star that lives in every player's 9th hotbar slot. Right-clicking
 * it opens the hub menu. A PDC tag marks it so the lock listener can keep it
 * pinned to its slot and re-hand it out when it goes missing.
 */
public final class HubItem {

    /** Hotbar index the star is pinned to (0-8 left to right; 8 is the 9th slot). */
    public static final int SLOT = 8;
    private static final String HUB_ITEM_KEY = "hub_item";

    private HubItem() {
    }

    public static NamespacedKey key(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, HUB_ITEM_KEY);
    }

    public static boolean isHubItem(WeaponsPlugin plugin, ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    public static ItemStack create(WeaponsPlugin plugin) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Menu", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click to open your menu.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Ender chest & accessories inside.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Ensures the player has exactly one hub item and it sits in {@link #SLOT}. Safe to call repeatedly. */
    public static void ensure(WeaponsPlugin plugin, Player player) {
        // Strip any stray copies elsewhere, then guarantee the pinned slot holds one.
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i != SLOT && isHubItem(plugin, contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        ItemStack pinned = player.getInventory().getItem(SLOT);
        if (!isHubItem(plugin, pinned)) {
            // Push whatever occupies the slot elsewhere so we never destroy a real item.
            if (pinned != null && !pinned.getType().isAir()) {
                player.getInventory().addItem(pinned);
            }
            player.getInventory().setItem(SLOT, create(plugin));
        }
    }
}
