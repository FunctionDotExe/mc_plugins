package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Fallen King's fight-scoped state: the duel, his court, the crown he is about to lose, the bell on
 * his dais, and the anvils his Judgment leaves embedded in the floor. One per live fight, shared by all
 * four phases.
 * <p>
 * It exists for the same reason the Overlord's does — this boss's state genuinely outlives a phase. The
 * anvils that P3 drops are the cover P4's Execution is solved with; the throne dais and its bell are
 * placed once and referred to by three different phases; and the Challenger rotation must survive a
 * phase change intact, because a boss that forgot who it was duelling every time its health crossed a
 * seam would hand the group a free reset on the one mechanic it is built around.
 * <p>
 * <b>Lifecycle.</b> Registered under the boss entity's id and torn down by one <em>untracked</em>
 * watchdog once {@code BossManager} stops listing the fight as live. Untracked deliberately:
 * {@link BossInstance#end} cancels every tracked task as its first step, so a tracked watchdog would be
 * dead before it could notice the fight was over.
 */
final class KingFight {

    private static final Map<UUID, KingFight> ACTIVE = new HashMap<>();

    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color KING_GOLD = Color.fromRGB(212, 175, 55);
    static final Color KING_SHADOW = Color.fromRGB(60, 0, 90);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final KingConfig config;
    private final AttackContext griefContext;

    /**
     * Where the throne stands: a fixed point at the arena's edge, chosen once. Fixed rather than
     * boss-relative because two mechanics (seating the shards, ringing the bell) are trips the group has
     * to plan, and an objective that walks around with the boss is not a trip, it is a coincidence.
     */
    private final Location dais;

    private final Duel duel;
    private final Court court;
    private final CrownShards shards;
    private final ThroneBell bell;
    private final Judgment judgment;

    private BukkitTask watchdog;

    private KingFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new KingConfig(plugin);
        // Grief's block writes only read ctx.instance() to reach the ledger; there is no attack and no
        // victim behind a throne dais or a landed anvil, so the target slot is legitimately empty.
        this.griefContext = new AttackContext(plugin, instance, null);
        this.dais = pickDais();

        this.duel = new Duel(this);
        this.court = new Court(this, duel);
        this.shards = new CrownShards(this);
        this.bell = new ThroneBell(this);
        this.judgment = new Judgment(this);
    }

    static KingFight of(BossInstance instance) {
        KingFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        KingFight fight = new KingFight(instance);
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

    KingConfig config() {
        return config;
    }

    /**
     * The context every block write in this fight is routed through, so all of it lands in the arena
     * ledger and comes back out again when the fight ends. His writes use
     * {@link dev.rbm72.weaponsplugin.boss.grief.Grief#setMechanicBlock} rather than {@code setBlock}: a
     * throne, a bell and the chains rooting a player are the mechanics themselves, not damage done to
     * the world, and gating them on the server's grief switch would leave three phases with nothing for
     * players to touch.
     */
    AttackContext griefContext() {
        return griefContext;
    }

    Duel duel() {
        return duel;
    }

    Court court() {
        return court;
    }

    CrownShards shards() {
        return shards;
    }

    ThroneBell bell() {
        return bell;
    }

    Judgment judgment() {
        return judgment;
    }

    /** The throne dais — where shards are seated and the bell hangs. Fixed for the whole fight. */
    Location dais() {
        return dais.clone();
    }

    World world() {
        return instance.arena().world();
    }

    /** Combatants only — spectators and creative players never inflate a count that drives scaling. */
    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    /** Everyone in the fight right now, measured off the boss so a drifted fight still sees its group. */
    java.util.List<org.bukkit.entity.Player> combatants() {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    // ---------------------------------------------------------------- geometry

    /**
     * Puts the throne two thirds of the way out from the arena centre, on the surface. Deliberately not
     * at the centre: the dais is meant to be somewhere you have to <em>go</em>, and a throne in the
     * middle of the room is a throne you are already standing on.
     */
    private Location pickDais() {
        Location centre = instance.arena().center();
        World world = centre.getWorld();
        if (world == null) {
            return centre;
        }
        double distance = instance.arena().radius() * config.dbl("dais-fraction", 0.66);
        Location spot = centre.clone().add(distance, 0, 0);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("fallen_king")) {
                return;
            }
            release();
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    /**
     * Drops every prop this fight still holds and unhooks the bell's listener. Idempotent, and
     * deliberately does <em>not</em> undo block work: the dais, the bell, the chains and every landed
     * anvil are in the arena ledger, which restores them wholesale a moment after this runs.
     */
    private void release() {
        ACTIVE.remove(instance.entity().getUniqueId());
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        try {
            duel.clear();
            court.discardAll();
            shards.discardAll();
            bell.discard();
        } catch (Exception e) {
            // A failed teardown must never propagate into the scheduler and kill the timer that is
            // still trying to finish the rest of it.
            plugin.getLogger().log(Level.SEVERE,
                    "Fallen King fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }

    // ------------------------------------------------------------- shared cues

    /** The gilded flourish every one of his physical props announces itself with. */
    static void royalFlourish(Location loc, Sound sound, float pitch) {
        Fx.coloredBurst(loc.clone().add(0, 0.9, 0), KING_GOLD, 1.6f, 26, 0.5);
        Fx.burst(loc.clone().add(0, 0.9, 0), Particle.END_ROD, 14, 0.4);
        Fx.sound(loc, sound, 1.0f, pitch);
    }
}
