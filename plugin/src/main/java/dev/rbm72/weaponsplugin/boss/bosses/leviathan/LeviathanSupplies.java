package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * §0.3: the arena supplies every item this fight's counterplay depends on. The only one this boss
 * needs is a real {@code CONDUIT} item — every conduit is placed by a player carrying one to an empty
 * pedestal (see {@link Conduits}), so without a steady supply the fight's whole spine would be
 * unplayable by anyone who didn't bring their own.
 * <p>
 * Dropped at the start of every phase, sized to exactly how many pedestals are currently empty — a
 * conduit the Leviathan just smashed is re-suppliable on the very next phase check, satisfying "arena
 * -supplied... replenished per phase" without ever handing out more than the fight can currently use.
 */
final class LeviathanSupplies {

    private LeviathanSupplies() {
    }

    static void dropFor(LeviathanFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int needed = fight.conduits().inactiveSlotCount();
        if (needed <= 0) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        Item item = world.dropItem(at, new ItemStack(Material.CONDUIT, needed));
        item.setPersistent(false);
        fight.instance().trackEntity(item);
        Fx.burst(at, Particle.BUBBLE, 24, 0.6);
        Fx.sound(at, Sound.BLOCK_CONDUIT_AMBIENT, 1.0f, 1.0f);
    }
}
