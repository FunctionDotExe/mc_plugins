package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.realm.Realm;
import dev.rbm72.weaponsplugin.realm.RealmCrystalItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The realm catalog: one crystal per registered realm, click to receive a copy. Eating the crystal
 * (handled by {@code RealmListener}) is what actually carries the player into that realm's arena.
 */
public final class RealmsMenu {

    public static final Component TITLE = Component.text("Realms", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
    private static final int SIZE = 27;

    private RealmsMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        RealmsMenuHolder holder = new RealmsMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        int slot = 0;
        for (Realm realm : plugin.realmRegistry().all()) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot++, icon(plugin, realm));
        }

        ItemStack filler = MenuStyle.filler();
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
        return inventory;
    }

    private static ItemStack icon(WeaponsPlugin plugin, Realm realm) {
        ItemStack item = RealmCrystalItem.create(plugin, realm);
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(MenuStyle.border(NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.text("Click to receive one.", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(MenuStyle.border(NamedTextColor.LIGHT_PURPLE));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }
}
