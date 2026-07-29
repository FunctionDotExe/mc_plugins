package dev.rbm72.weaponsplugin.items.kit;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.util.BlockGuard;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The weapon-side answer to {@link dev.rbm72.weaponsplugin.boss.grief.ArenaLedger}: terrain a player's
 * ability lays down, which puts itself back after a few seconds.
 * <p>
 * <b>Why this had to exist before the doctrine pass could start.</b> Batch-1 §0.1 says an ability must
 * name the Minecraft object that produces its experience — real ice on the floor, a real wall of stone,
 * real fire — and that a particle may never <em>be</em> the mechanic. Bosses could already obey that
 * because a fight owns an arena, an undo log and an end. A weapon owns none of those: it fires in the
 * open world, and there is no "fight end" at which anything gets rolled back. So every weapon written
 * before this class had exactly two options — grief the world permanently, or draw a particle ring and
 * call {@code damage()} — and it always picked the particle ring. The debt is a missing undo log, not
 * 57 authors making the same mistake.
 * <p>
 * The undo log here is keyed on time instead of on a fight: every write carries a lifetime, and the
 * block reverts to exactly what was there before when it expires. That makes aggressive terrain safe
 * for an ability on a 7-second cooldown — an ice sheet that lasts 6 seconds is a real mechanic players
 * slide on, and it is also gone before anyone can build with it.
 * <p>
 * Three rules, each one the difference between a working feature and a quietly corrupted world:
 * <ul>
 *   <li><b>First write wins, TTL extends.</b> A position's original state is captured once. Two
 *       overlapping abilities hitting the same block push its revert time out; neither one gets to
 *       record the other's block as the "original", which would strand it there forever.</li>
 *   <li><b>Nothing unrestorable is touched.</b> {@link BlockGuard} decides, so a weapon and a boss
 *       refuse exactly the same blocks — chests, signs, spawners and bedrock are untouchable.</li>
 *   <li><b>Inside a live fight, the fight's ledger records first.</b> Both logs then hold the same
 *       pre-state, so whichever restores first is correct and the second is a no-op. Skipping this is
 *       the one ordering bug that would make weapon terrain permanent: the arena ledger would capture
 *       a weapon's ice as the floor's original state and dutifully "restore" it at fight end.</li>
 * </ul>
 */
public final class TempTerrain {

    /** Reverts processed per tick, so a burst of expiries never lands as one visible hitch. */
    private static final int REVERTS_PER_TICK = 512;

    private final WeaponsPlugin plugin;
    private final int maxBlocks;

    /** World id + packed position -> what was there before, and when to put it back. */
    private final Map<Key, Entry> tracked = new HashMap<>();

    private record Key(java.util.UUID world, long position) {
    }

    private static final class Entry {
        final BlockData original;
        long revertAtMs;

        Entry(BlockData original, long revertAtMs) {
            this.original = original;
            this.revertAtMs = revertAtMs;
        }
    }

    public TempTerrain(WeaponsPlugin plugin) {
        this.plugin = plugin;
        this.maxBlocks = Math.max(0, plugin.getConfig().getInt("weapons.terrain.max-blocks", 6000));
    }

    /** Starts the expiry sweep. Called once from {@code onEnable}. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 20L, 1L);
    }

    /** Distinct blocks currently held for revert, across every world. */
    public int size() {
        return tracked.size();
    }

    /**
     * Replaces {@code block} with {@code material} for {@code lifetimeTicks}, then puts back exactly
     * what was there.
     *
     * @param caster whose ability this is — used only to find the boss fight, if any, whose ledger also
     *               needs to know about the write
     * @return true if the block was changed. False means it was refused (unrestorable, out of budget) and
     *         the caller must treat that position as untouched rather than assume its effect landed.
     */
    public boolean place(Player caster, Block block, Material material, int lifetimeTicks) {
        return place(caster, block, material.createBlockData(), lifetimeTicks);
    }

    /** As {@link #place(Player, Block, Material, int)}, for a material that needs specific block data (stair facing, snow layers, waterlogging). */
    public boolean place(Player caster, Block block, BlockData data, int lifetimeTicks) {
        if (block == null || data == null || lifetimeTicks <= 0) {
            return false;
        }
        if (!BlockGuard.isRestorable(block)) {
            return false;
        }

        long revertAt = System.currentTimeMillis() + lifetimeTicks * 50L;
        Key key = key(block);
        Entry existing = tracked.get(key);

        if (existing != null) {
            // Already ours. Extend rather than re-record: the state standing here now is something a
            // previous ability put there, and capturing it as the original would make it permanent.
            existing.revertAtMs = Math.max(existing.revertAtMs, revertAt);
            block.setBlockData(data, false);
            return true;
        }

        if (tracked.size() >= maxBlocks) {
            return false;
        }
        // The fight's ledger gets the pre-state before we overwrite it, so a fight-end restore and a
        // TTL revert agree on what "before" means. Refusal here is a refusal outright: if the arena
        // cannot put this block back, a weapon has no business changing it inside that arena.
        if (!recordWithCoveringFight(block)) {
            return false;
        }

        tracked.put(key, new Entry(block.getBlockData(), revertAt));
        // applyPhysics=false throughout, matching the arena ledger: a weapon dropping an ice sheet must
        // not also start cascades of falling gravel and flowing water through blocks nothing recorded.
        block.setBlockData(data, false);
        return true;
    }

