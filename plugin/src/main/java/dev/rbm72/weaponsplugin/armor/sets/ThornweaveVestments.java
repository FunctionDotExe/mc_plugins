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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Living-growth set: passive tiers keep the wearer topped up and poison-free, while the reactive
 * tiers (3pc Thorned Retaliation, 4pc healing comeback) key off taking damage and live in
 * {@code ThornweaveListener}, same split as {@link CinderforgedPlate}.
 */
public final class ThornweaveVestments extends ArmorSet {

    public static final Color THORN_COLOR = Color.fromRGB(45, 110, 40);

    public ThornweaveVestments(WeaponsPlugin plugin) {
        super(plugin);
        piece(ArmorSlot.HELMET, new Piece(plugin, this, ArmorSlot.HELMET, "thornweave_helmet", Material.LEATHER_HELMET));
        piece(ArmorSlot.CHESTPLATE, new Piece(plugin, this, ArmorSlot.CHESTPLATE, "thornweave_chestplate", Material.LEATHER_CHESTPLATE));
        piece(ArmorSlot.LEGGINGS, new Piece(plugin, this, ArmorSlot.LEGGINGS, "thornweave_leggings", Material.LEATHER_LEGGINGS));
        piece(ArmorSlot.BOOTS, new Piece(plugin, this, ArmorSlot.BOOTS, "thornweave_boots", Material.LEATHER_BOOTS));
    }

    @Override
    public String id() {
        return "thornweave_vestments";
    }

    @Override
    public String displayNameText() {
        return "Thornweave Vestments";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Living bark and creeping vine,", NamedTextColor.GRAY),
                Component.text("grown to answer violence with thorns.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> tierDescriptions() {
        return List.of(
                "Regeneration I while below full health.",
                "+ Poison immunity: any poison on you is cured automatically.",
                "+ Thorned Retaliation: melee attackers take 30% of their damage back as thorns and are poisoned briefly.",
                "+ once per 20s cooldown, taking damage heals you 4 health.");
    }

    @Override
    public void onTick(Player player, int wornCount) {
        if (wornCount >= 1) {
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (player.getHealth() < maxHealth) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 0, true, false, false));
            }
        }
        if (wornCount >= 2 && player.hasPotionEffect(PotionEffectType.POISON)) {
            player.removePotionEffect(PotionEffectType.POISON);
        }
        // Tiers 3 (Thorned Retaliation) and 4 (healing comeback) are reactive to damage and live in ThornweaveListener.
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
            return THORN_COLOR;
        }
    }
}
