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

    /**
     * Per-stone tunable, read live off {@code stones.<id>.<key>}. Mirrors the {@code weapons.<id>.*} /
     * {@code bosses.<id>.*} convention — every number a stone uses gets a key, so a movement perk can be
     * retuned without a recompile the same way every other tuned number in this plugin can.
     */
    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("stones." + id() + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("stones." + id() + "." + key, def);
    }

    protected final boolean configBoolean(String key, boolean def) {
        return plugin.getConfig().getBoolean("stones." + id() + "." + key, def);
    }

    /**
     * Potion-effect amplifier rendered the way the game writes it — amplifier 1 is "II".
     * <p>
     * Here rather than in each passive stone because the amplifier is config-backed: a tooltip that says
     * "Speed II" while the config says amplifier 2 is a lie, and three stones each hand-writing the same
     * conversion is three chances to only fix two of them.
     */
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

    public abstract Material material();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Human-readable lore lines describing what the stone does. */
    public abstract List<Component> description();

    /** Fires twice per second for each equipped stone. Drives passive movement buffs. */
    public void onEquipTick(Player player) {
    }

    /**
     * Fires <em>every tick</em> for each equipped stone — see {@code StoneMovementTask}.
     * <p>
     * {@link #onEquipTick} runs at 2Hz, which is the right cadence for refreshing a potion effect and the
     * wrong one for anything that answers a movement input: at 0.5s granularity a wall-grab happens a third
     * of a second after you reached the wall, and a double-jump is unavailable for the first ten ticks of
     * every jump. Anything that has to feel connected to the player's own frame goes here instead.
     */
    public void onFastTick(Player player) {
    }

    /**
     * Fires every tick for each online player who does <em>not</em> have this stone equipped.
     * <p>
     * Exists because a stone that arms a vanilla capability on its wielder — {@code allowFlight}, in
     * {@link dev.rbm72.weaponsplugin.stone.stones.SkyleapStone}'s case — leaves that capability switched on
     * if the stone is unsocketed at the wrong moment, and "unsocket mid-air for permanent creative flight"
     * is an exploit no amount of care inside the equipped path can close. A stone that arms nothing has
     * nothing to do here, which is why this defaults to a no-op.
     */
    public void onIdleTick(Player player) {
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
