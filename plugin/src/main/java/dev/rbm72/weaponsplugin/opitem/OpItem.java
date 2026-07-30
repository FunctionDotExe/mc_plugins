package dev.rbm72.weaponsplugin.opitem;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.util.TooltipFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A right-click item that is openly, deliberately overpowered — the operator shelf, kept apart from the
 * balanced roster on purpose.
 * <p>
 * Every other item family in this plugin ({@link dev.rbm72.weaponsplugin.items.Weapon},
 * {@link dev.rbm72.weaponsplugin.consumable.Consumable}) exists inside a balance conversation: cooldowns,
 * charges, counterplay, a damage table that {@code /weaponbalance} audits. These do not, and mixing them into
 * those registries would quietly poison that conversation — an admin's god-potion showing up in the
 * consumables catalog next to a 3-charge heal makes both harder to reason about, and the balance sheet would
 * be reporting on an item nobody is meant to balance.
 * <p>
 * So the family is separate, its catalog is permission-gated at the menu <em>and</em> at the click, and the
 * tooltip says {@code OPERATOR ITEM} rather than a rarity label. What an op item does is spelled out in full
 * on the item: these are handed out by someone with a reason, and that reason is easier to hold if the effect
 * is not a surprise.
 * <p>
 * Deliberately no charge pool and no cooldown. A charge economy is what makes a support item a decision;
 * these are not decisions, they are grants, and one is consumed per use so the grant has a countable size.
 */
public abstract class OpItem {

    private static final String OP_ITEM_ID_KEY = "op_item_id";

    protected final WeaponsPlugin plugin;

    protected OpItem(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Material material();

    public abstract String displayNameText();

    /** Human-readable lore lines saying exactly what using it does. */
    public abstract List<Component> description();

    /**
     * What using it does. Returns false if nothing happened — a heart vessel at the cap, for instance — so the
     * caller knows not to spend the item for no effect.
     */
    public abstract boolean onUse(Player player);

    /** Colour of the item's name and tooltip frame. Not a balance tier: an op item has no rarity. */
    public NamedTextColor accentColor() {
        return NamedTextColor.GOLD;
    }

    /** True if one is taken out of the stack on a successful use. */
    public boolean consumedOnUse() {
        return true;
    }

    public Sound useSound() {
        return Sound.BLOCK_BEACON_ACTIVATE;
    }

    /** Per-item hook for meta only this item understands — potion colour, custom model, and so on. */
    protected void decorate(ItemMeta meta) {
    }

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("op-items." + id() + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("op-items." + id() + "." + key, def);
    }

    public final Component displayName() {
        return Component.text(displayNameText(), accentColor())
                .decoration(TextDecoration.ITALIC, false);
    }

    public final ItemStack createItem() {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName());

        List<Component> body = new ArrayList<>();
        for (Component line : description()) {
            body.add(line.decoration(TextDecoration.ITALIC, false));
        }
        body.add(Component.empty());
        body.add(Component.text("↯ right-click to use", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        if (consumedOnUse()) {
            body.add(Component.text("✖ consumed on use", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }

        String footerLabel = "OPERATOR ITEM";
        int frameWidth = Math.max(TooltipFrame.widestLine(body), TooltipFrame.footerWidth(footerLabel));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(body);
        lore.add(Component.empty());
        lore.add(TooltipFrame.footer(footerLabel, accentColor(), frameWidth));
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(idKey(plugin), PersistentDataType.STRING, id());
        decorate(meta);

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(idKey(plugin), PersistentDataType.STRING);
        return id().equals(value);
    }

    public static NamespacedKey idKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, OP_ITEM_ID_KEY);
    }

    /** Amplifier rendered the way the game writes it — amplifier 1 is "II". */
    protected static String roman(int amplifier) {
        return switch (amplifier) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            default -> String.valueOf(amplifier + 1);
        };
    }

    /** Ticks as the tooltip says them: "6m 00s" past a minute, "45s" below it. */
    protected static String formatDuration(int ticks) {
        int seconds = ticks / 20;
        return seconds >= 60
                ? String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60)
                : seconds + "s";
    }
}
