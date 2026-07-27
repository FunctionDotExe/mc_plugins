package dev.rbm72.weaponsplugin.ridable;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.util.TooltipFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A saddle that lets its holder tame and ride every live mob of {@link #targetEntityType()} found
 * in the world. Mirrors {@link dev.rbm72.weaponsplugin.accessory.Accessory}'s shape: each concrete
 * saddle describes its icon/rarity/lore and the mob-specific active ability its rider gets while
 * mounted.
 */
public abstract class Ridable {

    private static final String RIDABLE_ID_KEY = "ridable_id";

    protected final WeaponsPlugin plugin;

    protected Ridable(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Material material();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** The mob type this saddle lets its holder mount. */
    public abstract EntityType targetEntityType();

    /** Human-readable lore lines describing the mob and how riding it feels. */
    public abstract List<Component> description();

    public abstract String abilityName();

    public abstract List<Component> abilityLore();

    public abstract double abilityCooldownSeconds();

    /** Whether this mob's steering is fully custom (ground/flight) vs. left to vanilla (Strider). */
    public boolean customSteering() {
        return true;
    }

    /** Whether this mob should have its gravity disabled while ridden (fliers). */
    public boolean flies() {
        return false;
    }

    /**
     * Degrees added to the rider's yaw before it's applied to the mount each tick. 0 for everything
     * except the Ender Dragon: its body/head model faces backwards relative to its own reported yaw
     * (a long-standing Minecraft quirk), so without the 180° correction the rider ends up staring
     * down the tail instead of the head.
     */
    public double yawOffsetDegrees() {
        return 0;
    }

    /** Invoked when the rider triggers their mount's active ability, off cooldown. */
    public abstract void ability(Player rider, LivingEntity mount);

    public final Component displayName() {
        return Component.text(displayNameText(), rarity().color())
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
        body.add(Component.text("☆ " + abilityName(), rarity().color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        for (Component line : abilityLore()) {
            body.add(Component.text(" ").append(line.decoration(TextDecoration.ITALIC, false)));
        }
        body.add(Component.text(" ↯ ", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("right-click while mounted", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true)));
        body.add(Component.text(" ⏱ ", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format(Locale.ROOT, "%.1fs", abilityCooldownSeconds()), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true)));

        String footerLabel = rarity().label().toUpperCase(Locale.ROOT) + " RIDABLE";
        int frameWidth = Math.max(TooltipFrame.widestLine(body), TooltipFrame.footerWidth(footerLabel));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(body);
        lore.add(Component.empty());
        lore.add(TooltipFrame.footer(footerLabel, rarity().color(), frameWidth));
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(rarity().glint());

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, RIDABLE_ID_KEY), PersistentDataType.STRING, id());

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, RIDABLE_ID_KEY), PersistentDataType.STRING);
        return id().equals(value);
    }

    public static NamespacedKey idKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, RIDABLE_ID_KEY);
    }
}
