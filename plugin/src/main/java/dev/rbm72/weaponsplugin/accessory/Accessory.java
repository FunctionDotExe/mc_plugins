package dev.rbm72.weaponsplugin.accessory;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.util.TooltipFrame;
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

    /**
     * True if this accessory cancels knockback applied to its wearer — the shove from a hit, not the
     * hit itself. The damage still lands unchanged; only the resulting velocity is suppressed.
     * <p>
     * The camera jolt is a <em>separate</em> thing riding the damage event, not the knockback, so
     * suppressing it needs its own pass: {@code AccessoryKnockbackListener} takes it off recurring
     * environmental ticks for the wearer, and leaves it on real hits on purpose.
     */
    public boolean negatesKnockback() {
        return false;
    }

    /**
     * True if this accessory takes the client's hurt tilt off <em>recurring</em> damage for its wearer —
     * environmental ticks, and anything hitting them repeatedly from the same cause. The health still
     * comes off, unreduced; only the camera roll goes. A single telegraphed hit keeps its tilt, because
     * that one is feedback the player wants (and because the vanilla pipeline is also what pays armor
     * durability and shield wear).
     */
    public boolean negatesTickFlinch() {
        return false;
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

    public final ItemStack createItem() {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName());

        List<Component> body = new ArrayList<>();
        for (Component line : description()) {
            body.add(line.decoration(TextDecoration.ITALIC, false));
        }

        if (hasPersonalAbility()) {
            body.add(Component.empty());
            String name = personalAbilityName();
            if (name != null && !name.isBlank()) {
                body.add(Component.text("☆ " + name, rarity().color())
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
            }
            for (Component line : personalAbilityLore()) {
                body.add(Component.text(" ").append(line.decoration(TextDecoration.ITALIC, false)));
            }
            body.add(Component.text(" ↯ ", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("double-tap sneak", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true)));
            if (personalAbilityCooldownSeconds() > 0) {
                body.add(Component.text(" ⏱ ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format(Locale.ROOT, "%.1fs", personalAbilityCooldownSeconds()), NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, true)));
            }
        }

        String footerLabel = rarity().label().toUpperCase(Locale.ROOT) + " ACCESSORY";
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
