package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Worldender's fight-scoped state: the vibration model that has to survive all eight phases (batch-5
 * §2 — "one constant, eight variables"), the toolkit ledger tracking which of the seven survival tools
 * the group has actually been handed, and the per-phase terrain collaborators (ice, charge arcs, lava,
 * rifts, clocks) that each phase builds lazily and the later phases can still query for their callbacks
 * (P6 reads whether the group still has water buckets left over from P4; P7's clocks re-trigger P2/P3/P4's
 * verbs directly). Same registry+watchdog pattern as every other reworked boss's fight-scoped state.
 */
final class WorldenderFight {

    private static final Map<UUID, WorldenderFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color VOID_PURPLE = Color.fromRGB(140, 40, 200);
    static final Color SCULK_TEAL = Color.fromRGB(60, 200, 190);
    static final Color FROST = Color.fromRGB(150, 230, 255);
    static final Color STORM = Color.fromRGB(255, 240, 120);
    static final Color INFERNO = Color.fromRGB(255, 120, 40);
    static final Color PLAGUE = Color.fromRGB(120, 200, 60);
    static final Color UNMAKING = Color.fromRGB(220, 40, 80);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final WorldenderConfig config;
    private final AttackContext griefContext;

    private final Vibration vibration;
    private final IceFloor ice;
    private final LavaFloor lava;
    private final Rifts rifts;
    private final Clocks clocks;

    /** The tools the arena has actually dropped so far — P8's kit requirement reads this directly. */
    private final Set<String> toolsGranted = new HashSet<>();

    private BukkitTask watchdog;

    private WorldenderFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new WorldenderConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);

        this.vibration = new Vibration(this);
        this.ice = new IceFloor(this);
        this.lava = new LavaFloor(this);
        this.rifts = new Rifts(this);
        this.clocks = new Clocks(this);
    }

    static WorldenderFight of(BossInstance instance) {
        WorldenderFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        WorldenderFight fight = new WorldenderFight(instance);
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

    WorldenderConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Vibration vibration() {
        return vibration;
    }

    IceFloor ice() {
        return ice;
    }

    LavaFloor lava() {
        return lava;
    }

    Rifts rifts() {
        return rifts;
    }

    Clocks clocks() {
        return clocks;
    }

    World world() {
        return instance.arena().world();
    }

    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    List<Player> combatants() {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    /** Marks a tool as granted so a later phase's kit check (P8) can see it was actually handed out. */
    void grantTool(String toolId) {
        toolsGranted.add(toolId);
    }

    boolean hasTool(String toolId) {
        return toolsGranted.contains(toolId);
    }

    int toolsGrantedCount() {
        return toolsGranted.size();
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("worldender")) {
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
            vibration.discardAll();
            lava.discardAll();
            rifts.discardAll();
            clocks.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Worldender fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
