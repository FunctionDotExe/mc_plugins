package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * §0.3: the arena supplies every item a mechanic depends on. This boss leans on exactly one — real
 * water-filled buckets, "bucket count = 2 + 1 per player" (§1.4 Water buckets row) — dropped at arena
 * centre at the start of every phase, same placement convention as {@code KingSupplies}/
 * {@code StormSupplies}, so a group is never caught with an empty inventory mid-fight and the bucket
 * economy resets to a known baseline at every phase transition.
 */
final class InfernoSupplies {

    private InfernoSupplies() {
    }

    static void dropFor(InfernoFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        int amount = fight.config().num("supply-buckets-base", 2) + fight.config().num("supply-buckets-per-player", 1) * players;

        int left = amount;
        while (left > 0) {
            left--;
            Item item = world.dropItem(at, new ItemStack(Material.WATER_BUCKET, 1));
            item.setPersistent(false);
            fight.instance().trackEntity(item);
        }
        Fx.burst(at, Particle.SPLASH, 20, 0.5);
        Fx.sound(at, Sound.ITEM_BUCKET_FILL, 1.0f, 0.8f);
    }
}
