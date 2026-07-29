package dev.rbm72.weaponsplugin.boss.bosses.graft;

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
 * The Grafted Horror's fight-scoped state: the graft modules and their circuits, which have to outlive
 * every phase change intact. Same registry+watchdog pattern as {@code StormFight} — grafts are
 * cumulative across phases (§1.3), so a per-phase object would throw away the wire the group has spent
 * the last two minutes learning every time the health band ticks over.
 */
final class GraftFight {

    private static final Map<UUID, GraftFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color RUST = Color.fromRGB(150, 70, 35);
    static final Color SPARK_RED = Color.fromRGB(255, 60, 40);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final GraftConfig config;
    private final AttackContext griefContext;
    private final Grafts grafts;

    private BukkitTask watchdog;

    private GraftFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new GraftConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);
        this.grafts = new Grafts(this);
    }

    static GraftFight of(BossInstance instance) {
        GraftFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        GraftFight fight = new GraftFight(instance);
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

    GraftConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Grafts grafts() {
        return grafts;
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
            if (plugin.bossManager().isLive("grafted_horror")) {
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
            grafts.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Grafted Horror fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
