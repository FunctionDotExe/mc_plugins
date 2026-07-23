package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Rewires the ultimate slot of every weapon into a two-hit combo: the instant its ultimate
 * resolves, ability1 fires again for free right on top of it. Doesn't scale a single number —
 * it changes what pressing the ultimate button does.
 */
public final class RunicOverload extends Accessory {

    private static final Color RUNE_COLOR = Color.fromRGB(190, 140, 255);

    public RunicOverload(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "runic_overload";
    }

    @Override
    public Material material() {
        return Material.ENCHANTED_BOOK;
    }

    @Override
    public String displayNameText() {
        return "Runic Overload";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Overloads your ultimate:", NamedTextColor.GREEN),
                Component.text("casting it also unleashes your", NamedTextColor.GRAY),
                Component.text("first ability in the same instant.", NamedTextColor.GRAY));
    }

    @Override
    public void onAbilityCast(Player player, Weapon weapon, Slot slot) {
        if (slot != Slot.ULTIMATE) {
            return;
        }

        Fx.sound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.7f);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), RUNE_COLOR, 1.6f, 26, 0.55);
        weapon.ability1(player);
    }
}
