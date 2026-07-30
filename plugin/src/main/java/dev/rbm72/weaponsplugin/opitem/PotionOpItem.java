package dev.rbm72.weaponsplugin.opitem;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/**
 * An op item that looks like a bottle.
 * <p>
 * A {@link Material#POTION} on the op shelf is never actually drunk: {@code OpItemUseListener} cancels the
 * interact event, so vanilla's drink never starts and this plugin applies the effects and spends the bottle
 * itself. That indirection is the only way to have a potion whose contents are not a vanilla potion type —
 * left drinkable, the client would animate a use the server had already handled and the bottle would empty
 * twice. The base type is therefore always {@link PotionType#WATER} (inert, so the client prints no effect
 * list of its own) and the visible colour comes from {@link #bottleColor()}.
 */
public abstract class PotionOpItem extends OpItem {

    /**
     * One effect the bottle grants. {@code configKey} is the {@code op-items.<id>.<key>} suffix that tunes its
     * amplifier, {@code label} is how the tooltip names it.
     */
    public record Infusion(PotionEffectType type, String configKey, int defaultAmplifier, String label) {
    }

    protected PotionOpItem(WeaponsPlugin plugin) {
        super(plugin);
    }

    /** Every effect this bottle grants, in tooltip order. */
    protected abstract List<Infusion> infusions();

    /** Bottle tint. Not decoration alone — it is how two op potions in one hotbar stay tellable apart. */
    protected abstract Color bottleColor();

    @Override
    public Material material() {
        return Material.POTION;
    }

    @Override
    public Sound useSound() {
        return Sound.ITEM_HONEY_BOTTLE_DRINK;
    }

    protected final int amplifier(Infusion infusion) {
        return Math.max(0, configInt(infusion.configKey(), infusion.defaultAmplifier()));
    }

    /** One "• Regeneration II" line per infusion, so a lore block never drifts from what {@code onUse} applies. */
    protected final List<Component> infusionLines(NamedTextColor color) {
        List<Component> lines = new ArrayList<>();
        for (Infusion infusion : infusions()) {
            lines.add(Component.text("• " + infusion.label() + " " + roman(amplifier(infusion)), color));
        }
        return lines;
    }

    /**
     * Bottle colour and a clean tooltip. {@code HIDE_ADDITIONAL_TOOLTIP} is deprecated in favour of the
     * item-data-component tooltip-display API, which is still experimental in this Paper build; every other
     * item family here reaches for {@link ItemFlag} and they all have to keep agreeing, so this one does too.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void decorate(ItemMeta meta) {
        if (meta instanceof PotionMeta potion) {
            potion.setBasePotionType(PotionType.WATER);
            potion.setColor(bottleColor());
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }
}
