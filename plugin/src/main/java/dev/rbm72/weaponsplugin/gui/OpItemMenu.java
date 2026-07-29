package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.opitem.OpItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The operator shelf: click an item to take one.
 * <p>
 * Unlike every other catalog in {@code gui/}, this one does not render a browsable, locked-icon version for
 * players without the permission. The other menus are advertising — a player who cannot pull a weapon out yet
 * still benefits from seeing it exists. An admin's god-potion and heart vessels are not something to advertise:
 * showing a locked row tells everyone the items exist and invites the ask. Non-holders get a plain "nothing
 * here" panel, and {@code MenuListener} re-checks the permission before honouring a click regardless.
 */
public final class OpItemMenu {

    public static final Component TITLE = Component.text("Operator Items", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final int SIZE = 27;
    /** Second row, so the shelf reads as a shelf rather than as items stuck to the top edge. */
    private static final int FIRST_SLOT = 10;
    private static final int LAST_SLOT = 16;
    private static final int NOTICE_SLOT = 13;

    private OpItemMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        OpItemMenuHolder holder = new OpItemMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, inventory);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, Inventory inventory) {
        inventory.clear();

        if (viewer.hasPermission("weaponsplugin.op")) {
            int slot = FIRST_SLOT;
            for (OpItem item : plugin.opItemRegistry().all()) {
                if (slot > LAST_SLOT) {
                    break;
                }
                inventory.setItem(slot, item.createItem());
                slot++;
            }
            inventory.setItem(SIZE - 1, help(plugin));
        } else {
            inventory.setItem(NOTICE_SLOT, empty());
        }

        ItemStack filler = MenuStyle.filler();
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    public static boolean isCatalogSlot(int rawSlot) {
        return rawSlot >= FIRST_SLOT && rawSlot <= LAST_SLOT;
    }

    private static ItemStack help(WeaponsPlugin plugin) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✎ Notes", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MenuStyle.border(NamedTextColor.GOLD),
                Component.text("Click an item to take one.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("These sit outside weapon balance", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("on purpose — /weaponbalance never", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("reports on them.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("/hearts — your bonus-heart tally,", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("add or remove at will (cap "
                        + plugin.heartManager().maxHearts() + ").", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                MenuStyle.border(NamedTextColor.GOLD)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack empty() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Nothing here", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
