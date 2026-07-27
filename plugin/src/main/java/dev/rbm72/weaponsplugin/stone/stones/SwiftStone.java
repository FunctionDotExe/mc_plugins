package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Passive mobility: grants Speed II as long as it stays equipped. */
public final class SwiftStone extends Stone {

    public SwiftStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "swift_stone";
    }

    @Override
    public Material material() {
        return Material.SUGAR;
    }

    @Override
    public String displayNameText() {
        return "Swift Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.COMMON;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Permanent Speed II", NamedTextColor.GREEN),
                Component.text("while equipped.", NamedTextColor.GRAY));
    }

    @Override
    public void onEquipTick(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, true, false, false));
    }
}
