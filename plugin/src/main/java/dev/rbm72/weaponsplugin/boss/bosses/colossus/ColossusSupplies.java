package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * §0.3: the arena supplies every item a mechanic depends on. This boss's whole P2 hinges on one — real
 * scaffolding, dropped at the Colossus's feet the instant it kneels, so the group builds its own route
 * up rather than climbing a structure the fight pre-built for them (see {@code KneelCycle}'s header for
 * why player-placed scaffolding beats a fight-authored tower). Ladders ride along as the fallback for
 * anyone who would rather brace against the Colossus's own frame than stack scaffolding under their feet.
 * <p>
 * "Scaffolding supply = 1 stack per player" (batch-2 §2.4) — dropped as one 64-stack per combatant
 * rather than a single pooled pile, so a group doesn't have to fight over who gets to climb.
 */
final class ColossusSupplies {

    private ColossusSupplies() {
    }

    static void dropForKneel(ColossusFight fight, Location at) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int players = fight.playerCount();
        int stacks = fight.config().num("scaffolding-stacks-per-player", 1) * players;
        for (int i = 0; i < stacks; i++) {
            drop(fight, at, Material.SCAFFOLDING, 64);
        }
        drop(fight, at, Material.LADDER, Math.max(16, players * 8));
        Fx.burst(at.clone().add(0, 0.5, 0), Particle.CLOUD, 24, 0.6);
        Fx.sound(at, Sound.BLOCK_SCAFFOLDING_BREAK, 1.0f, 0.8f);
    }

    private static void drop(ColossusFight fight, Location at, Material material, int amount) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        int max = material.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int size = Math.min(left, max);
            left -= size;
            Item item = world.dropItem(at.clone().add(0, 0.5, 0), new ItemStack(material, size));
            item.setPersistent(false);
            fight.instance().trackEntity(item);
        }
    }
}
