package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

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
 * The Voidwyrm's fight-scoped state: the flee/cornering rig P1 drives, the underground travel P2
 * drives, the coiling segment chain P3 (and P4's decorative ring) shares, and the swallow/interior
 * pocket P4 drives. One per live fight, same registry+watchdog pattern as {@code StormFight} — several
 * of these (the segment chain in particular) have to survive from P3 into P4 intact.
 */
final class WyrmFight {

    private static final Map<UUID, WyrmFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color VOID_PURPLE = Color.fromRGB(110, 20, 160);
    static final Color STARLIGHT = Color.fromRGB(220, 180, 255);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final WyrmConfig config;
    private final AttackContext griefContext;

    private final EvasiveRig rig;
    private final Burrow burrow;
    private final WyrmSegments segments;
    private final Interior interior;

    private BukkitTask watchdog;

    private WyrmFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new WyrmConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);

        this.rig = new EvasiveRig(this);
        this.burrow = new Burrow(this);
        this.segments = new WyrmSegments(this);
        this.interior = new Interior(this);
    }

    static WyrmFight of(BossInstance instance) {
        WyrmFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        WyrmFight fight = new WyrmFight(instance);
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

    WyrmConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    EvasiveRig rig() {
        return rig;
    }

    Burrow burrow() {
        return burrow;
    }

    WyrmSegments segments() {
        return segments;
    }

    Interior interior() {
        return interior;
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
            if (plugin.bossManager().isLive("voidwyrm")) {
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
            rig.discard();
            burrow.discardAll();
            segments.discardAll();
            interior.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Voidwyrm fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
