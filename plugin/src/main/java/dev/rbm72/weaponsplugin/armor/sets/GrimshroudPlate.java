package dev.rbm72.weaponsplugin.armor.sets;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.armor.ArmorPiece;
import dev.rbm72.weaponsplugin.armor.ArmorSet;
import dev.rbm72.weaponsplugin.armor.ArmorSlot;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Grave-bound set: passive tiers keep the wearer wither-free and hand out strength when things get
 * desperate, while the reactive tiers (3pc Soul Drain, 4pc Death's Bargain) key off dealing/taking
 * damage and live in {@code GrimshroudListener}, same split as {@link CinderforgedPlate}.
 */
public final class GrimshroudPlate extends ArmorSet {

    public static final Color GRIM_COLOR = Color.fromRGB(70, 90, 70);
    private static final double SOUL_WISP_CHANCE = 0.3;
    private static final double DESPERATION_HEALTH_FRACTION = 0.5;

    public GrimshroudPlate(WeaponsPlugin plugin) {
        super(plugin);
        piece(ArmorSlot.HELMET, new Piece(plugin, this, ArmorSlot.HELMET, "grimshroud_helmet", Material.LEATHER_HELMET));
        piece(ArmorSlot.CHESTPLATE, new Piece(plugin, this, ArmorSlot.CHESTPLATE, "grimshroud_chestplate", Material.LEATHER_CHESTPLATE));
        piece(ArmorSlot.LEGGINGS, new Piece(plugin, this, ArmorSlot.LEGGINGS, "grimshroud_leggings", Material.LEATHER_LEGGINGS));
        piece(ArmorSlot.BOOTS, new Piece(plugin, this, ArmorSlot.BOOTS, "grimshroud_boots", Material.LEATHER_BOOTS));
    }

    @Override
    public String id() {
        return "grimshroud_plate";
    }

    @Override
    public String displayNameText() {
        return "Grimshroud Plate";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Stitched from grave-cloth and old", NamedTextColor.GRAY),
                Component.text("oaths the dead never got to keep.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> tierDescriptions() {
        return List.of(
                "Wither immunity (cured automatically) and faint soul wisps.",
                "+ Strength I while below 50% health.",
                "+ Soul Drain: your melee hits heal you for 15% of the damage dealt.",
                "+ Death's Bargain: once per 60s, a fatal hit instead leaves you at 20% health with Resistance + Regeneration.");
    }

    @Override
    public void onTick(Player player, int wornCount) {
        if (wornCount >= 1) {
            if (player.hasPotionEffect(PotionEffectType.WITHER)) {
                player.removePotionEffect(PotionEffectType.WITHER);
            }
            if (ThreadLocalRandom.current().nextDouble() < SOUL_WISP_CHANCE) {
                Fx.point(player.getLocation().add(0, 1, 0), Particle.SOUL, 3);
            }
        }
        if (wornCount >= 2) {
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (player.getHealth() < maxHealth * DESPERATION_HEALTH_FRACTION) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30, 0, true, false, false));
            }
        }
        // Tiers 3 (Soul Drain) and 4 (Death's Bargain) are reactive to combat and live in GrimshroudListener.
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
            return GRIM_COLOR;
        }
    }
}
