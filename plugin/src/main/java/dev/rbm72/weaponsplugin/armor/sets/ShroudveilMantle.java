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
 * Void-stitched set: passive tiers just help the wearer stay hidden, while the reactive tiers
 * (3pc Shadowstep Evasion, 4pc Umbral Collapse) key off taking damage and live in
 * {@code ShroudveilListener}, same split as {@link CinderforgedPlate}.
 */
public final class ShroudveilMantle extends ArmorSet {

    public static final Color VOID_COLOR = Color.fromRGB(35, 0, 55);

    public ShroudveilMantle(WeaponsPlugin plugin) {
        super(plugin);
        piece(ArmorSlot.HELMET, new Piece(plugin, this, ArmorSlot.HELMET, "shroudveil_helmet", Material.LEATHER_HELMET));
        piece(ArmorSlot.CHESTPLATE, new Piece(plugin, this, ArmorSlot.CHESTPLATE, "shroudveil_chestplate", Material.LEATHER_CHESTPLATE));
        piece(ArmorSlot.LEGGINGS, new Piece(plugin, this, ArmorSlot.LEGGINGS, "shroudveil_leggings", Material.LEATHER_LEGGINGS));
        piece(ArmorSlot.BOOTS, new Piece(plugin, this, ArmorSlot.BOOTS, "shroudveil_boots", Material.LEATHER_BOOTS));
    }

    @Override
    public String id() {
        return "shroudveil_mantle";
    }

    @Override
    public String displayNameText() {
        return "Shroudveil Mantle";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Woven from the fabric between", NamedTextColor.GRAY),
                Component.text("worlds — it barely touches this one.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> tierDescriptions() {
        return List.of(
                "Speed I.",
                "+ Night Vision.",
                "+ Shadowstep Evasion: incoming attacks have a 15% chance to be fully avoided, blinking you a short distance away.",
                "+ Umbral Collapse: at 25% health (40s cooldown), vanish with Invisibility + Speed II to slip away.");
    }

    @Override
    public void onTick(Player player, int wornCount) {
        if (wornCount >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false, false));
        }
        if (wornCount >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 30, 0, true, false, false));
        }
        // Tiers 3 (Shadowstep Evasion) and 4 (Umbral Collapse) are reactive to incoming damage and live in ShroudveilListener.
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
            return VOID_COLOR;
        }
    }
}
