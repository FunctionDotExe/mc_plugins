package dev.rbm72.weaponsplugin.stone;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.util.TooltipFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A socketed movement stone that grants a mechanical mobility perk while equipped in one of a
 * player's stone slots (see {@link StoneManager}). Mirrors {@link dev.rbm72.weaponsplugin.accessory.Accessory}'s
 * shape but drops the weapon-buff hooks that don't apply here — stones only ever affect the
 * wielder's own movement, either passively ({@link #onEquipTick}) or via the same double-tap-sneak
 * personal-ability gesture accessories use.
 */
public abstract class Stone {

    private static final String STONE_ID_KEY = "stone_id";

    protected final WeaponsPlugin plugin;

    protected Stone(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Material material();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Human-readable lore lines describing what the stone does. */
    public abstract List<Component> description();

    /** Fires twice per second for each equipped stone. Drives passive movement buffs. */
    public void onEquipTick(Player player) {
    }

    /** True if this stone grants a standalone active ability, triggered by double-tapping sneak. */
    public boolean hasPersonalAbility() {
        return false;
    }

    public String personalAbilityName() {
        return "";
    }

    public List<Component> personalAbilityLore() {
        return List.of();
    }

    public double personalAbilityCooldownSeconds() {
        return 0;
    }

    /** Invoked on a successful double-sneak-tap while this stone is equipped and off cooldown. */
    public void personalAbility(Player player) {
    }

    /**
     * True if this stone has cooldown/charge state worth surfacing on the action bar. Defaults to
     * {@link #hasPersonalAbility()}; override independently for a stone whose active ability
     * doesn't use the double-tap-sneak trigger (e.g. {@link dev.rbm72.weaponsplugin.stone.stones.SkyleapStone}'s
     * double-tap-space jump), since that flag also controls the sneak-trigger tooltip/dispatch.
     */
    public boolean showsCooldownStatus() {
        return hasPersonalAbility();
    }

    /**
     * Action-bar text shown continuously while {@link #showsCooldownStatus()} and equipped — see
     * {@code StoneCooldownDisplayTask}. Default renders the manager-tracked cooldown ("Name 3.2s" /
     * "Name READY"); override when a stone's real gating isn't a simple timer (e.g.
     * {@link dev.rbm72.weaponsplugin.stone.stones.WindrunnerStone}'s charge count).
     */
    public Component actionBarStatus(Player player, boolean onCooldown, double remainingSeconds) {
        return displayName().append(Component.text(
                onCooldown ? String.format(Locale.ROOT, " %.1fs", remainingSeconds) : " READY",
                onCooldown ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
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

        String footerLabel = rarity().label().toUpperCase(Locale.ROOT) + " STONE";
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
                new NamespacedKey(plugin, STONE_ID_KEY), PersistentDataType.STRING, id());

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, STONE_ID_KEY), PersistentDataType.STRING);
        return id().equals(value);
    }

    public static NamespacedKey idKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, STONE_ID_KEY);
    }
}
