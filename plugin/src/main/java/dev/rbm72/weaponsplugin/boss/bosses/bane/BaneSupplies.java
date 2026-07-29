package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the arena hands the group at the start of every phase — the §0.3 rule, and load-bearing here:
 * slowing a clock means <em>placing a repeater in it</em>, so a fight that assumed players brought
 * repeaters would be unwinnable for anyone who didn't. Bone blocks let a group repair or extend the
 * cover a Convergence is survived behind.
 */
final class BaneSupplies {

    private BaneSupplies() {
    }

    static void dropFor(BaneFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        BaneConfig config = fight.config();

        drop(fight, at, Material.REPEATER, config.num("supply-repeaters-per-player", 3) * players);
        drop(fight, at, Material.BONE_BLOCK, config.num("supply-bone-blocks-per-player", 8) * players);
        drop(fight, at, Material.MILK_BUCKET, config.num("supply-milk-per-player", 1) * players);

        Fx.burst(at, Particle.SOUL, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }

    private static void drop(BaneFight fight, Location at, Material material, int amount) {
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
