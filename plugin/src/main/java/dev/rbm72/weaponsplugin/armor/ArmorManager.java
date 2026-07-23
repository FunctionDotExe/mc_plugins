package dev.rbm72.weaponsplugin.armor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Answers "how many pieces of set X does this player currently have on" by reading their real
 * armor slots directly — armor sets have no equip menu of their own, unlike accessories.
 */
public final class ArmorManager {

    private final ArmorRegistry registry;

    public ArmorManager(ArmorRegistry registry) {
        this.registry = registry;
    }

    /** Every armor set this player is wearing any piece of, mapped to how many pieces (1-4). */
    public Map<ArmorSet, Integer> wornCounts(Player player) {
        Map<ArmorSet, Integer> counts = new HashMap<>();
        PlayerInventory inv = player.getInventory();
        ItemStack[] worn = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        for (ItemStack item : worn) {
            registry.identifyPiece(item).ifPresent(piece -> counts.merge(piece.set(), 1, Integer::sum));
        }
        return counts;
    }

    public int wornCount(Player player, ArmorSet set) {
        return wornCounts(player).getOrDefault(set, 0);
    }

    public boolean fullSet(Player player, ArmorSet set) {
        return wornCount(player, set) == 4;
    }

    /** Ticks every registered set's onTick hook for the given player. */
    public void tick(Player player) {
        Map<ArmorSet, Integer> counts = wornCounts(player);
        for (ArmorSet set : registry.all()) {
            set.onTick(player, counts.getOrDefault(set, 0));
        }
    }
}
