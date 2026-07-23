package dev.rbm72.weaponsplugin.armor;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One wearable piece of an {@link ArmorSet}. Slots into a normal vanilla armor slot like any
 * other piece of gear; it only needs to look the part and carry an id — every actual effect
 * lives on the owning {@link ArmorSet}, keyed off how many of its pieces are currently worn.
 */
public abstract class ArmorPiece {

    private static final String ARMOR_ID_KEY = "armor_piece_id";

    protected final WeaponsPlugin plugin;
    private final ArmorSet set;
    private final ArmorSlot slot;

    protected ArmorPiece(WeaponsPlugin plugin, ArmorSet set, ArmorSlot slot) {
        this.plugin = plugin;
        this.set = set;
        this.slot = slot;
    }

    public abstract String id();

    public abstract Material material();

    /** Dye color applied when {@link #material()} is a leather piece; null (default) leaves it undyed. */
    protected Color leatherColor() {
        return null;
    }

    public final ArmorSet set() {
        return set;
    }

    public final ArmorSlot slot() {
        return slot;
    }

    public final Component displayName() {
        return Component.text(set.displayNameText() + " " + slot.label(), set.rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Rarity-colored horizontal rule framing the tooltip's set-bonus block and footer. */
    private Component borderLine() {
        return Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", set.rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    public final ItemStack createItem() {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName());

        if (meta instanceof LeatherArmorMeta leatherMeta && leatherColor() != null) {
            leatherMeta.setColor(leatherColor());
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(borderLine());
        for (Component line : set.description()) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("❈ Set Bonus — " + set.displayNameText(), NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        List<String> tiers = set.tierDescriptions();
        for (int i = 0; i < tiers.size(); i++) {
            int piecesWorn = i + 1;
            lore.add(Component.text(" " + piecesWorn + " pc  ", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(tiers.get(i), NamedTextColor.GRAY)));
        }

        lore.add(Component.empty());
        lore.add(borderLine());
        lore.add(Component.text("◆ " + set.rarity().label().toUpperCase(Locale.ROOT) + " ARMOR ◆", set.rarity().color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(borderLine());
        meta.lore(lore);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(set.rarity().glint());

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, ARMOR_ID_KEY), PersistentDataType.STRING, id());

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, ARMOR_ID_KEY), PersistentDataType.STRING);
        return id().equals(value);
    }

    public static NamespacedKey idKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, ARMOR_ID_KEY);
    }
}
