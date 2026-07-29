package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * §0.3: the arena supplies every item this boss's counterplay leans on. This is the one boss in the
 * roster where ranged is mandatory rather than optional, so bows and arrows are not a nice-to-have —
 * a group that shows up with none still needs a way to shoot the wing membranes and take the baseline
 * damage source the whole fight is balanced around. Dropped at the arena centre at the start of every
 * phase, same contested-middle-of-the-room placement every other converted boss uses.
 * <p>
 * Batch-2 §4.5: "1 player: the arena supplies arrows generously" — solo gets a flat multiplier on top
 * of the per-player rate rather than just the one player's share of it, since a solo group has nobody
 * to split ammunition with and no melee partner to lean on between shots.
 */
final class ArenaSupplies {

    private ArenaSupplies() {
    }

    static void dropFor(DragonFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        DragonConfig config = fight.config();

        double soloBonus = players <= 1 ? config.dbl("supply-solo-bonus-multiplier", 2.0) : 1.0;
        int arrows = (int) Math.round(config.num("supply-arrows-per-player", 24) * players * soloBonus);
        int bows = config.num("supply-bows-per-player", 1) * players;

        drop(fight, at, Material.BOW, bows);
        drop(fight, at, Material.ARROW, arrows);

        Fx.burst(at, Particle.FLAME, 18, 0.5);
        Fx.sound(at, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.9f);
    }

    private static void drop(DragonFight fight, Location at, Material material, int amount) {
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
