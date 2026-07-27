package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Necro Overlord's fight-scoped state: his sky, his army, the corpses it leaves and the graves it
 * comes out of. One per live fight, shared by all four phases.
 * <p>
 * It exists because this boss is the one encounter in the roster whose state genuinely outlives a
 * phase. A corpse pile raised in P1 is still physically on the floor in P3 and still on its rise
 * timer; the daylight the group bought by breaking the shroud in P2 is precisely what makes P4
 * survivable. Rebuilding any of that per phase would either delete the group's earned progress at
 * every transition or, worse, make P3's "clear the corpse floor" objective measure a floor that only
 * started filling ten seconds ago.
 * <p>
 * <b>Lifecycle.</b> {@link BossInstance} has no per-fight state bag to hang this off, and a
 * {@code PhaseMechanic}'s {@code stop()} cannot tell a phase change from the fight ending — so a
 * mechanic tearing this down in {@code onStop} would wipe it on every transition. Instead this
 * registers itself under the boss entity's id and runs one <em>untracked</em> watchdog that tears
 * everything down once {@code BossManager} no longer lists the fight as live. Untracked deliberately:
 * {@link BossInstance#end} cancels every tracked task as its first step, so a tracked watchdog would
 * be dead before it could ever notice the fight was over — the same reasoning that keeps
 * {@code ArenaLedger}'s restore task off the tracked list.
 */
final class NecroFight {

    private static final Map<UUID, NecroFight> ACTIVE = new HashMap<>();

    private static final long WATCHDOG_INTERVAL_TICKS = 10L;
    /** Columns sampled for sky access — centre plus four at half radius. Cheap, and enough to tell a roofed arena from an open one. */
    private static final double[][] SKY_SAMPLES = {{0, 0}, {0.5, 0}, {-0.5, 0}, {0, 0.5}, {0, -0.5}};
    private static final byte FULL_SKYLIGHT = 15;

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final NecroConfig config;
    private final AttackContext griefContext;

    private final UndeadHorde horde;
    private final CorpseField corpses;
    private final GraveMarkers graves;

    /**
     * Whether this world has a day/night clock at all. Only {@code NORMAL} worlds do — calling
     * {@code setTime} on a NETHER or THE_END world throws "Cannot set time in world without world
     * clock", which is the same trap {@code RealmManager} already had to guard when it set up realms.
     */
    private final boolean skyClock;
    /** Whether the arena floor actually sees the sky, which is what decides if vanilla sunlight can reach the horde. */
    private final boolean openSky;
    private final long originalFullTime;

    private boolean daylight;
    private BukkitTask watchdog;

    private NecroFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new NecroConfig(plugin);
        // Grief's block writes only ever read ctx.instance() to reach the ledger; there is no attack and
        // no victim behind a canopy or a corpse pile, so the target slot is legitimately empty. One
        // cached context rather than a fresh one per block keeps that oddity to a single place.
        this.griefContext = new AttackContext(plugin, instance, null);

        World world = instance.arena().world();
        this.skyClock = world != null && world.getEnvironment() == World.Environment.NORMAL;
        this.openSky = detectOpenSky();
        this.originalFullTime = world != null ? world.getFullTime() : 0L;

        this.corpses = new CorpseField(this);
        this.horde = new UndeadHorde(this, corpses);
        this.graves = new GraveMarkers(this, horde);

        // He is holding the sky in an artificial night before anyone walks in — that is his whole
        // identity, and it is also the only way P1 plays the same wherever the arena is. Fought at noon
        // in the open world without this, his horde would be on fire before it finished walking in and
        // there would be nothing left for the shroud to take away in P2.
        forceTime(config.num("night-time", 18000));
    }

    static NecroFight of(BossInstance instance) {
        NecroFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        NecroFight fight = new NecroFight(instance);
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

    NecroConfig config() {
        return config;
    }

    /**
     * The context every block write in this fight is routed through, so all of it lands in the arena
     * ledger and comes back out again when the fight ends.
     * <p>
     * Every one of this boss's writes uses {@link dev.rbm72.weaponsplugin.boss.grief.Grief#setMechanicBlock}
     * rather than {@code setBlock}, which is the explicit form of an exemption this boss depends on:
     * {@code setBlock} honours the server's grief switch, {@code setMechanicBlock} does not. The rest of
     * the roster's block work is destruction, which a server may reasonably want to turn off; the canopy,
     * the graves and the corpse floor are not destruction, they are the mechanics themselves. Gating them
     * would leave three of his four phases running with nothing for players to interact with.
     */
    AttackContext griefContext() {
        return griefContext;
    }

    UndeadHorde horde() {
        return horde;
    }

    CorpseField corpses() {
        return corpses;
    }

    GraveMarkers graves() {
        return graves;
    }

    /** Combatants only — spectators and creative players never inflate a count that drives mechanic scaling. */
    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    World world() {
        return instance.arena().world();
    }

    /** Y of the block a player stands on at the arena's fixed centre — the reference height for canopy and pile work. */
    int floorY() {
        return instance.arena().center().getBlockY();
    }

    // -------------------------------------------------------------------- sky

    /**
     * Tears his hold off the sky. Vanilla does the rest: undead with a clear view of a daytime sky
     * catch fire on their own, and the roster rule is that we lean on that rather than reimplement it.
     * The Overlord himself is a wither skeleton, which vanilla exempts — so the sun burns his army and
     * pointedly not him, which is exactly the fantasy.
     */
    void letTheSunIn() {
        if (daylight) {
            return;
        }
        daylight = true;
        forceTime(config.num("daylight-time", 6000));
    }

    /** He re-knits the shroud: the sun goes back out and his army stops burning. */
    void pullTheNightBack() {
        if (!daylight) {
            return;
        }
        daylight = false;
        forceTime(config.num("night-time", 18000));
    }

    boolean isDaylight() {
        return daylight;
    }

    /** True when vanilla sunlight is doing the burning by itself, which is the intended path. */
    boolean sunBurnsUndead() {
        return daylight && skyClock && openSky;
    }

    /**
     * True when the daylight swing has to be faked because the arena cannot receive it — a nether/end
     * realm has no day clock, and a roofed or underground arena has no sky for the sun to come through.
     * {@link UndeadHorde#scorch} then sets the horde alight directly, so the phase-defining beat still
     * happens with real fire on real mobs instead of quietly doing nothing.
     */
    boolean needsArtificialSun() {
        return daylight && !(skyClock && openSky);
    }

    private void forceTime(int time) {
        World world = world();
        if (!skyClock || world == null) {
            return;
        }
        world.setTime(time);
    }

    /**
     * Samples skylight a couple of blocks above the arena floor. Skylight is time-independent — a block
     * with a clear column to the sky reads {@link #FULL_SKYLIGHT} at midnight too — so this answers
     * "can sunlight ever reach this floor" without caring what time it currently is. Run once at fight
     * start, before any canopy exists to skew it.
     */
    private boolean detectOpenSky() {
        World world = instance.arena().world();
        if (world == null) {
            return false;
        }
        Location centre = instance.arena().center();
        double radius = instance.arena().radius();
        for (double[] sample : SKY_SAMPLES) {
            Block block = world.getBlockAt(
                    centre.getBlockX() + (int) Math.round(sample[0] * radius),
                    centre.getBlockY() + 2,
                    centre.getBlockZ() + (int) Math.round(sample[1] * radius));
            if (block.getLightFromSky() >= FULL_SKYLIGHT) {
                return true;
            }
        }
        plugin.getLogger().warning(() -> "Necro Overlord's arena has no view of the sky (roofed or underground) — "
                + "the shroud break will ignite his horde directly instead of letting real daylight do it.");
        return false;
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("necro_overlord")) {
                return;
            }
            release();
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    /**
     * Puts the sky back and drops every prop this fight still holds. Idempotent, and deliberately does
     * <em>not</em> undo block work: the canopy, the graves and the corpse floor are all in the arena
     * ledger, which restores them wholesale a moment after this runs.
     */
    private void release() {
        ACTIVE.remove(instance.entity().getUniqueId());
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        try {
            World world = world();
            if (skyClock && world != null) {
                world.setFullTime(originalFullTime);
            }
            graves.discardAll();
        } catch (Exception e) {
            // A failed teardown must never propagate into the scheduler and kill the timer that is
            // still trying to finish the rest of it.
            plugin.getLogger().log(Level.SEVERE,
                    "Necro Overlord fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }

    // ------------------------------------------------------------------ shared cues

    /** The soul-fire flourish every one of his physical props announces itself with. */
    static void necroticFlourish(Location loc, Sound sound, float pitch) {
        Fx.burst(loc.clone().add(0, 0.8, 0), Particle.SCULK_SOUL, 24, 0.5);
        Fx.burst(loc.clone().add(0, 0.8, 0), Particle.SOUL_FIRE_FLAME, 12, 0.4);
        Fx.sound(loc, sound, 1.0f, pitch);
    }
}
