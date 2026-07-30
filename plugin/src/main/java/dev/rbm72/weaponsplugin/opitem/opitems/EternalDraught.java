package dev.rbm72.weaponsplugin.opitem.opitems;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.opitem.InfiniteDraught;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Never hungry, never blind, never out of health, hard to kill — and it never expires.
 * <p>
 * The four together are the "stop thinking about upkeep" grant: Saturation feeds the Regeneration that
 * out-heals chip damage, Resistance flattens what gets through, Night Vision removes the torch. Resistance
 * sits at IV rather than V for the same reason it does on {@link AscendantElixir}: amplifier 4 is total damage
 * immunity, which is a different item and should be asked for on purpose.
 * <p>
 * Drinking again with the set running lifts it — see {@link InfiniteDraught}.
 */
public final class EternalDraught extends InfiniteDraught {

    private static final Color DRAUGHT_TEAL = Color.fromRGB(70, 235, 200);

    public EternalDraught(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "eternal_draught";
    }

    @Override
    public String displayNameText() {
        return "Eternal Draught";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.AQUA;
    }

    @Override
    protected NamedTextColor flashColor() {
        return NamedTextColor.AQUA;
    }

    @Override
    protected String flashLabel() {
        return "Eternal Draught";
    }

    @Override
    protected Color bottleColor() {
        return DRAUGHT_TEAL;
    }

    @Override
    protected List<Infusion> infusions() {
        return List.of(
                new Infusion(PotionEffectType.RESISTANCE, "resistance-amplifier", 3, "Resistance"),
                new Infusion(PotionEffectType.REGENERATION, "regeneration-amplifier", 1, "Regeneration"),
                new Infusion(PotionEffectType.NIGHT_VISION, "night-vision-amplifier", 0, "Night Vision"),
                new Infusion(PotionEffectType.SATURATION, "saturation-amplifier", 0, "Saturation"));
    }

    @Override
    public List<Component> description() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("No duration. These do not run out:", NamedTextColor.AQUA));
        lore.addAll(infusionLines(NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(Component.text("Drink again to lift them —", NamedTextColor.GRAY));
        lore.add(Component.text("that one is free, no bottle spent.", NamedTextColor.GRAY));
        return lore;
    }
}
