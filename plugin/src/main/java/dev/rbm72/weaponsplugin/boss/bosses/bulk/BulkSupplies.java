package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the arena hands the group at the start of every phase — the §0.3 rule. Hoes are the point: sculk
 * is cleared fastest with one, and P2 asks the group to spend real time cleaning the floor, so a fight
 * that assumed somebody brought a hoe would be a fight decided in the inventory screen. Milk buckets are
 * the answer to a Darkness wave that catches somebody mid-kite.
 */
final class BulkSupplies {

    private BulkSupplies() {
    }

    static void dropFor(BulkFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        BulkConfig config = fight.config();

        drop(fight, at, Material.IRON_HOE, config.num("supply-hoes-per-player", 1) * players);
        drop(fight, at, Material.MILK_BUCKET, config.num("supply-milk-per-player", 1) * players);
        drop(fight, at, Material.COOKED_BEEF, config.num("supply-food-per-player", 4) * players);

        Fx.burst(at, Particle.ITEM_SLIME, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }

    private static void drop(BulkFight fight, Location at, Material material, int amount) {
        World world = at.getWorld();
        if (world == null || amount <= 0) {
            return;
        }
        int max = material.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int size = Math.min(left, max);
            left -= size;
            Item item = world.dropItem(at, new ItemStack(material, size));
            item.setPersistent(false);
            fight.instance().trackEntity(item);
        }
    }
}
