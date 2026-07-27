package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Stateless definition of a boss encounter: what it is, how it scales, and
 * what it drops. Mirrors {@code items.Weapon} — one instance per boss type,
 * shared across every fight; per-fight state lives in {@link BossInstance}.
 */
public abstract class Boss {

    protected final WeaponsPlugin plugin;

    protected Boss(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Component displayName();

    public abstract EntityType baseEntityType();

    /** Ordered by descending {@link BossPhase#entryThresholdFraction()}; phase 1 first. */
    public abstract List<BossPhase> phases();

    public abstract LootTable lootTable();

    /**
     * Scripted interruptions that fire once at each of their health milestones, independent of which
     * phase is running — shield checks, beacon drops, and anything else that stops the fight, names
     * one task, and puts a timer on it. Empty by default; a boss opts in.
     */
    public List<BossEvent> events() {
        return List.of();
    }

    public double maxHealth() {
        return configDouble("max-health", 300.0);
    }

    public double arenaRadius() {
        return configDouble("arena-radius", 36.0);
    }

    /**
     * Base {@link org.bukkit.attribute.Attribute#SCALE} applied at spawn, before the per-fight
     * player-count health scaling and any boss-specific size logic in its first phase's onEnter
     * (which runs after this and overrides it — see {@code AmalgamatedBulk}/{@code WeepingColossus}/
     * {@code Voidwyrm} for bosses with their own deliberate size).
     */
    public double baseScale() {
        return configDouble("scale", 1.5);
    }

    /**
     * Scalar applied to every point of damage this boss deals to a player, on top of whatever its
     * individual attacks are tuned for. One knob to make a whole boss hit harder or softer without
     * editing the dozens of per-attack damage values in config — see {@link BossDamageListener},
     * which applies it centrally to any hit credited to the boss entity.
     */
    public double outgoingDamageMultiplier() {
        return configDouble("damage-multiplier", 1.0);
    }

    /** No themed ambiance unless overridden. */
    public BossAmbiance ambiance() {
        return BossAmbiance.none();
    }

    /** Big title shown to the arena the instant the boss spawns. Empty by default (no title). */
    public Component entranceTitle() {
        return Component.empty();
    }

    public Component entranceSubtitle() {
        return Component.empty();
    }

    /** Big title shown to the arena the instant the boss is defeated. Empty by default (no title). */
    public Component defeatTitle() {
        return Component.empty();
    }

    public Component defeatSubtitle() {
        return Component.empty();
    }

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("bosses." + id() + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("bosses." + id() + "." + key, def);
    }

    protected final boolean configBoolean(String key, boolean def) {
        return plugin.getConfig().getBoolean("bosses." + id() + "." + key, def);
    }

    protected final String configString(String key, String def) {
        return plugin.getConfig().getString("bosses." + id() + "." + key, def);
    }

    /** Master grief switch for this boss. Default on — bosses are destructive unless disabled. */
    public boolean griefEnabled() {
        return configBoolean("grief", true);
    }

    /**
     * Whether everything this fight does to the world is written down and rolled back when it ends.
     * Default on, and it is what allows bosses to be as destructive as they are: with restore in place
     * an attack can flood the floor with lava or delete it outright, because none of it outlives the
     * fight. Turning this off restores the old permanent-scar behaviour, in which case the roster's
     * heavier terrain attacks will grind an arena down over repeated clears.
     */
    public boolean arenaRestoreEnabled() {
        return configBoolean("arena-restore", true);
    }

    /**
     * Hard ceiling on distinct blocks one fight may damage. Doubles as the grief-scope cap: the ledger
     * refuses any change past this, so a fight can never wreck more of the world than it can put back.
     */
    public int maxLedgerBlocks() {
        return configInt("max-ledger-blocks", 120_000);
    }

    /** Blocks restored per tick when the fight ends — bounds the rollback's own lag cost. */
    public int restoreBlocksPerTick() {
        return configInt("restore-blocks-per-tick", 4000);
    }

    /**
     * How far below the arena floor a player counts as having fallen into a pit. Anything this deep is
     * treated as "the floor gave out under you" regardless of which mechanic removed it.
     */
    public double pitDepth() {
        return configDouble("pit-depth", 6.0);
    }

    /**
     * Fraction of a player's max health a pit fall costs. Tuned so a single fall at full health is
     * survivable and a second one shortly after is not — the punishment is meant to stack, so that
     * carelessness with the arena kills while one mistake never does.
     */
    public double pitDamageFraction() {
        return configDouble("pit-damage-fraction", 0.55);
    }

    // ------------------------------------------------------------ player meters

    /**
     * Master switch for this boss's armour-ignoring, non-healable player meters (Chill, Static Charge,
     * Infection, Void Echo — see {@code boss.meter}). Default on; turning it off makes every attached
     * meter inert rather than absent, so a boss built around one still runs, just without its clock.
     */
    public boolean metersEnabled() {
        return configBoolean("meters", true);
    }

    /**
     * Ticks between meter pulses. Every rate a meter is configured with is per <em>second</em>, so this
     * only changes the resolution of the clock, never its speed — five ticks keeps the readout smooth
     * and keeps the block scans behind the campfire/lightning-rod cures to four passes a second.
     */
    public int meterTickInterval() {
        return configInt("meter-tick-interval", 5);
    }

    /**
     * Which channel this boss's meter readout is drawn on — {@code bar} for a boss bar of its own,
     * {@code action-bar} for the merged action-bar line, or {@code auto}.
     * <p>
     * Default {@code auto}: a boss bar while the player's stack still has room for one, the action bar
     * once it does not. A meter is fight-long and must be readable at all times, and a fight already
     * owns the health bar and the mechanic bar — three tiled bars is where the names start colliding
     * with the bar above and all three stop being readable. See
     * {@link dev.rbm72.weaponsplugin.boss.meter.MeterRegistry}.
     */
    public String meterChannel() {
        return configString("meter-channel", "auto");
    }

    /**
     * How many of this fight's boss bars a player may already be seeing before {@code auto} moves the
     * meter readout off the stack. Two — health plus the mechanic bar — is the point past which a third
     * bar costs more legibility than it adds.
     */
    public int meterBarStackLimit() {
        return configInt("meter-bar-stack-limit", 2);
    }

    /**
     * Per-skin tunable, read as {@code bosses.<boss>.meter.<meter>.<key>}. Every rate, radius and
     * damage number a {@link dev.rbm72.weaponsplugin.boss.meter.MeterSpec} is built from should come
     * through here: a meter is the one system in a fight that is deliberately unanswerable by gear or
     * healing, so it is the one most likely to need retuning without a recompile.
     */
    public double meterDouble(String meterId, String key, double def) {
        return configDouble("meter." + meterId + "." + key, def);
    }

    public int meterInt(String meterId, String key, int def) {
        return configInt("meter." + meterId + "." + key, def);
    }

    public boolean meterBoolean(String meterId, String key, boolean def) {
        return configBoolean("meter." + meterId + "." + key, def);
    }

    /** Whether this boss gets a DecentHolograms name/health-bar hologram (if that plugin is installed). Default on. */
    public boolean hologramEnabled() {
        return configBoolean("hologram", true);
    }

    /** WorldGuard build-lock region over the arena while this boss is live (if WorldGuard is installed). Default on. */
    public boolean worldGuardProtectionEnabled() {
        return configBoolean("worldguard-protection", true);
    }

    /**
     * Temp per-player world-border wall around the arena while this boss is live, so players can't
     * wander (or get knocked) out of the fight. Vanilla client-side border — no blocks placed.
     * Default off: every boss now fights inside its own walled realm (see {@code realm} package),
     * whose physical walls already do this job — the border shimmer would otherwise render right on
     * top of that wall. Only worth turning on for a boss still fought in the open, unwalled world.
     */
    public boolean arenaBarrierEnabled() {
        return configBoolean("arena-barrier", false);
    }

    /** Hard ceiling on any explosion power this boss requests (server-stability cap, not a grief-scope cap). */
    public float maxExplosionPower() {
        return (float) configDouble("max-explosion-power", 4.0);
    }

    /** Hard ceiling on crater break radius per call (bounds block churn). */
    public double maxCraterRadius() {
        return configDouble("max-crater-radius", 6.0);
    }

    /** Hard ceiling on live thrown FallingBlocks per fight. */
    public int maxFallingBlocks() {
        return configInt("max-falling-blocks", 24);
    }

    /** Soft ceiling attacks should keep per-tick particle counts under. */
    public int maxParticlesPerTick() {
        return configInt("max-particles-per-tick", 400);
    }

    /**
     * How much of an attack's listed cooldown gets cut by the final phase (see
     * {@code BossInstance#phaseCooldownScale}) — 0.45 means the last phase's cooldowns run at 55% of
     * what's authored. Softer than the original hardcoded 0.65 so the ramp stays readable.
     */
    public double phaseCooldownProgressCut() {
        return configDouble("phase-cooldown-progress-cut", 0.45);
    }

    /** Extra multiplier applied only in the enrage phase, on top of {@link #phaseCooldownProgressCut()}. */
    public double phaseCooldownEnrageCut() {
        return configDouble("phase-cooldown-enrage-cut", 0.85);
    }

    /** Floor on {@code phaseCooldownScale} — cooldowns never shrink past this fraction of authored. */
    public double phaseCooldownFloor() {
        return configDouble("phase-cooldown-floor", 0.45);
    }

    /**
     * How long {@code BossInstance#clampToPhaseFloor} pins a phase's health seam while its objective
     * goes <em>completely</em> unengaged before giving up and letting the fight through.
     * <p>
     * A safety valve against an unreachable objective, never a route through one. The clock resets on
     * any progress at all ({@code BossInstance#recordProgress}), so this is the time a group can spend
     * making <b>zero</b> headway — not the time the objective is allowed to take. Override it upward for
     * a boss whose objectives are block work rather than a burst window: mining a corpse floor out or
     * breaking two grave markers under horde pressure legitimately opens with a long stretch of nothing
     * measurable happening, and a valve short enough to cover that hands over every phase for free.
     */
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 45_000);
    }
}
