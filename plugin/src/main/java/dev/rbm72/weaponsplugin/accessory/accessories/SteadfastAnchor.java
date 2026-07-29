package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/**
 * Cancels every hit's knockback outright, and takes the hurt tilt off recurring damage ticks — the
 * damage still lands, but the wearer is never shoved, and standing in fire/poison/lava no longer jolts
 * the camera several times a second. A real telegraphed hit still flinches; that one is feedback.
 */
public final class SteadfastAnchor extends Accessory {

    public SteadfastAnchor(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "steadfast_anchor";
    }

    @Override
    public Material material() {
        return Material.LODESTONE;
    }

    @Override
    public String displayNameText() {
        return "Steadfast Anchor";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Immune to knockback.", NamedTextColor.GREEN),
                Component.text("Damage over time never jolts your", NamedTextColor.GREEN),
                Component.text("camera and never rattles your ears —", NamedTextColor.GREEN),
                Component.text("no tilt, no hurt sound, no shove.", NamedTextColor.GREEN),
                Component.text("It still costs exactly as much health.", NamedTextColor.GRAY));
    }

    @Override
    public boolean negatesKnockback() {
        return true;
    }

    @Override
    public boolean negatesTickFlinch() {
        return true;
    }
}
