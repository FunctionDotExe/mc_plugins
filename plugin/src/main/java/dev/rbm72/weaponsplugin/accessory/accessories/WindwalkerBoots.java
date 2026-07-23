package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Passive mobility: grants Speed I as long as it stays equipped. */
public final class WindwalkerBoots extends Accessory {

    public WindwalkerBoots(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "windwalker_boots";
    }

    @Override
    public Material material() {
        return Material.FEATHER;
    }

    @Override
    public String displayNameText() {
        return "Windwalker Boots";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Permanent Speed I", NamedTextColor.GREEN),
                Component.text("while equipped.", NamedTextColor.GRAY));
    }

    @Override
    public void onEquipTick(Player player) {
        // Refreshed twice a second (the tick cadence); 30 ticks outlasts the gap so it never flickers off.
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false, false));
    }
}
