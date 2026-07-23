package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/** Shaves every weapon's ability cooldowns so abilities come back faster. */
public final class SwiftcasterRing extends Accessory {

    public SwiftcasterRing(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "swiftcaster_ring";
    }

    @Override
    public Material material() {
        return Material.SUGAR;
    }

    @Override
    public String displayNameText() {
        return "Swiftcaster Ring";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("-20% cooldown", NamedTextColor.GREEN)
                        .append(Component.text(" on all abilities.", NamedTextColor.GRAY)));
    }

    @Override
    public double cooldownMultiplier(Weapon weapon) {
        return 0.80;
    }
}
