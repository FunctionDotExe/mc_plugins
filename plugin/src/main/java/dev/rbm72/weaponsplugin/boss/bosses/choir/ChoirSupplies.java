package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the arena hands the group at the start of every phase — the §0.3 rule, with one honest caveat.
 * §4.4 lists torches as the answer to this boss's Darkness, but §5.3's later deconfliction ruling makes
 * the Choir's Darkness the <em>supernatural</em> kind, which no light source cures; the Weeping Colossus
 * owns the version torches answer. Both cannot be true, and the ruling is the later and more specific
 * one, so it wins.
 * <p>
 * Torches are still dropped, and still worth carrying: they light the arena between waves, so the group
 * can see the note-block ring and each other in the gaps. They just do not cure the Darkness itself —
 * the counterplay to that is audio, which is the whole point of the boss.
 */
final class ChoirSupplies {

    private ChoirSupplies() {
    }

    static void dropFor(ChoirFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        ChoirConfig config = fight.config();

        drop(fight, at, Material.TORCH, config.num("supply-torches-per-player", 8) * players);
        drop(fight, at, Material.LANTERN, config.num("supply-lanterns-per-player", 2) * players);
        drop(fight, at, Material.COOKED_BEEF, config.num("supply-food-per-player", 4) * players);

        Fx.burst(at, Particle.NOTE, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }

    private static void drop(ChoirFight fight, Location at, Material material, int amount) {
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
