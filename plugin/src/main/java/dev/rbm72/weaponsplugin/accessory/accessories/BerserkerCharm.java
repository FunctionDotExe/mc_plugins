package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/** Heavier flat damage boost across all weapons for players who want raw power. */
public final class BerserkerCharm extends Accessory {

    public BerserkerCharm(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "berserker_charm";
    }

    @Override
    public Material material() {
        return Material.BLAZE_POWDER;
    }

    @Override
    public String displayNameText() {
        return "Berserker Charm";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("+25% damage", NamedTextColor.GREEN)
                        .append(Component.text(" with all weapons.", NamedTextColor.GRAY)));
    }

    @Override
    public double damageMultiplier(Weapon weapon) {
        return 1.25;
    }
}
