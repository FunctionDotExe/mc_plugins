package dev.rbm72.weaponsplugin.armor.sets;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.armor.ArmorPiece;
import dev.rbm72.weaponsplugin.armor.ArmorSet;
import dev.rbm72.weaponsplugin.armor.ArmorSlot;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Dragon-scale flight set: the more of it you wear, the more sky you get. The full set lets you
 * double-jump into a short wing-dash burst (see {@code WingDashListener}) instead of just
 * falling — a real, controlled taste of flight without granting free creative-style flying.
 */
public final class WyrmwingPlate extends ArmorSet {

    public static final Color SCALE_COLOR = Color.fromRGB(46, 125, 122);

    public WyrmwingPlate(WeaponsPlugin plugin) {
        super(plugin);
        piece(ArmorSlot.HELMET, new Piece(plugin, this, ArmorSlot.HELMET, "wyrmwing_helmet", Material.LEATHER_HELMET));
        piece(ArmorSlot.CHESTPLATE, new Piece(plugin, this, ArmorSlot.CHESTPLATE, "wyrmwing_chestplate", Material.LEATHER_CHESTPLATE));
        piece(ArmorSlot.LEGGINGS, new Piece(plugin, this, ArmorSlot.LEGGINGS, "wyrmwing_leggings", Material.LEATHER_LEGGINGS));
        piece(ArmorSlot.BOOTS, new Piece(plugin, this, ArmorSlot.BOOTS, "wyrmwing_boots", Material.LEATHER_BOOTS));
    }

    @Override
    public String id() {
        return "wyrmwing_plate";
    }

    @Override
    public String displayNameText() {
        return "Wyrmwing Plate";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Scaled hide from a wyrm that", NamedTextColor.GRAY),
                Component.text("never truly landed.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> tierDescriptions() {
        return List.of(
                "Regeneration I.",
                "+ Jump Boost II.",
                "+ Permanent Slow Falling & Fire Resistance.",
                "+ Double-jump to wing-dash forward (6s cooldown).");
    }

    @Override
    public void onTick(Player player, int wornCount) {
        if (wornCount >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 0, true, false, false));
        }
        if (wornCount >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 1, true, false, false));
        }
        if (wornCount >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, true, false, false));
        }

        // Arms/disarms the double-tap-space gesture WingDashListener listens for. Left alone in
        // creative/spectator, where the vanilla toggle already means something else.
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (wornCount >= 4) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        } else if (player.getAllowFlight() && !player.isFlying()) {
            player.setAllowFlight(false);
        }
    }

    private static final class Piece extends ArmorPiece {
        private final String id;
        private final Material material;

        Piece(WeaponsPlugin plugin, ArmorSet set, ArmorSlot slot, String id, Material material) {
            super(plugin, set, slot);
            this.id = id;
            this.material = material;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Material material() {
            return material;
        }

        @Override
        protected Color leatherColor() {
            return SCALE_COLOR;
        }
    }
}
