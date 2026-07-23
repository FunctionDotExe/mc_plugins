package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Shield;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Op-only catalog of every shield: click one to grab a copy. Reachable only from the hub's
 * Shields button, which itself only renders for {@code weaponsplugin.give} holders — this menu
 * re-checks the permission on open/click anyway, same defense-in-depth as {@link ArmorMenu}.
 */
public final class ShieldMenu {

    public static final Component TITLE = Component.text("Shields (Op)", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final int SIZE = 9;

    private ShieldMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        ShieldMenuHolder holder = new ShieldMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, inventory);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, Inventory inventory) {
        inventory.clear();

        boolean unlocked = viewer.hasPermission("weaponsplugin.give");
        int slot = 0;
        for (Shield shield : plugin.shieldRegistry().all()) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, unlocked ? shield.createItem() : MenuStyle.lockedIcon());
            slot++;
        }
    }
}
