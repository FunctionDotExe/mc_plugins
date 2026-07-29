package dev.rbm72.weaponsplugin.boss.modifier;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Which affixes are armed for each boss id, plus the tuning numbers each affix runs at.
 * <p>
 * Armed per boss id and applied at spawn, exactly like the hard-mode boolean it replaces: a live fight
 * never changes difficulty under the group fighting it. Persisted to {@code modifiers.yml} so a restart
 * doesn't silently disarm an event night's setup — the old boolean lived only in memory, which meant any
 * restart quietly reverted every boss to normal with nothing to say so.
 */
public final class BossModifiers {

    private final WeaponsPlugin plugin;
    private final File file;
    private final Map<String, EnumSet<BossModifier>> armed = new HashMap<>();

    public BossModifiers(WeaponsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "modifiers.yml");
        load();
    }

    /** Affixes armed for this boss id. Never null, never modifiable by the caller. */
    public Set<BossModifier> of(String bossId) {
        EnumSet<BossModifier> set = armed.get(key(bossId));
        return set == null ? EnumSet.noneOf(BossModifier.class) : EnumSet.copyOf(set);
    }

    public boolean has(String bossId, BossModifier modifier) {
        EnumSet<BossModifier> set = armed.get(key(bossId));
        return set != null && set.contains(modifier);
    }

    /** @return the new state — true if it is now armed. */
    public boolean toggle(String bossId, BossModifier modifier) {
        EnumSet<BossModifier> set = armed.computeIfAbsent(key(bossId), id -> EnumSet.noneOf(BossModifier.class));
        boolean nowArmed;
        if (set.contains(modifier)) {
            set.remove(modifier);
            nowArmed = false;
        } else {
            set.add(modifier);
            nowArmed = true;
        }
        save();
        return nowArmed;
    }

    public void set(String bossId, BossModifier modifier, boolean armedNow) {
        EnumSet<BossModifier> set = armed.computeIfAbsent(key(bossId), id -> EnumSet.noneOf(BossModifier.class));
        if (armedNow) {
            set.add(modifier);
        } else {
            set.remove(modifier);
        }
        save();
    }

    public void clear(String bossId) {
        armed.remove(key(bossId));
        save();
    }

    /** Every boss id with at least one affix armed — for a {@code /bossaffix list} with no argument. */
    public List<String> armedBosses() {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, EnumSet<BossModifier>> entry : armed.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                ids.add(entry.getKey());
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /** Affix ids as they should appear in a telemetry file — stable, sorted, no display formatting. */
    public List<String> names(String bossId) {
        List<String> names = new ArrayList<>();
        for (BossModifier modifier : of(bossId)) {
            names.add(modifier.id());
        }
        names.sort(String::compareTo);
        return names;
    }

    // ------------------------------------------------------------------ tuning

    /**
     * A magnitude for one affix, read live from {@code boss-modifiers.<affix>.<key>} so an event's
     * difficulty can be dialled between pulls without a recompile.
     */
    public double tuning(BossModifier modifier, String key, double def) {
        return plugin.getConfig().getDouble("boss-modifiers." + modifier.id() + "." + key, def);
    }

    public double healthMultiplier(String bossId) {
        return has(bossId, BossModifier.HARD) ? tuning(BossModifier.HARD, "health-multiplier", 1.5) : 1.0;
    }

    public double scaleMultiplier(String bossId) {
        return has(bossId, BossModifier.HARD) ? tuning(BossModifier.HARD, "scale-multiplier", 1.15) : 1.0;
    }

    public double speedMultiplier(String bossId) {
        return has(bossId, BossModifier.HARD) ? tuning(BossModifier.HARD, "speed-multiplier", 1.2) : 1.0;
    }

    /** Extra scalar on every attack cooldown — below 1.0 means faster. */
    public double cooldownMultiplier(String bossId) {
        return has(bossId, BossModifier.FRENZY) ? tuning(BossModifier.FRENZY, "cooldown-multiplier", 0.7) : 1.0;
    }

    /** How many of each add to spawn: 1 normally, 2 under {@link BossModifier#DOUBLE_ADDS}. */
    public int addCopies(String bossId) {
        return has(bossId, BossModifier.DOUBLE_ADDS)
                ? Math.max(1, (int) Math.round(tuning(BossModifier.DOUBLE_ADDS, "copies", 2))) : 1;
    }

    /** Fraction of a player's damage reflected back at them, 0 when the affix is off. */
    public double reflectFraction(String bossId) {
        return has(bossId, BossModifier.REFLECT) ? tuning(BossModifier.REFLECT, "fraction", 0.25) : 0.0;
    }

    public long timerMs(String bossId) {
        return has(bossId, BossModifier.TIMER)
                ? Math.round(tuning(BossModifier.TIMER, "minutes", 10.0) * 60_000) : 0L;
    }

    // -------------------------------------------------------------------- disk

    private static String key(String bossId) {
        return bossId.toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String bossId : config.getKeys(false)) {
            EnumSet<BossModifier> set = EnumSet.noneOf(BossModifier.class);
            for (String affixId : config.getStringList(bossId)) {
                BossModifier.byId(affixId).ifPresentOrElse(set::add, () -> plugin.getLogger()
                        .warning("Unknown boss affix '" + affixId + "' in modifiers.yml — ignoring it."));
            }
            if (!set.isEmpty()) {
                armed.put(key(bossId), set);
            }
        }
        if (!armed.isEmpty()) {
            plugin.getLogger().info(() -> "Boss affixes armed for " + armed.size() + " boss(es): " + armedBosses());
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, EnumSet<BossModifier>> entry : armed.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            config.set(entry.getKey(), names(entry.getKey()));
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create the plugin data folder — boss affixes will not persist.");
            return;
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save modifiers.yml — affixes will reset on restart.", e);
        }
    }
}
