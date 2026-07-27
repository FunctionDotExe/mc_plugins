package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.consumable.Consumable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Catalog of every healing consumable: click one to take a copy at full charges. Same
 * op-gated-grab shape as {@link StoneMenu}'s catalog row — everyone can browse and read what the
 * items do, only {@code weaponsplugin.give} holders can pull one out.
 */
public final class ConsumableMenu {

    public static final Component TITLE = Component.text("Consumables", NamedTextColor.GREEN, TextDecoration.BOLD);
    private static final int SIZE = 27;

    private ConsumableMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        ConsumableMenuHolder holder = new ConsumableMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, inventory);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, Inventory inventory) {
        inventory.clear();

        boolean unlocked = viewer.hasPermission("weaponsplugin.give");
        int slot = 0;
        for (Consumable consumable : plugin.consumableRegistry().all()) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, unlocked ? consumable.createItem() : MenuStyle.lockedIcon());
            slot++;
        }

        ItemStack filler = MenuStyle.filler();
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
}
