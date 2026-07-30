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
 * Unseen, unheard by mobs, and it never wears off mid-approach.
 * <p>
 * The observation bottle: an endless Invisibility is what lets an operator stand inside a live fight and watch
 * a mechanic resolve without being the thing it resolves onto. Slow Falling rides along because the usual way
 * that goes wrong is a rooftop vantage point and a long drop, and Night Vision because half of what is worth
 * watching happens in a cave.
 * <p>
 * Armour still gives you away — vanilla renders worn pieces on an invisible player, and this bottle does not
 * pretend otherwise. Drinking again with the set running lifts it, which is also the fast way back to being
 * visible when someone needs to see you.
 */
public final class PhantomDraught extends InfiniteDraught {

    private static final Color PHANTOM_VIOLET = Color.fromRGB(150, 110, 220);

    public PhantomDraught(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "phantom_draught";
    }

    @Override
    public String displayNameText() {
        return "Phantom Draught";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.LIGHT_PURPLE;
    }

    @Override
    protected NamedTextColor flashColor() {
        return NamedTextColor.LIGHT_PURPLE;
    }

    @Override
    protected String flashLabel() {
        return "Phantom Draught";
    }

    @Override
    protected Color bottleColor() {
        return PHANTOM_VIOLET;
    }

    @Override
    protected List<Infusion> infusions() {
        return List.of(
                new Infusion(PotionEffectType.INVISIBILITY, "invisibility-amplifier", 0, "Invisibility"),
                new Infusion(PotionEffectType.NIGHT_VISION, "night-vision-amplifier", 0, "Night Vision"),
                new Infusion(PotionEffectType.SPEED, "speed-amplifier", 1, "Speed"),
                new Infusion(PotionEffectType.SLOW_FALLING, "slow-falling-amplifier", 0, "Slow Falling"));
    }

    @Override
    public List<Component> description() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("No duration. These do not run out:", NamedTextColor.LIGHT_PURPLE));
        lore.addAll(infusionLines(NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(Component.text("Worn armour still shows —", NamedTextColor.GRAY));
        lore.add(Component.text("take it off to vanish properly.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Drink again to lift them —", NamedTextColor.GRAY));
        lore.add(Component.text("that one is free, no bottle spent.", NamedTextColor.GRAY));
        return lore;
    }
}
