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

    public double maxHealth() {
        return configDouble("max-health", 300.0);
    }

    public double arenaRadius() {
        return configDouble("arena-radius", 20.0);
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
}
