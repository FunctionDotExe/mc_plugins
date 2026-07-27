package dev.rbm72.weaponsplugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The hub reached from the nether star: ender chest and accessories for everyone, plus an
 * op-only Armor and Shields catalog. The two op buttons only render for
 * {@code weaponsplugin.give} holders — everyone else sees plain filler in those slots, and
 * {@code MenuListener} re-checks the permission before honoring a click there regardless.
 */
public final class HubMenu {

    public static final Component TITLE = Component.text("Menu", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);
    private static final int SIZE = 36;
    public static final int ENDER_CHEST_SLOT = 10;
    public static final int ACCESSORIES_SLOT = 12;
    public static final int STONES_SLOT = 14;
    public static final int RIDABLES_SLOT = 16;
    public static final int ARMOR_SLOT = 19;
    public static final int SHIELDS_SLOT = 21;
    public static final int BOSSES_SLOT = 23;
    public static final int REALMS_SLOT = 25;
    public static final int CONSUMABLES_SLOT = 30;
    public static final int WEAPONS_SLOT = 32;

    private HubMenu() {
    }

    public static Inventory open(Player viewer) {
        HubMenuHolder holder = new HubMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        inventory.setItem(ENDER_CHEST_SLOT, button(Material.ENDER_CHEST, NamedTextColor.LIGHT_PURPLE,
                "⛃ Ender Chest", false,
                List.of(Component.text("Open your personal ender chest.", NamedTextColor.GRAY))));
        inventory.setItem(ACCESSORIES_SLOT, button(Material.AMETHYST_SHARD, NamedTextColor.AQUA,
                "❋ Accessories", false,
                List.of(
                        Component.text("Equip accessories that buff", NamedTextColor.GRAY),
                        Component.text("your weapons.", NamedTextColor.GRAY))));
        inventory.setItem(STONES_SLOT, button(Material.BREEZE_ROD, NamedTextColor.LIGHT_PURPLE,
                "✦ Movement Stones", false,
                List.of(
                        Component.text("Socket stones for double jumps,", NamedTextColor.GRAY),
                        Component.text("dashes, wall runs, and more.", NamedTextColor.GRAY))));
        inventory.setItem(RIDABLES_SLOT, button(Material.SADDLE, NamedTextColor.GREEN,
                "🐴 Ridables", false,
                List.of(
                        Component.text("Equip a saddle to tame and ride", NamedTextColor.GRAY),
                        Component.text("its matching mob in the world.", NamedTextColor.GRAY))));
        inventory.setItem(REALMS_SLOT, button(Material.CHORUS_FRUIT, NamedTextColor.LIGHT_PURPLE,
                "✧ Realms", false,
                List.of(
                        Component.text("Grab a Realm Crystal — right-click to", NamedTextColor.GRAY),
                        Component.text("step into that boss's own dimension.", NamedTextColor.GRAY))));

        inventory.setItem(CONSUMABLES_SLOT, button(Material.GHAST_TEAR, NamedTextColor.GREEN,
                "✚ Consumables", false,
                List.of(
                        Component.text("Healing items that run on charges", NamedTextColor.GRAY),
                        Component.text("and refill themselves over time.", NamedTextColor.GRAY))));
        inventory.setItem(WEAPONS_SLOT, button(Material.NETHERITE_SWORD, NamedTextColor.RED,
                "⚔ Weapons", false,
                List.of(
                        Component.text("Browse every weapon, filtered", NamedTextColor.GRAY),
                        Component.text("by rarity. Same as /weapons.", NamedTextColor.GRAY))));

        if (viewer.hasPermission("weaponsplugin.give")) {
            inventory.setItem(ARMOR_SLOT, button(Material.NETHERITE_CHESTPLATE, NamedTextColor.GOLD,
                    "⛨ Armor (Op)", true,
                    List.of(Component.text("Browse and grab any armor piece.", NamedTextColor.GRAY))));
            inventory.setItem(SHIELDS_SLOT, button(Material.SHIELD, NamedTextColor.GOLD,
                    "❁ Shields (Op)", true,
                    List.of(Component.text("Browse and grab any shield.", NamedTextColor.GRAY))));
        }
        if (viewer.hasPermission("weaponsplugin.boss.spawn")) {
            inventory.setItem(BOSSES_SLOT, button(Material.WITHER_SKELETON_SKULL, NamedTextColor.GOLD,
                    "☠ Bosses (Op)", true,
                    List.of(Component.text("Browse every boss and spawn one", NamedTextColor.GRAY),
                            Component.text("at your location.", NamedTextColor.GRAY))));
        }

        ItemStack filler = MenuStyle.filler();
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
        return inventory;
    }

    private static ItemStack button(Material material, NamedTextColor color, String name, boolean glint, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> fullLore = new ArrayList<>();
        fullLore.add(MenuStyle.border(color));
        lore.forEach(line -> fullLore.add(line.decoration(TextDecoration.ITALIC, false)));
        fullLore.add(MenuStyle.border(color));
        meta.lore(fullLore);

        if (glint) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }
}
