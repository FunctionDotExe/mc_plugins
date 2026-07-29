package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the arena hands the group at the start of every phase — the §0.3 rule, and this boss leans on it
 * harder than most: P3's darkness is real block light, so torches and lanterns are the counterplay, and
 * wedging a piston face needs blocks to wedge with. Light supply scales with players (§5.4).
 */
final class WeepingSupplies {

    private WeepingSupplies() {
    }

    static void dropFor(WeepingFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        WeepingConfig config = fight.config();

        drop(fight, at, Material.TORCH, config.num("supply-torches-per-player", 12) * players);
        drop(fight, at, Material.LANTERN, config.num("supply-lanterns-per-player", 2) * players);
        drop(fight, at, Material.COBBLESTONE, config.num("supply-blocks-per-player", 24) * players);

        Fx.burst(at, Particle.SPLASH, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }

    private static void drop(WeepingFight fight, Location at, Material material, int amount) {
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
