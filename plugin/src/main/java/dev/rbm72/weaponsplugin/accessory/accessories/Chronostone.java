package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/** Stronger cooldown reduction for ability-spam builds. */
public final class Chronostone extends Accessory {

    public Chronostone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "chronostone";
    }

    @Override
    public Material material() {
        return Material.CLOCK;
    }

    @Override
    public String displayNameText() {
        return "Chronostone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("-35% cooldown", NamedTextColor.GREEN)
                        .append(Component.text(" on all abilities.", NamedTextColor.GRAY)));
    }

    @Override
    public double cooldownMultiplier(Weapon weapon) {
        return 0.65;
    }
}
