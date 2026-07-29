package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * §0.3: the arena supplies every item a mechanic depends on, so a fight is never unwinnable because
 * somebody arrived without one. This boss leans on exactly one — chorus fruit, the P4 "survival kit"
 * (§5.3) and the always-available solo self-rescue from a Banish (§5.6) — dropped at arena centre at
 * the start of every phase, the same contested-middle-of-the-room placement {@code KingSupplies} uses.
 */
final class SovereignSupplies {

    private SovereignSupplies() {
    }

    static void dropFor(SovereignFight fight) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center().add(0, 1, 0);
        int players = fight.playerCount();
        int amount = fight.config().num("supply-chorus-fruit-per-player", 2) * players;

        int max = Material.CHORUS_FRUIT.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int size = Math.min(left, max);
            left -= size;
            Item item = world.dropItem(at, new ItemStack(Material.CHORUS_FRUIT, size));
            item.setPersistent(false);
            fight.instance().trackEntity(item);
        }
        Fx.burst(at, Particle.PORTAL, 20, 0.6);
        Fx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
    }
}
