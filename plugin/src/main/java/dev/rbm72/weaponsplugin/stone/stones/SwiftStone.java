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
                Component.text("Permanent Speed " + roman(amplifier()), NamedTextColor.GREEN),
                Component.text("while equipped.", NamedTextColor.GRAY));
    }

    private int amplifier() {
        return Math.max(0, configInt("amplifier", 1));
    }

    @Override
    public void onEquipTick(Player player) {
        // Refreshed twice a second (the tick cadence); 30 ticks outlasts the gap so it never flickers off.
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, amplifier(), true, false, false));
    }
}
