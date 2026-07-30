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
 * Deletes the environment as a threat, permanently: lava, drowning and the sea floor all stop mattering.
 * <p>
 * Deliberately no combat stat on it — that is {@link EternalDraught}'s and {@link AscendantElixir}'s job. This
 * one is the traversal grant, the bottle to hand someone who has to go build in the Nether or dig out an
 * ocean-floor arena, and keeping the two roles in separate bottles is what stops "give them a potion" from
 * always meaning "give them everything".
 * <p>
 * Drinking again with the set running lifts it — see {@link InfiniteDraught}.
 */
public final class ElementalWard extends InfiniteDraught {

    private static final Color WARD_BLUE = Color.fromRGB(60, 150, 255);

    public ElementalWard(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "elemental_ward";
    }

    @Override
    public String displayNameText() {
        return "Elemental Ward";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.BLUE;
    }

    @Override
    protected NamedTextColor flashColor() {
        return NamedTextColor.BLUE;
    }

    @Override
    protected String flashLabel() {
        return "Elemental Ward";
    }

    @Override
    protected Color bottleColor() {
        return WARD_BLUE;
    }

    @Override
    protected List<Infusion> infusions() {
        return List.of(
                new Infusion(PotionEffectType.FIRE_RESISTANCE, "fire-resistance-amplifier", 0, "Fire Resistance"),
                new Infusion(PotionEffectType.WATER_BREATHING, "water-breathing-amplifier", 0, "Water Breathing"),
                new Infusion(PotionEffectType.CONDUIT_POWER, "conduit-power-amplifier", 0, "Conduit Power"),
                new Infusion(PotionEffectType.DOLPHINS_GRACE, "dolphins-grace-amplifier", 0, "Dolphin's Grace"));
    }

    @Override
    public List<Component> description() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("No duration. These do not run out:", NamedTextColor.BLUE));
        lore.addAll(infusionLines(NamedTextColor.BLUE));
        lore.add(Component.empty());
        lore.add(Component.text("Fire, lava and water stop being", NamedTextColor.GRAY));
        lore.add(Component.text("hazards. No combat stats — those", NamedTextColor.GRAY));
        lore.add(Component.text("live in the other bottles.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Drink again to lift them —", NamedTextColor.GRAY));
        lore.add(Component.text("that one is free, no bottle spent.", NamedTextColor.GRAY));
        return lore;
    }
}
