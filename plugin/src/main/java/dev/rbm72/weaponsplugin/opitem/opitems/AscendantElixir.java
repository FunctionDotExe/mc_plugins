package dev.rbm72.weaponsplugin.opitem.opitems;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.opitem.OpItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Locale;

/**
 * The god potion: Resistance, Regeneration and Speed all at once, at the top of their range, for minutes.
 * <p>
 * Built on {@link Material#POTION} because it should look like what it is — but it is never actually drunk.
 * {@code OpItemUseListener} cancels the interact event, which stops vanilla's drink entirely, and this class
 * applies the effects and spends one bottle itself. That indirection is the only way to have a potion item
 * whose contents are not a vanilla potion type: had it been left drinkable, the client would play the drinking
 * animation for a use the server had already handled, and the bottle would empty twice.
 * <p>
 * Every level and duration is config-backed, and the defaults sit at the vanilla brewing ceiling for the two
 * that have one (Regeneration II, Speed II) with Resistance at IV — one short of the immunity that amplifier 4
 * grants, because "cannot be damaged at all" is a different item and should be asked for on purpose.
 */
public final class AscendantElixir extends OpItem {

    private static final Color ELIXIR_GOLD = Color.fromRGB(255, 215, 90);

    public AscendantElixir(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ascendant_elixir";
    }

    @Override
    public Material material() {
        return Material.POTION;
    }

    @Override
    public String displayNameText() {
        return "Ascendant Elixir";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.GOLD;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text(String.format(Locale.ROOT, "Resistance %s, Regeneration %s and",
                        roman(resistanceAmplifier()), roman(regenerationAmplifier())), NamedTextColor.GOLD),
                Component.text(String.format(Locale.ROOT, "Speed %s for %s.",
                        roman(speedAmplifier()), duration()), NamedTextColor.GOLD),
                Component.empty(),
                Component.text("Refreshes from zero each time —", NamedTextColor.GRAY),
                Component.text("no stacking, no waiting it out.", NamedTextColor.GRAY));
    }

    private int durationTicks() {
        return Math.max(20, configInt("duration-ticks", 6000));
    }

    private String duration() {
        int seconds = durationTicks() / 20;
        return seconds >= 60
                ? String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60)
                : seconds + "s";
    }

    private int resistanceAmplifier() {
        return Math.max(0, configInt("resistance-amplifier", 3));
    }

    private int regenerationAmplifier() {
        return Math.max(0, configInt("regeneration-amplifier", 1));
    }

    private int speedAmplifier() {
        return Math.max(0, configInt("speed-amplifier", 1));
    }

    @Override
    public Sound useSound() {
        return Sound.ITEM_HONEY_BOTTLE_DRINK;
    }

    /**
     * Bottle colour and a clean tooltip: the base type is inert so the client has no effect list to
     * print. {@code HIDE_ADDITIONAL_TOOLTIP} is deprecated in favour of the item-data-component
     * tooltip-display API, which is still experimental in this Paper build; every other item family
     * here reaches for {@link ItemFlag} and they all have to keep agreeing, so this one does too.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void decorate(ItemMeta meta) {
        if (meta instanceof PotionMeta potion) {
            potion.setBasePotionType(PotionType.WATER);
            potion.setColor(ELIXIR_GOLD);
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    @Override
    public boolean onUse(Player player) {
        int ticks = durationTicks();
        // ambient=false, particles=true: an op buff this large should be visible on the player wearing it,
        // both to them and to everyone else in the fight.
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, resistanceAmplifier(), false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, regenerationAmplifier(), false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, speedAmplifier(), false, true, true));

        plugin.actionBarHub().flash(player, Component.text("Ascendant — " + duration(), NamedTextColor.GOLD),
                2000, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        Fx.coloredBurst(player.getEyeLocation(), ELIXIR_GOLD, 1.8f, 30, 0.6);
        Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        return true;
    }

    /** Amplifier rendered the way the game writes it — amplifier 1 is "II". */
    private static String roman(int amplifier) {
        return switch (amplifier) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            default -> String.valueOf(amplifier + 1);
        };
    }
}
