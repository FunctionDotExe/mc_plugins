package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The cover Convergence is survivable behind. §2.4 says the counter to a Convergence is "be behind
 * cover, or have desynced the clocks beforehand" — which requires the arena to actually contain cover,
 * so the Bane grows it: real bone pillars, breakable, at mid-arena.
 * <p>
 * Made of bone rather than something indestructible on purpose. A pillar can be broken (by a player
 * clearing a firing lane, or by the fight's own skull volleys), so cover is a resource the group spends
 * and repositions around rather than a permanently safe tile — which is what keeps §2.6's anti-camping
 * clause honest.
 */
final class BonePillars {

    private final BaneFight fight;
    private final List<Block> blocks = new ArrayList<>();

    BonePillars(BaneFight fight) {
        this.fight = fight;
    }

    void raise() {
        if (!blocks.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = Math.max(2, fight.config().num("pillar-count", 5));
        int height = Math.max(2, fight.config().num("pillar-height", 3));
        double fraction = fight.config().dbl("pillar-placement-fraction", 0.55);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            for (int y = 0; y < height; y++) {
                Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY() + y, spot.getBlockZ());
                if (Grief.setMechanicBlock(fight.griefContext(), block, Material.BONE_BLOCK)) {
                    blocks.add(block);
                }
            }
            Fx.burst(spot.clone().add(0, 1, 0), Particle.BLOCK_CRUMBLE, 18, 0.4);
            Fx.sound(spot, Sound.BLOCK_BONE_BLOCK_PLACE, 1.1f, 0.7f);
        }
    }

    /** How much cover is left standing — the readout a group needs before a Convergence lands. */
    int standing() {
        int standing = 0;
        for (Block block : blocks) {
            if (block.getType() == Material.BONE_BLOCK) {
                standing++;
            }
        }
        return standing;
    }

    void discardAll() {
        blocks.clear();
    }

    private Location surfaceSpot(double angleRadians, double fraction) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }
}
