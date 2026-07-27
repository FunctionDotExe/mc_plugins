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

    /** Master grief switch for this boss. Default on — bosses are destructive unless disabled. */
    public boolean griefEnabled() {
        return configBoolean("grief", true);
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
}
