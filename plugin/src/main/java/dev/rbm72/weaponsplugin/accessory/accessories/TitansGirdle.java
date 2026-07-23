package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/** Top-tier hybrid: raw damage and faster cooldowns in one slot. */
public final class TitansGirdle extends Accessory {

    public TitansGirdle(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "titans_girdle";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_INGOT;
    }

    @Override
    public String displayNameText() {
        return "Titan's Girdle";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("+20% damage", NamedTextColor.GREEN)
                        .append(Component.text(" and ", NamedTextColor.GRAY))
                        .append(Component.text("-20% cooldown", NamedTextColor.GREEN)),
                Component.text("with all weapons.", NamedTextColor.GRAY));
    }

    @Override
    public double damageMultiplier(Weapon weapon) {
        return 1.20;
    }

    @Override
    public double cooldownMultiplier(Weapon weapon) {
        return 0.80;
    }
}
