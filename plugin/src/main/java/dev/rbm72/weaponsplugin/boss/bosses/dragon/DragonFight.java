package dev.rbm72.weaponsplugin.boss.bosses.dragon;

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
 * The Dragon Elder's fight-scoped state: the aerial rig, its four perch pillars, its wing-membrane
 * meter and its continuous fireball spawner. One per live fight, same registry+untracked-watchdog
 * pattern as {@code NecroFight}/{@code StormFight}/{@code SovereignFight} — every one of these outlives
 * individual phase transitions (a pillar broken in P2 must stay broken for P3's cover reckoning; the
 * membrane pool and the deflection count are explicitly "all fight" per batch-2 §4.4), so none of it can
 * live inside a single {@code PhaseMechanic} without being rebuilt (and losing the group's progress)
 * every time a phase changes.
 * <p>
 * <b>Lifecycle.</b> Registers itself under the boss entity's id and runs one <em>untracked</em> watchdog
 * that tears everything down once {@code BossManager} no longer lists the fight as live —
 * {@link BossInstance#end} cancels every <em>tracked</em> task as its first step, so a tracked watchdog
 * would already be dead before it could notice the fight ended.
 */
final class DragonFight {

    private static final Map<UUID, DragonFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);
    static final Color EMBER = Color.fromRGB(255, 130, 30);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final DragonConfig config;
    private final AttackContext griefContext;

    private final AerialRig aerial;
    private final PerchPillars pillars;
    private final WingMembranes membranes;
    private final Fireballs fireballs;
    private final FireLanes fireLanes;

    private BukkitTask watchdog;

    private DragonFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new DragonConfig(plugin);
        // Grief's block writes only ever read ctx.instance() to reach the ledger; pillar/fire-lane work
        // has no attack and no victim behind it, so — same reasoning as every other fight-scoped grief
        // context in this roster — the target slot is legitimately empty.
        this.griefContext = new AttackContext(plugin, instance, null);

        this.aerial = new AerialRig(this);
        this.pillars = new PerchPillars(this);
        this.membranes = new WingMembranes(this);
        this.fireballs = new Fireballs(this);
        this.fireLanes = new FireLanes(this);

        aerial.start();
        membranes.arm();
    }

    static DragonFight of(BossInstance instance) {
        DragonFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        DragonFight fight = new DragonFight(instance);
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

    DragonConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    AerialRig aerial() {
        return aerial;
    }

    PerchPillars pillars() {
        return pillars;
    }

    WingMembranes membranes() {
        return membranes;
    }

    Fireballs fireballs() {
        return fireballs;
    }

    FireLanes fireLanes() {
        return fireLanes;
    }

    World world() {
        return instance.arena().world();
    }

    /** Combatants only — spectators and creative players never inflate a count that drives mechanic scaling. */
    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    List<Player> combatants() {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("dragon_elder")) {
                return;
            }
            release();
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    /**
     * Drops every prop this fight still holds and stops the aerial rig's own tick task. Deliberately
     * does <em>not</em> undo block work directly: the pillars and any standing fire lane are all written
     * through the {@code Grief} primitives into the arena ledger, which restores them wholesale a moment
     * after this runs.
     */
    private void release() {
        ACTIVE.remove(instance.entity().getUniqueId());
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        try {
            aerial.discard();
            pillars.discardAll();
            membranes.discard();
            fireballs.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Dragon Elder fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
