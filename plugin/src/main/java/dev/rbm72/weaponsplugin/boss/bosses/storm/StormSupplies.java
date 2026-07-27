package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * What the arena hands the group at the start of every phase — the same §0.3 rule as
 * {@code KingSupplies}/{@code FrostSupplies}. Wind charges and scaffolding are P3's verticality answer
 * (batch-1 §3.3: "build up temporary scaffolding towers that he blows down"); dropped every phase rather
 * than only in P3 so nobody reaches the height check with an empty inventory.
 */
final class StormSupplies {

    private StormSupplies() {
    }

    static void dropFor(StormFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        StormConfig config = fight.config();

        drop(fight, at, Material.WIND_CHARGE, config.num("supply-wind-charges-per-player", 3) * players);
        drop(fight, at, Material.SCAFFOLDING, config.num("supply-scaffolding-per-player", 16) * players);

        Fx.burst(at, Particle.ELECTRIC_SPARK, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }

    private static void drop(StormFight fight, Location at, Material material, int amount) {
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
