package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/** Flat damage boost that applies to every weapon. The entry-level offensive trinket. */
public final class WarriorsBand extends Accessory {

    public WarriorsBand(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "warriors_band";
    }

    @Override
    public Material material() {
        return Material.IRON_INGOT;
    }

    @Override
    public String displayNameText() {
        return "Warrior's Band";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("+15% damage", NamedTextColor.GREEN)
                        .append(Component.text(" with all weapons.", NamedTextColor.GRAY)));
    }

    @Override
    public double damageMultiplier(Weapon weapon) {
        return 1.15;
    }
}
