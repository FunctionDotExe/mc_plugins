package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.opitem.OpItem;
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
 * The operator shelf: click an item to take one, prev/next along the bottom once the shelf runs past one row.
 * <p>
 * Unlike every other catalog in {@code gui/}, this one does not render a browsable, locked-icon version for
 * players without the permission. The other menus are advertising — a player who cannot pull a weapon out yet
 * still benefits from seeing it exists. An admin's god-potion and heart vessels are not something to advertise:
 * showing a locked row tells everyone the items exist and invites the ask. Non-holders get a plain "nothing
 * here" panel and no page controls at all, and {@code MenuListener} re-checks the permission before honouring
 * a click regardless.
 * <p>
 * Paging is kept to a single 7-wide row rather than widened to a grid on purpose: the shelf reading as a shelf
 * is the whole visual point, and a second page is a cheaper way to hold that than a wall of bottles.
 */
public final class OpItemMenu {

    public static final Component TITLE = Component.text("Operator Items", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final int SIZE = 27;
    /** Second row, so the shelf reads as a shelf rather than as items stuck to the top edge. */
    private static final int FIRST_SLOT = 10;
    private static final int LAST_SLOT = 16;
    private static final int PAGE_SIZE = LAST_SLOT - FIRST_SLOT + 1;
    private static final int NOTICE_SLOT = 13;
    private static final int NOTES_SLOT = 8;
    private static final int PREV_PAGE_SLOT = 18;
    private static final int PAGE_INDICATOR_SLOT = 22;
    private static final int NEXT_PAGE_SLOT = 26;

    private OpItemMenu() {
    }

    private static NamespacedKey pageDeltaKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, "op_item_menu_page_delta");
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        OpItemMenuHolder holder = new OpItemMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, holder);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, OpItemMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();

        ItemStack filler = MenuStyle.filler();

        if (!viewer.hasPermission("weaponsplugin.op")) {
            inventory.setItem(NOTICE_SLOT, empty());
            fill(inventory, filler);
            return;
        }

        List<OpItem> all = new ArrayList<>(plugin.opItemRegistry().all());
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(holder.page(), totalPages - 1));
        holder.setPage(page);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(FIRST_SLOT + (i - start), all.get(i).createItem());
        }

        inventory.setItem(NOTES_SLOT, help(plugin));
        fill(inventory, filler);

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

    public static boolean isCatalogSlot(int rawSlot) {
        return rawSlot >= FIRST_SLOT && rawSlot <= LAST_SLOT;
    }

    private static void fill(Inventory inventory, ItemStack filler) {
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
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
                Component.text("Endless bottles toggle: drink one", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("again to lift it, no bottle spent.", NamedTextColor.DARK_GRAY)
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
