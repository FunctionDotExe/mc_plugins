package dev.rbm72.weaponsplugin.armor;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A four-piece wearable set (helmet/chestplate/leggings/boots) whose effects scale with how many
 * of its own pieces are currently worn, evaluated every {@link ArmorTickTask} pass. Mirrors {@link
 * dev.rbm72.weaponsplugin.items.Weapon} and {@link dev.rbm72.weaponsplugin.accessory.Accessory}:
 * a concrete set describes itself and its per-tier bonuses, and reacts to worn-count changes
 * through {@link #onTick}. Unlike weapons/accessories, pieces sit in real vanilla armor slots —
 * no equip menu needed.
 */
public abstract class ArmorSet {

    protected final WeaponsPlugin plugin;
    private final Map<ArmorSlot, ArmorPiece> pieces = new EnumMap<>(ArmorSlot.class);

    protected ArmorSet(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Concrete sets call this once per slot from their constructor, after their fields are set. */
    protected final void piece(ArmorSlot slot, ArmorPiece piece) {
        pieces.put(slot, piece);
    }

    public abstract String id();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Flavor lore shown on every piece, above the set-bonus tier breakdown. */
    public abstract List<Component> description();

    /** One summary line per worn-count tier: index 0 = 1 piece worn ... index 3 = full 4-piece set. */
    public abstract List<String> tierDescriptions();

    public final ArmorPiece piece(ArmorSlot slot) {
        return pieces.get(slot);
    }

    public final Map<ArmorSlot, ArmorPiece> pieces() {
        return pieces;
    }

    /** Fires twice a second for every online player, passed however many of this set's pieces (0-4) they have on. */
    public void onTick(Player player, int wornCount) {
    }
}
