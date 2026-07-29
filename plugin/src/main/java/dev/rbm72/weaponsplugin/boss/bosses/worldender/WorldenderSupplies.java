package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * The thread running the whole fight (batch-5 §0): each phase drops the tool that counters it, and those
 * tools persist for the rest of the fight — by phase 8 the group is carrying an improvised kit assembled
 * from seven survivals. One drop call per phase, at that phase's {@code onArm}; §0.3's "the arena supplies
 * every item a mechanic depends on" rule applied per phase rather than all at once, so nobody reaches a
 * phase with an empty inventory for it.
 */
final class WorldenderSupplies {

    private WorldenderSupplies() {
    }

    static void leatherBoots(WorldenderFight fight) {
        drop(fight, Material.LEATHER_BOOTS, 1, "boots");
    }

    static void lightningRod(WorldenderFight fight) {
        drop(fight, Material.LIGHTNING_ROD, 1, "lightning_rod");
    }

    static void waterBuckets(WorldenderFight fight) {
        int perPlayer = fight.config().num("supply-buckets-per-player", 2);
        drop(fight, Material.WATER_BUCKET, Math.max(1, perPlayer * fight.playerCount()), "water_buckets");
    }

    static void pyreKit(WorldenderFight fight) {
        drop(fight, Material.FLINT_AND_STEEL, 1, "pyre_kit");
        drop(fight, Material.COAL, 4, "pyre_kit");
    }

    static void chorusFruit(WorldenderFight fight) {
        int perPlayer = fight.config().num("supply-chorus-fruit-per-player", 3);
        drop(fight, Material.CHORUS_FRUIT, Math.max(1, perPlayer * fight.playerCount()), "chorus_fruit");
    }

    static void repeater(WorldenderFight fight) {
        drop(fight, Material.REPEATER, Math.max(2, fight.playerCount()), "repeater");
    }

    private static void drop(WorldenderFight fight, Material material, int amount, String toolId) {
        World world = fight.world();
        if (world == null || amount <= 0) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int max = material.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int size = Math.min(left, max);
            left -= size;
            Item item = world.dropItem(at, new ItemStack(material, size));
            item.setPersistent(false);
            fight.instance().trackEntity(item);
        }
        fight.grantTool(toolId);
        Fx.burst(at, Particle.END_ROD, 18, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.1f);
    }
}
