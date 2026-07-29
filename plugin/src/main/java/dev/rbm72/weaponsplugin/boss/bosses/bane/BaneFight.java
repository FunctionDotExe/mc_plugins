package dev.rbm72.weaponsplugin.boss.bosses.bane;

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
 * The Threefold Bane's fight-scoped state: the three clocks and the cover they are survived behind. The
 * clocks in particular must outlive every phase change — P4's whole point is that the finale runs at
 * whatever tempo the group left the machinery on, which is only true if the same three loops have been
 * running since P1. Same registry+watchdog pattern as {@code StormFight}.
 */
final class BaneFight {

    private static final Map<UUID, BaneFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color SOUL_BLUE = Color.fromRGB(30, 200, 210);
    static final Color DECAY_GREY = Color.fromRGB(50, 50, 55);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final BaneConfig config;
    private final AttackContext griefContext;

    private final Clocks clocks;
    private final BonePillars pillars;

    private BukkitTask watchdog;

    private BaneFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new BaneConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);
        this.clocks = new Clocks(this);
        this.pillars = new BonePillars(this);
    }

    static BaneFight of(BossInstance instance) {
        BaneFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        BaneFight fight = new BaneFight(instance);
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

    BaneConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Clocks clocks() {
        return clocks;
    }

    BonePillars pillars() {
        return pillars;
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
            if (plugin.bossManager().isLive("threefold_bane")) {
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
            clocks.discardAll();
            pillars.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Threefold Bane fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
