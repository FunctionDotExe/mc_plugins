package dev.rbm72.weaponsplugin.boss.bosses.colossus;

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
 * The Solar Colossus's fight-scoped state: its joints, the kneel/climb/shake-off cycle, the beacons
 * it plants in P3, and the pillars/debris it sheds on the ground. One per live fight, shared by all
 * four phases.
 * <p>
 * It exists for the same reason {@code NecroFight}/{@code SovereignFight}/{@code StormFight} exist: a
 * joint broken in P1 (an ankle) is still meaningfully "broken" bookkeeping in P3 when a completed
 * beacon charge repairs it, and {@link Joints} has to be the one object every phase's mechanic reaches
 * through rather than four independent copies that would disagree with each other the instant a
 * repair fired. See {@code NecroFight}'s header for the full explanation of why this registers itself
 * under the boss entity's id and runs an <em>untracked</em> watchdog rather than tearing down from a
 * mechanic's {@code onStop} (a phase change and a fight end are indistinguishable from inside a single
 * mechanic, and a tracked watchdog would already be cancelled by the time fight-end teardown needs it).
 */
final class ColossusFight {

    private static final Map<UUID, ColossusFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);
    static final Color RADIANT_WHITE = Color.fromRGB(255, 250, 220);
    static final Color CORE_RED = Color.fromRGB(255, 90, 60);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final ColossusConfig config;
    private final AttackContext griefContext;

    private final Joints joints;
    private final KneelCycle kneelCycle;
    private final Beacons beacons;
    private final Pillars pillars;
    private final DebrisField debris;
    private final ArmSweep armSweep;

    private BukkitTask watchdog;

    private ColossusFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new ColossusConfig(plugin);
        // Same oddity NecroFight documents: Grief's writes only ever read ctx.instance() to reach the
        // ledger, and every block this boss places (scaffolding starters, beacon pedestals, settled
        // pillar/debris material) is mechanic upkeep with no attack and no victim behind it.
        this.griefContext = new AttackContext(plugin, instance, null);

        this.joints = new Joints(this);
        this.kneelCycle = new KneelCycle(this);
        this.beacons = new Beacons(this);
        this.pillars = new Pillars(this);
        this.debris = new DebrisField(this);
        this.armSweep = new ArmSweep(this);
    }

    static ColossusFight of(BossInstance instance) {
        ColossusFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        ColossusFight fight = new ColossusFight(instance);
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

    ColossusConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Joints joints() {
        return joints;
    }

    KneelCycle kneelCycle() {
        return kneelCycle;
    }

    Beacons beacons() {
        return beacons;
    }

    Pillars pillars() {
        return pillars;
    }

    DebrisField debris() {
        return debris;
    }

    ArmSweep armSweep() {
        return armSweep;
    }

    World world() {
        return instance.arena().world();
    }

    /** Combatants only — spectators/creative never inflate a count that drives mechanic scaling. */
    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    List<Player> combatants() {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    boolean solo() {
        return playerCount() <= 1;
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("solar_colossus")) {
                return;
            }
            release();
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    /**
     * Drops every prop this fight still holds. Idempotent, and deliberately does not undo block work —
     * scaffolding starters, beacon pedestals and settled pillar/debris material are all in the arena
     * ledger, which restores them wholesale a moment after this runs.
     */
    private void release() {
        ACTIVE.remove(instance.entity().getUniqueId());
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        try {
            joints.discardAll();
            kneelCycle.discard();
            beacons.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Solar Colossus fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
