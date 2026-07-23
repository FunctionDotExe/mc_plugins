package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.armor.ArmorPiece;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Op-only catalog of every armor piece across every set: click one to grab a copy. Reachable only
 * from the hub's Armor button, which itself only renders for {@code weaponsplugin.give} holders —
 * this menu re-checks the permission on open/click anyway, same defense-in-depth as
 * {@link AccessoryMenu}'s catalog row.
 */
public final class ArmorMenu {

    public static final Component TITLE = Component.text("Armor (Op)", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final int SIZE = 36;

    private ArmorMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        ArmorMenuHolder holder = new ArmorMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, inventory);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, Inventory inventory) {
        inventory.clear();

        boolean unlocked = viewer.hasPermission("weaponsplugin.give");
        int slot = 0;
        for (ArmorPiece piece : plugin.armorRegistry().allPieces()) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, unlocked ? piece.createItem() : MenuStyle.lockedIcon());
            slot++;
        }

        ItemStack filler = MenuStyle.filler();
        for (int i = slot; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }
}
