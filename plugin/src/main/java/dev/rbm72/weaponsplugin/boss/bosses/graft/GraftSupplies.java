package dev.rbm72.weaponsplugin.boss.bosses.graft;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the arena hands the group at the start of every phase — the §0.3 rule. Wire work needs no tool
 * at all (dust and repeaters break by hand), so what is actually scarce here is <em>reach</em>: shields
 * to walk a line under dispenser fire, and blocks to wedge a piston head or bridge back onto the floor
 * after a shove. Dropped every phase so nobody arrives at P3's three-cut window empty-handed.
 */
final class GraftSupplies {

    private GraftSupplies() {
    }

    static void dropFor(GraftFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        GraftConfig config = fight.config();

        drop(fight, at, Material.SHIELD, config.num("supply-shields-per-player", 1) * players);
        drop(fight, at, Material.COBBLESTONE, config.num("supply-blocks-per-player", 16) * players);
        drop(fight, at, Material.IRON_PICKAXE, config.num("supply-pickaxes-per-player", 1) * players);

        Fx.burst(at, Particle.ELECTRIC_SPARK, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }

    private static void drop(GraftFight fight, Location at, Material material, int amount) {
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
