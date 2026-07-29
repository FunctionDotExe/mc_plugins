package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Real soul-sand (up) and magma-block (down) columns, placed by the Leviathan on the flooded seafloor.
 * <p>
 * There is deliberately no custom push/pull code here at all. Once {@link WaterLevel} has real water
 * standing over the arena, a soul-sand or magma block on the seafloor <em>is</em> a bubble column —
 * vanilla generates the rising/sinking current and the particle animation on its own, the moment the
 * block exists underwater. This class's whole job is placing and rotating those two block types; batch
 * 2's own instruction that these are "real soul sand (up) and magma blocks (down), visually
 * unmistakable" is satisfied by the block placement being real, not by any FX layered on top of it.
 */
final class BubbleColumns {

    private final LeviathanFight fight;
    private final List<Column> columns = new ArrayList<>();

    private int soulSandTarget;
    private int magmaTarget;
    private int placeCooldown;

    private static final class Column {
        Block block;
        Material type;
    }

    BubbleColumns(LeviathanFight fight) {
        this.fight = fight;
    }

    /** Called from a phase's {@code onArm} — raises (never lowers) how many of each type should stand. */
    void ensureColumns(int soulSand, int magma) {
        soulSandTarget = Math.max(soulSandTarget, soulSand);
        magmaTarget = Math.max(magmaTarget, magma);
    }

    void pulse(int intervalTicks) {
        placeCooldown -= intervalTicks;
        if (placeCooldown > 0) {
            return;
        }
        placeCooldown = fight.config().num("columns-place-interval-ticks", 60);

        int soulSandAlive = countAlive(Material.SOUL_SAND);
        int magmaAlive = countAlive(Material.MAGMA_BLOCK);
        if (soulSandAlive < soulSandTarget) {
            placeOne(Material.SOUL_SAND);
        } else if (magmaAlive < magmaTarget) {
            placeOne(Material.MAGMA_BLOCK);
        } else if (columns.size() >= soulSandTarget + magmaTarget && !columns.isEmpty()) {
            // Both at target: refresh the oldest one so "he places them as traps" stays an ongoing
            // pressure (batch-2 §3.4 — "column count scales") rather than a one-time setup.
            recycleOldest();
        }
    }

    private void placeOne(Material type) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        double fraction = fight.config().dbl("columns-fraction-max", 0.85);
        double minFraction = fight.config().dbl("columns-fraction-min", 0.2);
        double arenaRadius = fight.instance().arena().radius();
        double dist = arenaRadius * ThreadLocalRandom.current().nextDouble(minFraction, fraction);
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        Location centre = fight.instance().arena().center();
        int floorY = fight.water().floorY() - 1;
        int x = centre.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = centre.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        Block block = world.getBlockAt(x, floorY, z);
        if (!Grief.setMechanicBlock(fight.griefContext(), block, type)) {
            return;
        }
        Column column = new Column();
        column.block = block;
        column.type = type;
        columns.add(column);
    }

    private void recycleOldest() {
        if (columns.isEmpty()) {
            return;
        }
        Column oldest = columns.remove(0);
        if (oldest.block.getType() == oldest.type) {
            Grief.setMechanicBlock(fight.griefContext(), oldest.block, Material.WATER);
        }
        placeOne(oldest.type);
    }

    private int countAlive(Material type) {
        int count = 0;
        for (Column column : columns) {
            if (column.type == type && column.block.getType() == type) {
                count++;
            }
        }
        return count;
    }

    /** P3's escape tool — "ride a soul-sand column out" of the whirlpool's pull. */
    boolean isNearSoulSandColumn(Location loc, double radius) {
        for (Column column : columns) {
            if (column.type != Material.SOUL_SAND || column.block.getType() != Material.SOUL_SAND) {
                continue;
            }
            double dx = column.block.getX() + 0.5 - loc.getX();
            double dz = column.block.getZ() + 0.5 - loc.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    void discardAll() {
        columns.clear();
        soulSandTarget = 0;
        magmaTarget = 0;
    }
}
