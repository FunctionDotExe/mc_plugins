package dev.rbm72.weaponsplugin.items;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An off-hand shield: holding right-click raises it (vanilla {@link Material#SHIELD} blocking),
 * which {@code ShieldBlockListener} intercepts to apply this shield's damage reduction, or — if
 * the hit lands within {@link #parryWindowMs()} of the block starting — a full parry that cancels
 * the hit and stuns the attacker instead. Trades away a weapon's ability3/ultimate (both live on
 * whatever's in the off hand) for a defensive tool, same shape as {@link Weapon}/{@link
 * dev.rbm72.weaponsplugin.accessory.Accessory}: describe yourself, override only what differs.
 */
public abstract class Shield {

    private static final String SHIELD_ID_KEY = "shield_id";

    protected final WeaponsPlugin plugin;

    protected Shield(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Fraction of incoming damage blocked while raised, outside the parry window. 0.6 = takes 40%. */
    public double blockDamageReduction() {
        return configDouble("block-damage-reduction", 0.6);
    }

    /** Window after raising the shield during which a landed hit is a full parry instead of a block. */
    public long parryWindowMs() {
        return (long) configDouble("parry-window-ms", 350);
    }

    /** Ticks the parried attacker is slowed/fatigued for, punishing the whiffed attack. */
    public int parryStunTicks() {
        return (int) configDouble("parry-stun-ticks", 40);
    }

    /** Fraction of a blocked (non-parry) hit's raw damage reflected back onto the attacker. 0 = no reflect. */
    public double reflectFraction() {
        return configDouble("reflect-fraction", 0.0);
    }

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("shields." + id() + "." + key, def);
    }

    public final Component displayName() {
        return Component.text(displayNameText(), rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Rarity-colored horizontal rule framing the tooltip's stat block and footer. */
    private Component borderLine() {
        return Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    public final ItemStack createItem() {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(borderLine());
        lore.add(Component.text("⛨ Block  ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format(Locale.ROOT, "-%.0f%% damage", blockDamageReduction() * 100), NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, true)));
        lore.add(Component.text(" held right-click to raise", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        lore.add(Component.text("❋ Parry", rarity().color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" first ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format(Locale.ROOT, "%.1fs", parryWindowMs() / 1000.0), NamedTextColor.AQUA))
                .append(Component.text(" negates the hit and stuns the attacker", NamedTextColor.GRAY)));

        lore.add(Component.empty());
        lore.add(borderLine());
        lore.add(Component.text("◆ " + rarity().label().toUpperCase(Locale.ROOT) + " SHIELD ◆", rarity().color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(borderLine());
        meta.lore(lore);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(rarity().glint());

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, SHIELD_ID_KEY), PersistentDataType.STRING, id());

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, SHIELD_ID_KEY), PersistentDataType.STRING);
        return id().equals(value);
    }
}
