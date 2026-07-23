package dev.rbm72.weaponsplugin.accessory;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A wearable trinket that buffs the weapons a player wields while it sits in
 * one of their accessory slots. Mirrors {@link Weapon}'s shape: each concrete
 * accessory describes its icon/rarity/lore and overrides only the buff hooks
 * it actually uses. All hooks default to no-ops / neutral multipliers so an
 * accessory that only tweaks damage need not touch cooldowns or on-hit logic.
 */
public abstract class Accessory {

    private static final String ACCESSORY_ID_KEY = "accessory_id";

    protected final WeaponsPlugin plugin;

    protected Accessory(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Material material();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Human-readable lore lines describing what the accessory does. */
    public abstract List<Component> description();

    /**
     * Multiplier applied to damage the given weapon deals while this accessory
     * is equipped. 1.0 = no change. Targeted accessories return >1.0 only for
     * the weapons they care about.
     */
    public double damageMultiplier(Weapon weapon) {
        return 1.0;
    }

    /** Multiplier applied to the given weapon's ability cooldowns. 1.0 = no change, 0.8 = 20% shorter. */
    public double cooldownMultiplier(Weapon weapon) {
        return 1.0;
    }

    /** Fires twice per second for each equipped accessory. Drives passive buffs (e.g. Speed). */
    public void onEquipTick(Player player) {
    }

    /** Fires when the wielder lands a hit with a weapon while this accessory is equipped. */
    public void onWeaponHit(Player player, Weapon weapon, LivingEntity victim, EntityDamageByEntityEvent event) {
    }

    /**
     * Fires right after a weapon ability finishes executing in the given slot, whether or not this
     * accessory recognizes the weapon. This is how an accessory changes what an ability <em>does</em>
     * instead of just scaling a number: chaining a second cast, echoing it for free, adding a bonus
     * effect on top, etc. No-op by default.
     */
    public void onAbilityCast(Player player, Weapon weapon, Slot slot) {
    }

    /**
     * True if this accessory grants its own standalone active ability, independent of whichever
     * weapon is held. Triggered by double-tapping sneak (see {@code AccessoryPersonalAbilityListener}),
     * since all four right-click ability slots are already spoken for by weapons.
     */
    public boolean hasPersonalAbility() {
        return false;
    }

    /** Short header shown above {@link #personalAbilityLore()} in the tooltip. */
    public String personalAbilityName() {
        return "";
    }

    public List<Component> personalAbilityLore() {
        return List.of();
    }

    public double personalAbilityCooldownSeconds() {
        return 0;
    }

    /** Invoked on a successful double-sneak-tap while this accessory is equipped and off cooldown. */
    public void personalAbility(Player player) {
    }

    public final Component displayName() {
        return Component.text(displayNameText(), rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Rarity-colored horizontal rule framing the tooltip's ability block and footer. */
    private Component borderLine() {
        return Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    public final ItemStack createItem() {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(borderLine());
        for (Component line : description()) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }

        if (hasPersonalAbility()) {
            lore.add(Component.empty());
            String name = personalAbilityName();
            if (name != null && !name.isBlank()) {
                lore.add(Component.text("☆ " + name, rarity().color())
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
            }
            for (Component line : personalAbilityLore()) {
                lore.add(Component.text(" ").append(line.decoration(TextDecoration.ITALIC, false)));
            }
            lore.add(Component.text(" ↯ ", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("double-tap sneak", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true)));
            if (personalAbilityCooldownSeconds() > 0) {
                lore.add(Component.text(" ⏱ ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format(Locale.ROOT, "%.1fs", personalAbilityCooldownSeconds()), NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, true)));
            }
        }

        lore.add(Component.empty());
        lore.add(borderLine());
        lore.add(Component.text("◆ " + rarity().label().toUpperCase(Locale.ROOT) + " ACCESSORY ◆", rarity().color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(borderLine());
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(rarity().glint());

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, ACCESSORY_ID_KEY), PersistentDataType.STRING, id());

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, ACCESSORY_ID_KEY), PersistentDataType.STRING);
        return id().equals(value);
    }

    public static NamespacedKey idKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, ACCESSORY_ID_KEY);
    }
}
