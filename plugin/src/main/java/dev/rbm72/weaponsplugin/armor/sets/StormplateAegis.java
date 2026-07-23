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
 * Storm-charged set: quick and reactive rather than a straight tank. The passive tiers here are
 * just mobility; the two reactive tiers (3pc Static Discharge, 4pc Storm Surge) key off taking
 * damage and live in {@code StormplateListener}, same split as {@link PenitentsVestments}.
 */
public final class StormplateAegis extends ArmorSet {

    public static final Color STORM_COLOR = Color.fromRGB(196, 220, 255);

    public StormplateAegis(WeaponsPlugin plugin) {
        super(plugin);
        piece(ArmorSlot.HELMET, new Piece(plugin, this, ArmorSlot.HELMET, "stormplate_helmet", Material.LEATHER_HELMET));
        piece(ArmorSlot.CHESTPLATE, new Piece(plugin, this, ArmorSlot.CHESTPLATE, "stormplate_chestplate", Material.LEATHER_CHESTPLATE));
        piece(ArmorSlot.LEGGINGS, new Piece(plugin, this, ArmorSlot.LEGGINGS, "stormplate_leggings", Material.LEATHER_LEGGINGS));
        piece(ArmorSlot.BOOTS, new Piece(plugin, this, ArmorSlot.BOOTS, "stormplate_boots", Material.LEATHER_BOOTS));
    }

    @Override
    public String id() {
        return "stormplate_aegis";
    }

    @Override
    public String displayNameText() {
        return "Stormplate Aegis";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Plating salvaged from a tower", NamedTextColor.GRAY),
                Component.text("struck by lightning nine times.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> tierDescriptions() {
        return List.of(
                "Speed I.",
                "+ Jump Boost I.",
                "+ Static Discharge: attackers have a 30% chance to be zapped with Slowness II and take bonus damage.",
                "+ Storm Surge: at 30% health (45s cooldown), unleash a lightning nova on nearby enemies and gain Speed II + Resistance I.");
    }

    @Override
    public void onTick(Player player, int wornCount) {
        if (wornCount >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false, false));
        }
        if (wornCount >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 0, true, false, false));
        }
        // Tiers 3 (Static Discharge) and 4 (Storm Surge) are reactive to incoming damage and live in StormplateListener.
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
            return STORM_COLOR;
        }
    }
}
