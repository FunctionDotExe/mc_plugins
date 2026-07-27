package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the arena hands the group, dropped on the floor at the start of every phase.
 * <p>
 * This boss depends on four verbs a player may simply not have brought the tools for: walling a lane
 * off, climbing to something high, mining a bone pile, and shooting an anchor down. The roster rule is
 * that a fight is never unwinnable because somebody arrived with an empty inventory, and never assumes
 * preparation — so the materials are part of the encounter, replenished each phase.
 * <p>
 * Scarcity is still the interesting part: the stacks are modest, they are dropped in one contested spot
 * in the middle of the arena rather than handed out, and picking them up means leaving the chokepoint.
 * <p>
 * Everything dropped is registered with the fight so none of it outlives it.
 */
final class ArenaSupplies {

    private ArenaSupplies() {
    }

    /**
     * Drops one phase's worth of supplies at the arena centre. Scales with head count so a five-player
     * group is not fighting over one player's worth of cobble — a count/coverage scale, not a power one.
     */
    static void dropFor(NecroFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        NecroConfig config = fight.config();

        drop(fight, at, Material.COBBLESTONE, config.num("supply-blocks-per-player", 32) * players);
        drop(fight, at, Material.SCAFFOLDING, config.num("supply-scaffolding-per-player", 16) * players);
        drop(fight, at, Material.IRON_PICKAXE, config.num("supply-pickaxes-per-player", 1) * players);
        drop(fight, at, Material.BOW, config.num("supply-bows-per-player", 1) * players);
        drop(fight, at, Material.ARROW, config.num("supply-arrows-per-player", 32) * players);

        Fx.burst(at, Particle.SCULK_SOUL, 20, 0.6);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.8f);
    }

    private static void drop(NecroFight fight, Location at, Material material, int amount) {
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
