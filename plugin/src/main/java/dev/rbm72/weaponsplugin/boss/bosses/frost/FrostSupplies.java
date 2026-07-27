package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the throne room hands the group, dropped at arena centre at the start of every phase — the same
 * §0.3 rule as {@code KingSupplies}: no mechanic here may assume a player prepared correctly.
 * <p>
 * Leather boots make Powder Snow (P2's Avalanche craters) survivable to walk on rather than a guaranteed
 * freeze; flint and steel and torches are the fire a player carries to the Heart in P3, and doing double
 * duty as the fastest available Chill cure the whole fight through. Dropped at the point that becomes
 * the Heart's own footprint in P3, so the walk to resupply and the walk to deliver fire are the same trip.
 */
final class FrostSupplies {

    private FrostSupplies() {
    }

    static void dropFor(FrostFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.heartSpot().add(0, 1, 0);
        int players = fight.playerCount();
        FrostConfig config = fight.config();

        drop(fight, at, Material.LEATHER_BOOTS, config.num("supply-boots-per-player", 1) * players);
        drop(fight, at, Material.FLINT_AND_STEEL, config.num("supply-flint-and-steel-per-player", 1) * players);
        drop(fight, at, Material.TORCH, config.num("supply-torches-per-player", 2) * players);

        Fx.burst(at, Particle.FLAME, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }

    private static void drop(FrostFight fight, Location at, Material material, int amount) {
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
