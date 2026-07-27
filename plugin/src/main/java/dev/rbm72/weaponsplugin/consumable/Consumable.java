package dev.rbm72.weaponsplugin.consumable;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
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
 * A right-click support item that runs on charges instead of being eaten. Each one carries a small
 * pool of uses that refills on its own over time, plus a short cooldown between sips, so healing is
 * something you spend and ration mid-fight rather than a stack of golden apples you spam.
 * <p>
 * Charges live on the {@link ItemStack} itself (see {@link ConsumableCharges}), not on the player:
 * two vials in the same inventory each have their own pool, dropping one doesn't reset it, and a
 * relog changes nothing. The per-use cooldown is vanilla's own item cooldown on the item's material,
 * which the client already draws as the familiar grey sweep.
 * <p>
 * Deliberately built on non-edible, non-drinkable materials — a right-click on real food would race
 * this plugin's use handler against vanilla's eat animation.
 */
public abstract class Consumable {

    private static final String CONSUMABLE_ID_KEY = "consumable_id";

    protected final WeaponsPlugin plugin;

    protected Consumable(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Material material();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Human-readable lore lines describing what using it does. */
    public abstract List<Component> description();

    /** What actually happens on a successful use. Charges and cooldown are already handled by the caller. */
    public abstract void onUse(Player player);

    /** Charges a fresh item carries, and the ceiling the recharge timer refills back up to. */
    public int maxCharges() {
        return configInt("max-charges", 3);
    }

    /** Real time to regain one spent charge. Runs whether the item is held, stashed, or on the ground. */
    public double rechargeSeconds() {
        return configDouble("recharge-seconds", 45.0);
    }

    /** Minimum gap between two uses, independent of how many charges are banked. */
    public double useCooldownSeconds() {
        return configDouble("use-cooldown-seconds", 6.0);
    }

    public Sound useSound() {
        return Sound.ITEM_BOTTLE_FILL;
    }

    /** Played instead of {@link #useSound()} when the player is out of charges. */
    public Sound emptySound() {
        return Sound.BLOCK_NOTE_BLOCK_BASS;
    }

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("consumables." + id() + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("consumables." + id() + "." + key, def);
    }

    public final Component displayName() {
        return Component.text(displayNameText(), rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    /** A fresh item at full charges. */
    public final ItemStack createItem() {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(rarity().glint());
        meta.getPersistentDataContainer().set(idKey(plugin), PersistentDataType.STRING, id());
        item.setItemMeta(meta);

        ConsumableCharges.initialize(plugin, this, item);
        refreshLore(item);
        return item;
    }

    /**
     * Rewrites the tooltip to match the item's current charge state. Called after every use rather
     * than on a timer — a charge meter that only moves when you actually spend or the tooltip is
     * reopened is plenty, and rewriting item meta every tick for every stashed vial is not.
     */
    public final void refreshLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        int max = maxCharges();
        int charges = ConsumableCharges.effective(plugin, this, item);

        List<Component> body = new ArrayList<>();
        for (Component line : description()) {
            body.add(line.decoration(TextDecoration.ITALIC, false));
        }
        body.add(Component.empty());
        body.add(Component.text("✦ Charges  ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("●".repeat(charges), NamedTextColor.GREEN))
                .append(Component.text("○".repeat(Math.max(0, max - charges)), NamedTextColor.DARK_GRAY)));
        if (charges < max) {
            double next = ConsumableCharges.secondsToNextCharge(plugin, this, item);
            body.add(Component.text(String.format(Locale.ROOT, " ⟳ next charge in %.0fs", Math.ceil(next)),
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            body.add(Component.text(String.format(Locale.ROOT, " ⟳ %.0fs per charge", rechargeSeconds()),
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        body.add(Component.text(String.format(Locale.ROOT, " ⏱ %.1fs between uses", useCooldownSeconds()),
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));

        String footerLabel = rarity().label().toUpperCase(Locale.ROOT) + " CONSUMABLE";
        int frameWidth = Math.max(TooltipFrame.widestLine(body), TooltipFrame.footerWidth(footerLabel));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(body);
        lore.add(Component.empty());
        lore.add(TooltipFrame.footer(footerLabel, rarity().color(), frameWidth));
        meta.lore(lore);
        item.setItemMeta(meta);
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
        return new NamespacedKey(plugin, CONSUMABLE_ID_KEY);
    }
}
