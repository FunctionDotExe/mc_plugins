package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Tide Leviathan's fight-scoped state: the water level, the conduit network, the bubble columns, the
 * whirlpool, the guardians and the breath system. One per live fight, shared by all four phases — same
 * registry+untracked-watchdog pattern as {@code NecroFight}/{@code StormFight}/{@code SovereignFight}.
 * <p>
 * This exists because almost everything this boss does outlives a single phase: the water level reached
 * in P1 is exactly where P2 continues rising from, a conduit placed in P1 is still standing (or not) in
 * P3, and the whirlpool's break count has to survive whatever health-band churn happens while the group
 * is racing to ride a third soul-sand column out. Rebuilding any of it per phase would either erase
 * earned progress at every transition or measure the wrong window entirely.
 * <p>
 * The watchdog is deliberately <b>untracked</b> — a task registered via {@link BossInstance#trackTask}
 * is already cancelled by the time a fight-end teardown needs to run (see {@code NecroFight}'s own
 * javadoc for the full reasoning), so a tracked watchdog here would never fire.
 */
final class LeviathanFight {

    static final Color TEAL = Color.fromRGB(40, 200, 200);
    static final Color PALE = Color.fromRGB(160, 240, 255);
    static final Color DEEP_TEAL = Color.fromRGB(10, 90, 130);

    private static final Map<UUID, LeviathanFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final LeviathanConfig config;
    private final AttackContext griefContext;

    private final WaterLevel water;
    private final Conduits conduits;
    private final BubbleColumns columns;
    private final Whirlpool whirlpool;
    private final Guardians guardians;
    private final Air air;

    private BukkitTask watchdog;

    private LeviathanFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new LeviathanConfig(plugin);
        // Same oddity NecroFight/StormFight accept: Grief's writes only ever read ctx.instance() to
        // reach the ledger, and none of this boss's terrain work has an attacker or a victim behind it.
        this.griefContext = new AttackContext(plugin, instance, null);

        this.water = new WaterLevel(this);
        this.conduits = new Conduits(this);
        this.columns = new BubbleColumns(this);
        this.whirlpool = new Whirlpool(this);
        this.guardians = new Guardians(this);
        this.air = new Air(this);
    }

    static LeviathanFight of(BossInstance instance) {
        LeviathanFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        LeviathanFight fight = new LeviathanFight(instance);
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

    LeviathanConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    WaterLevel water() {
        return water;
    }

    Conduits conduits() {
        return conduits;
    }

    BubbleColumns columns() {
        return columns;
    }

    Whirlpool whirlpool() {
        return whirlpool;
    }

    Guardians guardians() {
        return guardians;
    }

    Air air() {
        return air;
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

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("tide_leviathan")) {
                return;
            }
            release();
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    /**
     * Drops every prop this fight still holds. Idempotent, and deliberately does <em>not</em> undo
     * block work itself — the pedestals, columns and the whole flooded volume are all in the arena
     * ledger, which restores them wholesale a moment after this runs.
     */
    private void release() {
        ACTIVE.remove(instance.entity().getUniqueId());
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        try {
            conduits.discardAll();
            columns.discardAll();
            whirlpool.discardAll();
            guardians.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Tide Leviathan fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
