package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The four real {@link Material#LIGHTNING_ROD} blocks the Static Charge meter's cure
 * ({@code MeterConditions.touchingBlock}) reads directly off the world. This class only owns their
 * placement, their destruction, and their respawn — the actual "does touching this discharge you" rule
 * lives entirely in the meter, because a rod converted to air by {@link #destroyOne} simply stops
 * matching that condition with no extra bookkeeping required.
 * <p>
 * Deliberately scarce at four, whatever the group size (batch-1 §3.4: "deliberately scarce for
 * groups") — the coordination problem is exactly that four rods is not enough for five people to lean
 * on individually.
 */
final class LightningRods {

    private final StormFight fight;
    private final List<Block> blocks = new ArrayList<>();

    LightningRods(StormFight fight) {
        this.fight = fight;
    }

    /** Places (or replaces) all four rods. Idempotent per call — clears any survivors first. */
    void raiseAll() {
        discardAll();
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = Math.max(1, fight.config().num("rod-count", 4));
        double fraction = fight.config().dbl("rod-placement-fraction", 0.6);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block support = world.getBlockAt(spot.getBlockX(), spot.getBlockY() - 1, spot.getBlockZ());
            if (support.getType().isAir()) {
                Grief.setMechanicBlock(fight.griefContext(), support, Material.IRON_BLOCK);
            }
            Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            if (Grief.setMechanicBlock(fight.griefContext(), block, Material.LIGHTNING_ROD)) {
                blocks.add(block);
                Fx.coloredBurst(spot.clone().add(0, 1, 0), StormFight.STORM_YELLOW, 1.4f, 20, 0.4);
                Fx.sound(spot, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.3f);
            }
        }
    }

    /** Live rod blocks, for a phase that needs to track per-rod state (P4's burn-out-after-one-use). */
    List<Block> liveBlocks() {
        List<Block> live = new ArrayList<>();
        for (Block block : blocks) {
            if (block.getType() == Material.LIGHTNING_ROD) {
                live.add(block);
            }
        }
        return live;
    }

    /** Burns a specific rod out, rather than a random one — see {@link #destroyOne} for the random form. */
    void burnOut(Block rod) {
        Grief.setMechanicBlock(fight.griefContext(), rod, Material.AIR);
        Location at = rod.getLocation().add(0.5, 0.5, 0.5);
        Fx.burst(at, org.bukkit.Particle.SMOKE, 24, 0.3);
        Fx.sound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.8f);
    }

    int liveCount() {
        int live = 0;
        for (Block block : blocks) {
            if (block.getType() == Material.LIGHTNING_ROD) {
                live++;
            }
        }
        return live;
    }

    int total() {
        return blocks.size();
    }

    /** The Tyrant destroying one of his own rods — P2's "rods can be destroyed by the Tyrant". */
    void destroyOne() {
        List<Block> live = new ArrayList<>();
        for (Block block : blocks) {
            if (block.getType() == Material.LIGHTNING_ROD) {
                live.add(block);
            }
        }
        if (live.isEmpty()) {
            return;
        }
        Block target = live.get(ThreadLocalRandom.current().nextInt(live.size()));
        Grief.setMechanicBlock(fight.griefContext(), target, Material.AIR);
        Location at = target.getLocation().add(0.5, 0.5, 0.5);
        Fx.burst(at, org.bukkit.Particle.SMOKE, 30, 0.4);
        Fx.sound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.3f, 0.6f);
    }

    void pulse() {
        for (Block block : blocks) {
            if (block.getType() != Material.LIGHTNING_ROD) {
                continue;
            }
            Fx.coloredBurst(block.getLocation().add(0.5, 1.3, 0.5), StormFight.STORM_YELLOW, 1.0f, 3, 0.2);
        }
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
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()));
        return spot;
    }
}