    /**
     * True if this block is temporary terrain some weapon laid down.
     * <p>
     * The honest answer to "can I mine it?" — a block that is about to revert must not drop an item or
     * count as a real resource, or every ice-sheet ability is also a free-ice generator.
     */
    public boolean tracks(Block block) {
        return block != null && tracked.containsKey(key(block));
    }

    /**
     * Reverts everything within {@code radius} of {@code centre} right now, whatever its remaining
     * lifetime.
     * <p>
     * This is the pillar-break / footing-fix primitive: {@link Counterplay} clears a player out of ice
     * or out from behind a wall by asking for the terrain back early, rather than by breaking blocks and
     * leaving the ledger holding entries for positions that no longer contain what it wrote.
     *
     * @return how many blocks were put back.
     */
    public int revertNear(Location centre, double radius) {
        if (centre == null || centre.getWorld() == null) {
            return 0;
        }
        double radiusSq = radius * radius;
        java.util.UUID worldId = centre.getWorld().getUID();
        List<Key> expired = new ArrayList<>();
        for (Map.Entry<Key, Entry> entry : tracked.entrySet()) {
            Key key = entry.getKey();
            if (!key.world().equals(worldId)) {
                continue;
            }
            double dx = x(key.position()) + 0.5 - centre.getX();
            double dy = y(key.position()) + 0.5 - centre.getY();
            double dz = z(key.position()) + 0.5 - centre.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                expired.add(key);
            }
        }
        for (Key key : expired) {
            revert(key);
        }
        return expired.size();
    }

    /**
     * Puts every tracked block back immediately. Called from {@code onDisable}, where no further ticks
     * will run and a block still waiting on its TTL would otherwise stay in the world forever — the
     * exact permanent grief this class exists to prevent, arriving via a server restart.
     */
    public void revertAll() {
        for (Key key : new ArrayList<>(tracked.keySet())) {
            revert(key);
        }
    }

    private void sweep() {
        if (tracked.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int budget = REVERTS_PER_TICK;
        List<Key> due = new ArrayList<>();
        for (Map.Entry<Key, Entry> entry : tracked.entrySet()) {
            if (entry.getValue().revertAtMs > now) {
                continue;
            }
            due.add(entry.getKey());
            if (--budget <= 0) {
                break;
            }
        }
        for (Key key : due) {
            revert(key);
        }
    }

    private void revert(Key key) {
        Entry entry = tracked.remove(key);
        if (entry == null) {
            return;
        }
        World world = plugin.getServer().getWorld(key.world());
        if (world == null) {
            // World unloaded while the block was live. Nothing to put back and nothing to leak — the
            // entry is gone, and the world's own region file still holds the original state.
            return;
        }
        world.getBlockAt(x(key.position()), y(key.position()), z(key.position()))
                .setBlockData(entry.original, false);
    }

    /**
     * Gives the fight covering this block, if any, the chance to record its pre-state first.
     * <p>
     * Fails open only when there is no fight: a live fight that <em>refuses</em> (out of ledger budget,
     * already restoring) is a hard no, because a block the arena cannot restore is a block a weapon
     * must not touch inside that arena.
     */
    private boolean recordWithCoveringFight(Block block) {
        try {
            if (plugin.bossManager() == null) {
                return true;
            }
            BossInstance covering = plugin.bossManager().instanceCovering(block.getLocation()).orElse(null);
            return covering == null || covering.ledger().record(block);
        } catch (Exception e) {
            // A partially-initialised boss manager during startup/shutdown must not decide that a
            // weapon may write a block nothing is holding an undo for. Refuse and lose the effect.
            plugin.getLogger().warning("Temp terrain refused a write: could not consult the covering fight — " + e);
            return false;
        }
    }

    private static Key key(Block block) {
        return new Key(block.getWorld().getUID(), pack(block.getX(), block.getY(), block.getZ()));
    }

    /** Same 26/26/12-bit packing the arena ledger uses — full world border, full -64..320 build range. */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | ((y + 2048L) & 0xFFFL);
    }

    private static int x(long key) {
        return (int) (key >> 38);
    }

    private static int z(long key) {
        return (int) (key << 26 >> 38);
    }

    private static int y(long key) {
        return (int) (key & 0xFFFL) - 2048;
    }
}
