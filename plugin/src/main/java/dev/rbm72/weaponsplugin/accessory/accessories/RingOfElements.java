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

/** Passive elemental warding: a standing Fire Resistance plus a flat Resistance buff, so the
 *  wearer simply shrugs off more of everything while it's equipped. */
public final class RingOfElements extends Accessory {

    public RingOfElements(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ring_of_elements";
    }

    @Override
    public Material material() {
        return Material.EMERALD;
    }

    @Override
    public String displayNameText() {
        return "Ring of the Elements";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Warm to the touch, no matter", NamedTextColor.GRAY),
                Component.text("the elements raging outside.", NamedTextColor.GRAY));
    }

    @Override
    public void onEquipTick(Player player) {
        // Refreshed twice a second (the tick cadence); 30 ticks outlasts the gap so it never flickers off.
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, true, false, false));
    }
}
