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

/** Passive mobility: grants Dolphin's Grace II and Water Breathing as long as it stays equipped. */
public final class TideStone extends Stone {

    public TideStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "tide_stone";
    }

    @Override
    public Material material() {
        return Material.PRISMARINE_CRYSTALS;
    }

    @Override
    public String displayNameText() {
        return "Tide Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Permanent Dolphin's Grace " + roman(graceAmplifier()), NamedTextColor.AQUA),
                Component.text("& Water Breathing while", NamedTextColor.GRAY),
                Component.text("equipped.", NamedTextColor.GRAY));
    }

    private int graceAmplifier() {
        return Math.max(0, configInt("grace-amplifier", 1));
    }

    @Override
    public void onEquipTick(Player player) {
        // Refreshed twice a second (the tick cadence); 30 ticks outlasts the gap so it never flickers off.
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 30, graceAmplifier(), true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 30, 0, true, false, false));
    }
}
