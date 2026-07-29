package dev.rbm72.weaponsplugin.boss.bosses.bulk;

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
 * The Amalgamated Bulk's fight-scoped state: the catalyst nodes, the shed Bulklings and their kill
 * arithmetic, and the sculk floor. All three have to outlive a phase change — P2 exits on coverage the
 * group built up during P1, and P3's reabsorptions target Bulklings shed two phases earlier — so the same
 * registry+watchdog pattern as {@code StormFight} applies.
 */
final class BulkFight {

    private static final Map<UUID, BulkFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color OOZE_GREEN = Color.fromRGB(90, 170, 70);
    static final Color SCULK_TEAL = Color.fromRGB(20, 130, 130);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final BulkConfig config;
    private final AttackContext griefContext;

    private final Catalysts catalysts;
    private final SculkFloor sculk;
    private final Bulklings bulklings;

    private BukkitTask watchdog;

    private BulkFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new BulkConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);
        this.catalysts = new Catalysts(this);
        this.sculk = new SculkFloor(this);
        this.bulklings = new Bulklings(this);
    }

    static BulkFight of(BossInstance instance) {
        BulkFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        BulkFight fight = new BulkFight(instance);
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

    BulkConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Catalysts catalysts() {
        return catalysts;
    }

    SculkFloor sculk() {
        return sculk;
    }

    Bulklings bulklings() {
        return bulklings;
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
            if (plugin.bossManager().isLive("amalgamated_bulk")) {
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
            bulklings.discardAll();
            sculk.discardAll();
            catalysts.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Amalgamated Bulk fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
