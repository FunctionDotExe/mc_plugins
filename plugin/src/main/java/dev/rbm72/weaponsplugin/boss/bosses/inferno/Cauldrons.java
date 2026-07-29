package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;

import java.util.ArrayList;
import java.util.List;

/**
 * Real cauldrons, filled with real levelled water, that visibly drain as buckets are filled from them
 * and refill on a timer — §1.4's "Water buckets" row: "cauldrons visibly fill and drain... refill on a
 * timer (the contested resource)".
 * <p>
 * Draining is entirely vanilla: a player right-clicking a {@link Material#WATER_CAULDRON} with an empty
 * bucket is handled by the server with no listener needed, and it lowers the cauldron's own
 * {@link Levelled} state directly. Nothing here has to intercept that — the ledger already holds this
 * block's true pre-fight original from the one {@link Grief#setMechanicBlock} call in {@link #place()},
 * and every level change after that is just this same block's state changing, which restores correctly
 * regardless of how many times it happens in between. Refilling is the only half this class has to drive
 * by hand, since vanilla has no "cauldrons refill themselves" rule to lean on.
 */
final class Cauldrons {

    private static final int FULL_LEVEL = 3;

    private final InfernoFight fight;
    private final List<Block> spots = new ArrayList<>();
    private double refillCountdownSeconds;

    Cauldrons(InfernoFight fight) {
        this.fight = fight;
    }

    void place() {
        if (!spots.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = Math.max(1, fight.config().num("cauldron-count", 3));
        double fraction = fight.config().dbl("cauldron-fraction", 0.82);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            if (!Grief.setMechanicBlock(fight.griefContext(), block, Material.WATER_CAULDRON)) {
                continue;
            }
            setLevel(block, FULL_LEVEL);
            spots.add(block);
            Fx.coloredBurst(spot.clone().add(0.5, 0.6, 0.5), InfernoFight.STEAM, 1.2f, 18, 0.4);
            Fx.sound(spot, Sound.ITEM_BUCKET_FILL, 1.0f, 0.7f);
        }
    }

    /** Refills every drained cauldron by one level every {@code refillIntervalSeconds}. */
    void pulse(int intervalTicks) {
        double solo = fight.solo() ? fight.config().dbl("cauldron-refill-interval-seconds-solo", 4.0)
                : fight.config().dbl("cauldron-refill-interval-seconds", 7.0);
        refillCountdownSeconds -= intervalTicks / 20.0;
        if (refillCountdownSeconds > 0) {
            return;
        }
        refillCountdownSeconds = solo;
        for (Block block : spots) {
            int level = currentLevel(block);
            if (level < 0) {
                // Drained to plain, unlevelled CAULDRON — bring it back as a fresh, low-level pour.
                Grief.setMechanicBlock(fight.griefContext(), block, Material.WATER_CAULDRON);
                setLevel(block, 1);
                refillFx(block);
            } else if (level < FULL_LEVEL) {
                setLevel(block, level + 1);
                refillFx(block);
            }
        }
    }

    private void refillFx(Block block) {
        Fx.coloredBurst(block.getLocation().add(0.5, 0.7, 0.5), InfernoFight.STEAM, 1.0f, 10, 0.3);
        Fx.sound(block.getLocation(), Sound.ITEM_BUCKET_EMPTY, 0.8f, 1.2f);
    }

    private int currentLevel(Block block) {
        if (block.getType() != Material.WATER_CAULDRON || !(block.getBlockData() instanceof Levelled levelled)) {
            return -1;
        }
        return levelled.getLevel();
    }

    private void setLevel(Block block, int level) {
        if (!(block.getBlockData() instanceof Levelled levelled)) {
            return;
        }
        levelled.setLevel(Math.max(1, Math.min(levelled.getMaximumLevel(), level)));
        block.setBlockData(levelled, false);
    }

    void discardAll() {
        spots.clear();
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
