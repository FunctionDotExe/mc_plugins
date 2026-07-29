package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>P4's Moat Eruption channel</b> — Inferno Warlord's player-authored terrain (batch-5 §3, P4: "ground
 * is no longer something you have; it is something you make"), trimmed to the single rule the design
 * asks for: lava floods the floor, and real water poured on it becomes stone — permanently exempt from
 * ever flooding again, the platform the group built rather than a delay tactic. Registered for the whole
 * fight from {@link WorldenderFight}'s constructor (mirroring {@code RisingLava}), so pouring water
 * before P4 ever floods anything is simply a no-op — the tile map is empty until {@link #floodEverything}
 * runs.
 */
final class LavaFloor implements Listener {

    private enum Tile { LAVA, PLAYER_MADE }

    private static final int MAX_TILES_PER_TICK_BATCH = 60;

    private final WorldenderFight fight;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private final ArrayDeque<long[]> pending = new ArrayDeque<>();
    private BukkitTask batchTask;
    private int playerMadeCount;
    private boolean listening;

    LavaFloor(WorldenderFight fight) {
        this.fight = fight;
        fight.plugin().getServer().getPluginManager().registerEvents(this, fight.plugin());
        listening = true;
    }

    int playerMadeCount() {
        return playerMadeCount;
    }

    /** P4's opening beat: everything outside {@code keepFraction} of the radius floods at once. */
    void floodEverything(double keepFraction) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location centre = fight.instance().arena().center();
        double radius = fight.instance().arena().radius();
        double rim = radius * fight.config().dbl("burning-rim-fraction", 0.97);
        double inner = radius * Math.max(0.0, Math.min(1.0, keepFraction));
        int ir = (int) Math.ceil(rim);
        double rim2 = rim * rim;
        double inner2 = inner * inner;
        int cx = centre.getBlockX();
        int cz = centre.getBlockZ();
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                double d2 = x * x + z * z;
                if (d2 > rim2 || d2 < inner2) {
                    continue;
                }
                long key = key(cx + x, cz + z);
                if (tiles.get(key) == Tile.PLAYER_MADE) {
                    continue;
                }
                pending.add(new long[] {cx + x, cz + z});
            }
        }
        startBatchIfNeeded();
        Fx.coloredRing(centre, WorldenderFight.INFERNO, 1.8f, rim, 48, 0);
        Fx.sound(centre, Sound.BLOCK_LAVA_AMBIENT, 1.4f, 0.5f);
    }

    private void startBatchIfNeeded() {
        if (batchTask != null || pending.isEmpty()) {
            return;
        }
        batchTask = fight.plugin().getServer().getScheduler().runTaskTimer(fight.plugin(), () -> {
            World world = fight.world();
            if (world == null) {
                return;
            }
            int placed = 0;
            while (placed < MAX_TILES_PER_TICK_BATCH && !pending.isEmpty()) {
                long[] col = pending.poll();
                fillColumn(world, (int) col[0], (int) col[1]);
                placed++;
            }
            if (pending.isEmpty() && batchTask != null) {
                batchTask.cancel();
                batchTask = null;
            }
        }, 1L, 1L);
        fight.instance().trackTask(batchTask);
    }

    private void fillColumn(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y, z);
        Block above = ground.getRelative(0, 1, 0);
        if (!above.getType().isAir() && above.getType() != Material.LAVA) {
            return;
        }
        if (Grief.setBlock(fight.griefContext(), above, Material.LAVA)) {
            tiles.put(key(x, z), Tile.LAVA);
        }
    }

    /** True when {@code player} is standing on a column the group converted — P6's callback reads this. */
    boolean standingOnPlayerMade(Player player) {
        Block under = player.getLocation().getBlock().getRelative(0, -1, 0);
        return tiles.get(key(under.getX(), under.getZ())) == Tile.PLAYER_MADE
                && under.getType() == Material.STONE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        Block target = event.getBlock();
        long key = key(target.getX(), target.getZ());
        if (tiles.get(key) != Tile.LAVA) {
            return;
        }
        event.setCancelled(true);
        if (!Grief.setMechanicBlock(fight.griefContext(), target, Material.STONE)) {
            return;
        }
        tiles.put(key, Tile.PLAYER_MADE);
        playerMadeCount++;
        fight.instance().recordProgress();
        fight.vibration().registerLoud(event.getPlayer(), fight.config().dbl("burning-pour-loudness", 1.2));
        consumeBucket(event);

        Location at = target.getLocation().add(0.5, 0.5, 0.5);
        Fx.burst(at, Particle.CLOUD, 24, 0.4);
        Fx.sound(at, Sound.BLOCK_STONE_PLACE, 1.1f, 1.0f);
        Fx.sound(at, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
    }

    private void consumeBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack empty = new ItemStack(Material.BUCKET);
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(empty);
        } else {
            player.getInventory().setItemInMainHand(empty);
        }
    }

    void discardAll() {
        if (batchTask != null) {
            batchTask.cancel();
            batchTask = null;
        }
        pending.clear();
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
    }

    private static long key(int x, int z) {
        return ((long) (x & 0x3FFFFFFF) << 32) | (z & 0xFFFFFFFFL);
    }
}
