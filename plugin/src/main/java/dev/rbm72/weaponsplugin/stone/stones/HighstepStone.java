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

/** Passive mobility: grants Jump Boost II as long as it stays equipped. */
public final class HighstepStone extends Stone {

    public HighstepStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "highstep_stone";
    }

    @Override
    public Material material() {
        return Material.AMETHYST_SHARD;
    }

    @Override
    public String displayNameText() {
        return "Highstep Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.COMMON;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Permanent Jump Boost II", NamedTextColor.GREEN),
                Component.text("while equipped.", NamedTextColor.GRAY));
    }

    @Override
    public void onEquipTick(Player player) {
        // Refreshed twice a second (the tick cadence); 30 ticks outlasts the gap so it never flickers off.
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 1, true, false, false));
    }
}
