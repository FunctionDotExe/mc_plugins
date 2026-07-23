package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/** Targeted trinket: supercharges fire- and sun-themed weapons only. */
public final class Emberheart extends Accessory {

    private static final Set<String> FIRE_WEAPONS = Set.of(
            "flame_katana", "cinder_cleaver", "solar_greatsword", "dawnbreaker", "dragon_fang");

    public Emberheart(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "emberheart";
    }

    @Override
    public Material material() {
        return Material.MAGMA_CREAM;
    }

    @Override
    public String displayNameText() {
        return "Emberheart";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("+40% damage", NamedTextColor.GREEN)
                        .append(Component.text(" with fire weapons:", NamedTextColor.GRAY)),
                Component.text("Flame Katana, Cinder Cleaver,", NamedTextColor.DARK_GRAY),
                Component.text("Solar Greatsword, Dawnbreaker, Dragon Fang.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public double damageMultiplier(Weapon weapon) {
        return FIRE_WEAPONS.contains(weapon.id()) ? 1.40 : 1.0;
    }
}
