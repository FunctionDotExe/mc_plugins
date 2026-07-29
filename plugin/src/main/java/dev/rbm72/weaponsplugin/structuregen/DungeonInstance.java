package dev.rbm72.weaponsplugin.structuregen;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state for one generated dungeon: how many grunts are still alive per room, which mobs
 * pulse a {@link MobAbility}, and which blocks seal the treasure room until every other room is
 * cleared. Follows the fight-scoped-state pattern from the boss engine (static registry keyed by an
 * id, plus its own watchdog) but this state has no fight to end — it lives until the dungeon is
 * fully cleared, at which point it self-cancels rather than waiting on an {@code end()} call.
 * <p>
 * Deliberately outside the {@code boss/} package's grief/ledger contract: this is persistent world
 * content, not a fight arena, so it writes blocks directly (see {@link DungeonBuilder}) and tracks
 * its own mobs directly rather than through {@code AddManager}/{@code BossInstance.trackEntity},
 * which both require a live fight.
 */
final class DungeonInstance {

    private static final Map<UUID, DungeonInstance> ACTIVE = new HashMap<>();
    private static final Set<BukkitTask> WATCHDOGS = new HashSet<>();
    private static final int ABILITY_RANGE = 14;
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    private final UUID id = UUID.randomUUID();
    private final WeaponsPlugin plugin;
    private final World world;
    private final Map<Integer, Integer> aliveByRoom = new HashMap<>();
    private final Map<LivingEntity, MobAbility> abilityMobs = new HashMap<>();
    private final List<int[]> gateBlocks = new ArrayList<>();
    private boolean opened = false;
    private BukkitTask watchdog;

    private DungeonInstance(WeaponsPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    static DungeonInstance start(WeaponsPlugin plugin, World world) {
        DungeonInstance instance = new DungeonInstance(plugin, world);
        ACTIVE.put(instance.id, instance);
        instance.watchdog = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, instance::tick, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
        WATCHDOGS.add(instance.watchdog);
        return instance;
    }

    UUID id() {
        return id;
    }

    void registerRoom(int roomIndex, int mobCount) {
        aliveByRoom.put(roomIndex, mobCount);
    }

    void addGateBlock(int x, int y, int z) {
        gateBlocks.add(new int[]{x, y, z});
    }

    /** Tags {@code mob} so a later death is routed back to this dungeon's room-clear tracking. */
    void trackMob(LivingEntity mob, int roomIndex, MobAbility ability) {
        mob.getPersistentDataContainer().set(dungeonIdKey(plugin), PersistentDataType.STRING, id.toString());
        mob.getPersistentDataContainer().set(roomIndexKey(plugin), PersistentDataType.INTEGER, roomIndex);
        if (ability != MobAbility.NONE) {
            abilityMobs.put(mob, ability);
        }
    }

    private void onMobDeath(LivingEntity mob, int roomIndex) {
        abilityMobs.remove(mob);
        aliveByRoom.computeIfPresent(roomIndex, (key, count) -> count - 1);
        maybeOpenGate();
    }

    private void maybeOpenGate() {
        if (opened) {
            return;
        }
        for (int count : aliveByRoom.values()) {
            if (count > 0) {
                return;
            }
        }
        opened = true;
        for (int[] pos : gateBlocks) {
            world.getBlockAt(pos[0], pos[1], pos[2]).setType(Material.AIR, false);
        }
    }

    private void tick() {
        if (abilityMobs.isEmpty()) {
            if (opened) {
                stop();
            }
            return;
        }
        for (Map.Entry<LivingEntity, MobAbility> entry : List.copyOf(abilityMobs.entrySet())) {
            LivingEntity mob = entry.getKey();
            if (!mob.isValid() || mob.isDead()) {
                abilityMobs.remove(mob);
                continue;
            }
            Player target = nearestPlayer(mob);
            if (target != null) {
                entry.getValue().pulse(mob, target);
            }
        }
    }

    private Player nearestPlayer(LivingEntity mob) {
        Player best = null;
        double bestDistance = ABILITY_RANGE;
        for (Player player : mob.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(mob.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private void stop() {
        if (watchdog != null) {
            watchdog.cancel();
            WATCHDOGS.remove(watchdog);
        }
        ACTIVE.remove(id);
    }

    /** Routed here by {@link DungeonMobDeathListener}. A no-op if the dungeon already finished. */
    static void onMobDeath(UUID dungeonId, LivingEntity mob, int roomIndex) {
        DungeonInstance instance = ACTIVE.get(dungeonId);
        if (instance != null) {
            instance.onMobDeath(mob, roomIndex);
        }
    }

    static NamespacedKey dungeonIdKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, "dungeon_id");
    }

    static NamespacedKey roomIndexKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, "dungeon_room_index");
    }

    /** Cancels every dungeon watchdog still running — called from {@code onDisable()}. */
    static void shutdownAll() {
        for (BukkitTask task : WATCHDOGS) {
            task.cancel();
        }
        WATCHDOGS.clear();
        ACTIVE.clear();
    }
}
