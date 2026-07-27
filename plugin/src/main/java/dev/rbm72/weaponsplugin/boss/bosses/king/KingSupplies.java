package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the throne room hands the group, dropped at its centre at the start of every phase.
 * <p>
 * §0.3: no mechanic may assume a player prepared correctly, and no fight is unwinnable because somebody
 * arrived with an empty inventory. This boss leans on three items nobody brings to a boss fight on
 * purpose — a pickaxe to cut a chained teammate loose in seconds rather than in a slow punch, blocks to
 * raise a column when the arena has not yet dropped enough anvils to hide behind, and a shield for the
 * player who intends to stand in front of an Execution charge.
 * <p>
 * Scarcity is still the interesting part: modest stacks, in one contested spot in the middle of the
 * room, which is exactly where a chained Challenger and a shard carrier both do not want to be.
 * Everything dropped is registered with the fight so none of it outlives it.
 */
final class KingSupplies {

    private KingSupplies() {
    }

    static void dropFor(KingFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        KingConfig config = fight.config();

        drop(fight, at, Material.IRON_PICKAXE, config.num("supply-pickaxes-per-player", 1) * players);
        drop(fight, at, Material.SHIELD, config.num("supply-shields-per-player", 1) * players);
        drop(fight, at, Material.COBBLESTONE, config.num("supply-blocks-per-player", 24) * players);
        drop(fight, at, Material.COOKED_BEEF, config.num("supply-food-per-player", 8) * players);

        Fx.burst(at, Particle.END_ROD, 20, 0.6);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.8f);
    }

    private static void drop(KingFight fight, Location at, Material material, int amount) {
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
            // Registered so the fight's own teardown removes anything nobody picked up — a boss must
            // never leave loose items lying in an arena it is finished with.
            fight.instance().trackEntity(item);
        }
    }
}
