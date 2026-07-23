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
 * Frost-bound set: the tankiest of the elemental trio. Passive tiers stack raw damage mitigation;
 * the reactive tiers (3pc Frostbite, 4pc Absolute Zero) key off taking damage and live in
 * {@code GlacialWardListener}, same split as {@link PenitentsVestments}.
 */
public final class GlacialWard extends ArmorSet {

    public static final Color FROST_COLOR = Color.fromRGB(140, 200, 230);

    public GlacialWard(WeaponsPlugin plugin) {
        super(plugin);
        piece(ArmorSlot.HELMET, new Piece(plugin, this, ArmorSlot.HELMET, "glacial_ward_helmet", Material.LEATHER_HELMET));
        piece(ArmorSlot.CHESTPLATE, new Piece(plugin, this, ArmorSlot.CHESTPLATE, "glacial_ward_chestplate", Material.LEATHER_CHESTPLATE));
        piece(ArmorSlot.LEGGINGS, new Piece(plugin, this, ArmorSlot.LEGGINGS, "glacial_ward_leggings", Material.LEATHER_LEGGINGS));
        piece(ArmorSlot.BOOTS, new Piece(plugin, this, ArmorSlot.BOOTS, "glacial_ward_boots", Material.LEATHER_BOOTS));
    }

    @Override
    public String id() {
        return "glacial_ward";
    }

    @Override
    public String displayNameText() {
        return "Glacial Ward";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Carved from ice that has", NamedTextColor.GRAY),
                Component.text("never once felt the sun.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> tierDescriptions() {
        return List.of(
                "Resistance I.",
                "+ Absorption I.",
                "+ Frostbite: melee attackers have a 35% chance to be afflicted with Slowness II and Mining Fatigue I.",
                "+ Absolute Zero: at 25% health (45s cooldown), root all nearby enemies with Slowness IV + Weakness II and gain Resistance II + Absorption II.");
    }

    @Override
    public void onTick(Player player, int wornCount) {
        if (wornCount >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, true, false, false));
        }
        if (wornCount >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 30, 0, true, false, false));
        }
        // Tiers 3 (Frostbite) and 4 (Absolute Zero) are reactive to incoming damage and live in GlacialWardListener.
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
            return FROST_COLOR;
        }
    }
}
