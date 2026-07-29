package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Weeping Colossus's fight-scoped state: the four wall sections and how far each has advanced, the
 * dripstone the ceiling has already dropped, and the sealed roof. The walls above all must outlive phase
 * changes — the room's shrinkage is monotonic across the whole fight and P4's chamber is whatever the
 * group left, which is only true if one object has owned the geometry since P1. Same registry+watchdog
 * pattern as {@code StormFight}.
 */
final class WeepingFight {

    private static final Map<UUID, WeepingFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    /** How far either side of the spawn-time floor {@link #floorY} will look for a column's real surface. */
    private static final int FLOOR_SCAN_UP = 3;
    private static final int FLOOR_SCAN_DOWN = 6;

    /**
     * Everything this fight itself stands on the chamber floor. {@link #floorY} skips all of it, so a
     * column that already holds a wall course, a piston, a redstone feed or a fallen spike still reports
     * the floor underneath rather than the top of the fight's own furniture.
     */
    private static final Set<Material> FIGHT_BUILT = Set.of(
            Material.DEEPSLATE_BRICKS, Material.PISTON, Material.STICKY_PISTON,
            Material.REDSTONE_BLOCK, Material.REDSTONE_WIRE, Material.POINTED_DRIPSTONE);

    static final Color SORROW_BLUE = Color.fromRGB(70, 120, 200);
    static final Color PALE_STONE = Color.fromRGB(200, 210, 225);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final WeepingConfig config;
    private final AttackContext griefContext;

    private final PistonWalls walls;
    private final Dripstone dripstone;
    private final SealedCeiling ceiling;

    /** The chamber floor as it stood before this fight built anything — see {@link #floorY}. */
    private final int baseFloorY;

    private BukkitTask watchdog;

    private WeepingFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new WeepingConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);
        this.walls = new PistonWalls(this);
        this.dripstone = new Dripstone(this);
        this.ceiling = new SealedCeiling(this);

        Location centre = instance.arena().center();
        World world = centre.getWorld();
        this.baseFloorY = world != null
                ? world.getHighestBlockYAt(centre.getBlockX(), centre.getBlockZ())
                : centre.getBlockY();
    }

    static WeepingFight of(BossInstance instance) {
        WeepingFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        WeepingFight fight = new WeepingFight(instance);
        ACTIVE.put(instance.entity().getUniqueId(), fight);
        fight.startWatchdog();
        return fight;
    }

    // ------------------------------------------------------------------ access

    WeaponsPlugin plugin() {
        return plugin;
    }

    BossInstance instance() {
        return instance;
    }

    WeepingConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    PistonWalls walls() {
        return walls;
    }

    Dripstone dripstone() {
        return dripstone;
    }

    SealedCeiling ceiling() {
        return ceiling;
    }

    World world() {
        return instance.arena().world();
    }

    /**
     * The chamber floor's height in column {@code (x, z)} — the anchor every wall course, redstone feed,
     * dripstone column and light sweep in this fight measures from.
     * <p>
     * <b>Not {@code World#getHighestBlockYAt}, which is what this replaced and what was wrong.</b> That
     * reports the highest block in the column including the ones the fight itself just put there, and
     * {@link SealedCeiling} lays a solid roof over the whole chamber in P3 — so from that moment on, the
     * "highest block" in every roofed column <em>is</em> the ceiling. The walls and their feed were laid
     * on top of the roof (out of reach, making P3's jam objective unreachable and leaving it to the
     * floor-lock timeout), the dripstone grew above it, and the torch sweep counted lights in the four
     * blocks above the roof instead of the room below it — permanently zero, so the Colossus never snuffed
     * anything and P3's whole contested-light beat silently did nothing. The feed caused a smaller version
     * of the same thing every advance: the new course ran through columns whose top block was a redstone
     * feed, so those sections of wall sat a block high with a gap under them.
     * <p>
     * Scanned rather than fixed so an uneven arena floor still reads per column, and {@link #FIGHT_BUILT}
     * is skipped so the fight's own furniture is never mistaken for ground.
     */
    int floorY(int x, int z) {
        World world = world();
        if (world == null) {
            return baseFloorY;
        }
        for (int y = baseFloorY + FLOOR_SCAN_UP; y >= baseFloorY - FLOOR_SCAN_DOWN; y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type.isSolid() && !FIGHT_BUILT.contains(type)) {
                return y;
            }
        }
        return baseFloorY;
    }

    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    List<Player> combatants() {
        return Arena.combatants(instance.arena().center(), instance.arena().radius());
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("weeping_colossus")) {
                return;
            }
            release();
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    private void release() {
        ACTIVE.remove(instance.entity().getUniqueId());
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        try {
            walls.discardAll();
            dripstone.discardAll();
            ceiling.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Weeping Colossus fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
