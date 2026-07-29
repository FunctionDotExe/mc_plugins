package dev.rbm72.weaponsplugin.boss.grief;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.util.BlockGuard;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Undo log for everything a fight does to the world.
 * <p>
 * The roster is designed around bosses that flood arenas with lava, freeze the floor into ice, delete
 * it outright, and bury it in fallen anvils — which is only survivable as a server feature because
 * every one of those changes is written down here first and rolled back when the fight ends. Restore
 * is what <em>licenses</em> the destruction: the previous policy (permanent, unbounded grief) had to
 * keep attacks timid because every crater was forever, and repeated fights in one place ground the
 * arena down to an unusable pit.
 * <p>
 * Three rules make the rollback trustworthy, and every one of them exists because the alternative
 * silently corrupts a world:
 * <ul>
 *   <li><b>First write wins.</b> A block's <em>original</em> state is captured the first time anything
 *       touches it and never overwritten. Recording the newest state instead would mean a block hit
 *       twice restores to whatever the first attack turned it into, not to what the player built.</li>
 *   <li><b>Tile entities are refused outright.</b> A chest, sign, spawner or shulker box is never
 *       recorded and therefore never modified — {@link BlockData} carries no inventory or text, so
 *       restoring one would hand back an empty husk. Refusing the write keeps player storage
 *       untouchable by any boss, which is worth more than the handful of blocks it costs.</li>
 *   <li><b>The budget is a hard stop, not a warning.</b> Once {@link #maxBlocks} distinct blocks are
 *       recorded the ledger refuses every further modification, so a fight can never damage more of
 *       the world than it is able to put back. Grief scope and restore capacity are deliberately the
 *       same number.</li>
 * </ul>
 * One ledger per {@link dev.rbm72.weaponsplugin.boss.BossInstance}, restored exactly once on fight end.
 */
public final class ArenaLedger {

    /** Blocks restored per tick when rolling back gradually — bounds the restore's own lag spike. */
    private static final int DEFAULT_BLOCKS_PER_TICK = 4000;

    private final WeaponsPlugin plugin;
    private final boolean enabled;
    private final int maxBlocks;
    private final int blocksPerTick;

    /** Packed block position -> the state that was there before this fight touched it. */
    private final Map<Long, BlockData> originals = new HashMap<>();

    private World world;
    private boolean budgetExhausted;
    private boolean restoreStarted;

    public ArenaLedger(WeaponsPlugin plugin, boolean enabled, int maxBlocks, int blocksPerTick) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.maxBlocks = Math.max(0, maxBlocks);
        this.blocksPerTick = blocksPerTick > 0 ? blocksPerTick : DEFAULT_BLOCKS_PER_TICK;
    }

    /** True when this fight rolls its damage back. False means the old permanent-grief behaviour. */
    public boolean enabled() {
        return enabled;
    }

    /** Distinct blocks currently held for restore. */
    public int size() {
        return originals.size();
    }

    /**
     * True once the ledger has hit its budget and started refusing writes — the fight's destruction is
     * capped from here on. Surfaced so a boss can log it rather than players quietly noticing that
     * craters stopped appearing.
     */
    public boolean budgetExhausted() {
        return budgetExhausted;
    }

    /**
     * Asks permission to modify {@code block}, recording its current state first.
     *
     * @return {@code true} if the caller may go ahead and change it. {@code false} means the block is
     *         protected (tile entity) or the fight is out of restore budget — the caller must skip it
     *         entirely rather than modify something it cannot undo.
     */
    public boolean record(Block block) {
        if (block == null) {
            return false;
        }
        // Restore off: the fight is permitted to change anything and nothing is written down. This is
        // the pre-ledger behaviour, kept only for a server that deliberately wants permanent scars.
        if (!enabled) {
            return true;
        }
        if (restoreStarted) {
            return false;
        }
        if (!BlockGuard.isRestorable(block)) {
            return false;
        }
        World blockWorld = block.getWorld();
        if (world == null) {
            world = blockWorld;
        } else if (!world.equals(blockWorld)) {
            // A fight only ever owns one world; a stray cross-world write would be unrestorable
            // because restore walks a single world. Refusing it is the safe read.
            return false;
        }

        long key = key(block.getX(), block.getY(), block.getZ());
        if (originals.containsKey(key)) {
            return true;
        }
        if (originals.size() >= maxBlocks) {
            if (!budgetExhausted) {
                budgetExhausted = true;
                plugin.getLogger().warning(() -> "Arena ledger hit its " + maxBlocks
                        + "-block budget — further terrain damage in this fight is being refused so it stays fully restorable.");
            }
            return false;
        }
        originals.put(key, block.getBlockData());
        return true;
    }

    /**
     * True if this fight has already recorded {@code block} — i.e. the block standing there now was put
     * there by the fight, and the state the ledger will restore is something else.
     * <p>
     * Used by {@link LedgerDropListener} to stop a fight's own props from being farmed. A boss that
     * places a hundred bone blocks the players are supposed to mine is otherwise handing out a hundred
     * free bone blocks, because the ledger puts the original floor back afterwards regardless.
     */
    public boolean tracks(Block block) {
        if (!enabled || block == null || world == null || !world.equals(block.getWorld())) {
            return false;
        }
        return originals.containsKey(key(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * Records every block a pending explosion is about to destroy, dropping any it cannot cover from
     * the supplied list so those blocks survive the blast untouched.
     * <p>
     * Real explosions are the one path that damages terrain without going through {@link Grief}, so
     * without this hook every {@code createExplosion} call — and every real TNT the roster lights —
     * would punch permanent holes straight through the rollback.
     */
    public void recordExplosion(List<Block> blockList) {
        blockList.removeIf(block -> !record(block));
    }

    /**
     * Puts back just the blocks this fight changed within {@code radius} of {@code centre}, and forgets
     * them.
     * <p>
     * This is the counterplay primitive behind "pillar-break" and "footing-fix": a boss drop that answers
     * an ice encasement, a stone pillar or a frozen floor does it by <em>undoing the boss's terrain</em>,
     * not by breaking blocks. The distinction matters — breaking them would leave the ledger holding undo
     * entries for positions that no longer contain what it wrote, so fight-end restore would then put the
     * boss's ice back on top of whatever was there by the end. Removing the entry as we restore it is what
     * keeps the two consistent.
     * <p>
     * Deliberately does nothing once the full restore has started: at that point the fight is over and the
     * whole arena is coming back anyway.
     *
     * @return how many blocks were put back.
     */
    public int restoreNear(Location centre, double radius) {
        if (!enabled || restoreStarted || centre == null || world == null
                || !world.equals(centre.getWorld()) || originals.isEmpty()) {
            return 0;
        }
        double radiusSq = radius * radius;
        List<Long> hit = new ArrayList<>();
        for (Long key : originals.keySet()) {
            double dx = x(key) + 0.5 - centre.getX();
            double dy = y(key) + 0.5 - centre.getY();
            double dz = z(key) + 0.5 - centre.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                hit.add(key);
            }
        }
        for (Long key : hit) {
            BlockData original = originals.remove(key);
            if (original != null) {
                world.getBlockAt(x(key), y(key), z(key)).setBlockData(original, false);
            }
        }
        return hit.size();
    }

    /**
     * Puts the world back. Safe to call twice; the second call does nothing.
     *
     * @param immediate restore synchronously in one pass. Required on plugin disable, where no further
     *                  ticks will ever run and a batched restore would silently never finish — leaving
     *                  the arena permanently wrecked precisely when the server is being shut down.
     */
    public void restore(boolean immediate) {
        if (!enabled || restoreStarted) {
            return;
        }
        restoreStarted = true;
        if (originals.isEmpty() || world == null) {
            return;
        }
        if (immediate) {
            restoreBatch(new ArrayList<>(originals.keySet()), 0, originals.size());
            originals.clear();
            liftTrappedPlayers();
            return;
        }

        List<Long> keys = new ArrayList<>(originals.keySet());
        // Deliberately NOT registered with BossInstance.trackTask: the instance cancels every tracked
        // task as the first step of ending the fight, which is the same moment this is scheduled. A
        // tracked restore task would be cancelled before restoring a single block.
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int cursor;

            @Override
            public void run() {
                int end = Math.min(keys.size(), cursor + blocksPerTick);
                restoreBatch(keys, cursor, end);
                cursor = end;
                if (cursor >= keys.size()) {
                    originals.clear();
                    liftTrappedPlayers();
                    if (holder[0] != null) {
                        holder[0].cancel();
                    }
                }
            }
        }, 1L, 1L);
    }

    private void restoreBatch(List<Long> keys, int from, int to) {
        for (int i = from; i < to; i++) {
            long key = keys.get(i);
            BlockData original = originals.get(key);
            if (original == null) {
                continue;
            }
            Block block = world.getBlockAt(x(key), y(key), z(key));
            // applyPhysics=false throughout: a rollback that fires physics would make restored sand
            // and gravel fall, restored water flow, and torches pop off — the restore would visibly
            // fail to match what it was restoring, and would keep churning long after it finished.
            block.setBlockData(original, false);
        }
    }

    /**
     * Nudges anyone standing inside newly-restored terrain up onto open ground. A fight routinely ends
     * with players down in a crater or inside a rift the ledger is about to fill back in, and the
     * alternative to this is suffocating a player as their reward for winning.
     */
    private void liftTrappedPlayers() {
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            Block feet = at.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            if (!feet.getType().isSolid() && !head.getType().isSolid()) {
                continue;
            }
            Location safe = at.clone();
            safe.setY(world.getHighestBlockYAt(at.getBlockX(), at.getBlockZ()) + 1);
            player.teleport(safe);
        }
    }

    /**
     * Packs a block position into one long so the ledger can hold six figures of them without an
     * object per entry. 26 bits each for x/z and 12 for y covers the full world border and the whole
     * -64..320 build range with room to spare.
     */
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | ((y + 2048L) & 0xFFFL);
    }

    /** x lives in the top 26 bits, so a plain arithmetic shift sign-extends it back correctly. */
    private static int x(long key) {
        return (int) (key >> 38);
    }

    /** z is the middle 26 bits: shift its high bit up to bit 63 first so the same sign extension works. */
    private static int z(long key) {
        return (int) (key << 26 >> 38);
    }

    private static int y(long key) {
        return (int) (key & 0xFFFL) - 2048;
    }
}
