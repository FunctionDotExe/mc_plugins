package dev.rbm72.weaponsplugin.armor.sets;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.armor.ArmorPiece;
import dev.rbm72.weaponsplugin.armor.ArmorSet;
import dev.rbm72.weaponsplugin.armor.ArmorSlot;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Volcanic-forged set: passive tiers keep the wearer fireproof and swinging fast, while the
 * reactive tiers (3pc Molten Retribution, 4pc Eruption) key off taking damage and live in
 * {@code CinderforgedListener}, same split as {@link PenitentsVestments}.
 */
public final class CinderforgedPlate extends ArmorSet {

    public static final Color CINDER_COLOR = Color.fromRGB(191, 64, 15);

    public CinderforgedPlate(WeaponsPlugin plugin) {
        super(plugin);
        piece(ArmorSlot.HELMET, new Piece(plugin, this, ArmorSlot.HELMET, "cinderforged_helmet", Material.LEATHER_HELMET));
        piece(ArmorSlot.CHESTPLATE, new Piece(plugin, this, ArmorSlot.CHESTPLATE, "cinderforged_chestplate", Material.LEATHER_CHESTPLATE));
        piece(ArmorSlot.LEGGINGS, new Piece(plugin, this, ArmorSlot.LEGGINGS, "cinderforged_leggings", Material.LEATHER_LEGGINGS));
        piece(ArmorSlot.BOOTS, new Piece(plugin, this, ArmorSlot.BOOTS, "cinderforged_boots", Material.LEATHER_BOOTS));
    }

    @Override
    public String id() {
        return "cinderforged_plate";
    }

    @Override
    public String displayNameText() {
        return "Cinderforged Plate";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Quenched in the same forge", NamedTextColor.GRAY),
                Component.text("that never stopped burning.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> tierDescriptions() {
        return List.of(
                "Fire Resistance.",
                "+ Haste I.",
                "+ Molten Retribution: melee attackers are set alight and take burn damage.",
                "+ Eruption: at 30% health (45s cooldown), detonate a cinder nova that damages and ignites nearby enemies, granting yourself Fire Resistance + Absorption.");
    }

    @Override
    public void onTick(Player player, int wornCount) {
        if (wornCount >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, true, false, false));
        }
        if (wornCount >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 30, 0, true, false, false));
        }
        // Tiers 3 (Molten Retribution) and 4 (Eruption) are reactive to incoming damage and live in CinderforgedListener.
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
            return CINDER_COLOR;
        }
    }
}
