package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ridable.Ridable;
import dev.rbm72.weaponsplugin.ridable.RidableManager;
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
import java.util.Optional;

/**
 * Ridables screen: the top row is a catalog (ops click to grab a saddle), one slot holds the
 * player's equipped saddle. Whichever saddle is equipped determines which live mob they can
 * right-click to tame and ride, per {@link Ridable#targetEntityType()}.
 */
public final class RidableMenu {

    public static final Component TITLE = Component.text("Ridables", NamedTextColor.GREEN, TextDecoration.BOLD);
    private static final int SIZE = 54;
    private static final int LABEL_SLOT = 19;
    public static final int EQUIP_SLOT = 22;

    private RidableMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        RidableMenuHolder holder = new RidableMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, inventory);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, Inventory inventory) {
        inventory.clear();

        boolean unlocked = viewer.hasPermission("weaponsplugin.give");
        int slot = 0;
        for (Ridable ridable : plugin.ridableRegistry().all()) {
            if (slot > 8) {
                break;
            }
            inventory.setItem(slot, unlocked ? ridable.createItem() : MenuStyle.lockedIcon());
            slot++;
        }

        inventory.setItem(LABEL_SLOT, label());

        RidableManager manager = plugin.ridableManager();
        Optional<Ridable> equipped = manager.equipped(viewer);
        inventory.setItem(EQUIP_SLOT, equipped.isPresent() ? equippedIcon(equipped.get()) : emptySlotIcon());

        ItemStack filler = MenuStyle.filler();
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    public static boolean isEquipSlot(int rawSlot) {
        return rawSlot == EQUIP_SLOT;
    }

    public static boolean isCatalogSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot <= 8;
    }

    private static ItemStack equippedIcon(Ridable ridable) {
        ItemStack item = ridable.createItem();
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
        meta.lore(List.of(Component.text("Click a saddle in your", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("inventory to equip it here.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack label() {
        ItemStack item = new ItemStack(Material.SADDLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Your Ridable Slot", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MenuStyle.border(NamedTextColor.GOLD),
                Component.text("Equip a saddle, then right-click", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("the matching mob to tame and ride it.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                MenuStyle.border(NamedTextColor.GOLD)));
        item.setItemMeta(meta);
        return item;
    }
}
