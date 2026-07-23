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
 * Blasphemous-flavored penance set: the deeper you're steeped in it, the more it turns your
 * suffering into strength. Thorns (3pc) and the full-set Martyrdom cheat-death (see
 * {@code PenanceListener}) both key off incoming damage, so this class only owns the passive
 * hunger/resistance tiers directly — the damage-reactive tiers live in the listener.
 */
public final class PenitentsVestments extends ArmorSet {

    public static final Color VESTMENT_COLOR = Color.fromRGB(58, 10, 18);
    public static final double THORNS_FRACTION = 0.25;

    public PenitentsVestments(WeaponsPlugin plugin) {
        super(plugin);
        piece(ArmorSlot.HELMET, new Piece(plugin, this, ArmorSlot.HELMET, "penitent_hood", Material.LEATHER_HELMET));
        piece(ArmorSlot.CHESTPLATE, new Piece(plugin, this, ArmorSlot.CHESTPLATE, "penitent_vestment", Material.LEATHER_CHESTPLATE));
        piece(ArmorSlot.LEGGINGS, new Piece(plugin, this, ArmorSlot.LEGGINGS, "penitent_greaves", Material.LEATHER_LEGGINGS));
        piece(ArmorSlot.BOOTS, new Piece(plugin, this, ArmorSlot.BOOTS, "penitent_treads", Material.LEATHER_BOOTS));
    }

    @Override
    public String id() {
        return "penitents_vestments";
    }

    @Override
    public String displayNameText() {
        return "Penitent's Vestments";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Woven from the guilt of a", NamedTextColor.GRAY),
                Component.text("thousand unabsolved sins.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> tierDescriptions() {
        return List.of(
                "Never go hungry.",
                "+ Resistance I.",
                "+ Reflect 25% of damage taken back at your attacker.",
                "+ Martyrdom: cheat a fatal blow once every 90s, dropping to 1 HP and unleashing a guilt shockwave.");
    }

    @Override
    public void onTick(Player player, int wornCount) {
        if (wornCount >= 1) {
            player.setFoodLevel(20);
            player.setSaturation(Math.max(player.getSaturation(), 5f));
        }
        if (wornCount >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, true, false, false));
        }
        // Tiers 3 (thorns) and 4 (Martyrdom) are reactive to incoming damage and live in PenanceListener.
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
            return VESTMENT_COLOR;
        }
    }
}
