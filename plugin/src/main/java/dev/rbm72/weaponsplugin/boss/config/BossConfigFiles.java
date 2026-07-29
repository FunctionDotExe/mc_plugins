package dev.rbm72.weaponsplugin.boss.config;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Splits boss tuning out of the single 2300-line {@code config.yml} into one file per boss under
 * {@code plugins/WeaponsPlugin/bosses/}, and overlays those files back onto the live config at startup.
 * <p>
 * The overlay is the whole trick. Every tunable in the roster is already read as
 * {@code bosses.<id>.<key>} off {@code plugin.getConfig()} (see {@code Boss#configDouble} and
 * {@code BossAttack#configDouble}), so writing each per-boss file's values into that path at load time
 * means several hundred existing call sites keep working untouched — no accessor changes, no per-boss
 * plumbing, and nothing to get half-migrated.
 * <p>
 * <b>Precedence:</b> {@code config.yml} is the base, a per-boss file overrides it. After the one-time
 * migration below the two hold identical values, so nothing changes on the first run; from then on the
 * per-boss file is the one to edit. {@code config.yml}'s {@code bosses:} section is deliberately left in
 * place rather than rewritten — {@link org.bukkit.configuration.file.FileConfiguration#save} discards
 * every comment in the file, and those comments are the only documentation several of these numbers have.
 * <p>
 * Done now because it is cheap now: a split is a file move today and a data migration once servers have
 * hand-edited values in the old location.
 */
public final class BossConfigFiles {

    private static final String FOLDER = "bosses";

    private BossConfigFiles() {
    }

    /**
     * Creates the folder and migrates on first run, then overlays every per-boss file onto the live
     * config. Safe to call repeatedly — {@code /bossreload} does exactly that, and must, because
     * {@link WeaponsPlugin#reloadConfig()} re-reads {@code config.yml} from disk and so discards the
     * previous overlay.
     */
    public static void apply(WeaponsPlugin plugin) {
        File folder = new File(plugin.getDataFolder(), FOLDER);
        boolean fresh = !folder.isDirectory();
        if (fresh && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the bosses/ config folder — boss tuning stays in config.yml.");
            return;
        }
        if (fresh) {
            int written = migrate(plugin, folder);
            plugin.getLogger().info(() -> "Split boss tuning into " + written + " file(s) under bosses/."
                    + " config.yml's bosses: section is now the fallback and can be trimmed by hand.");
        }
        int overlaid = overlay(plugin, folder);
        if (overlaid > 0) {
            plugin.getLogger().info(() -> "Applied per-boss tuning from " + overlaid + " file(s) in bosses/.");
        }
    }

    /** One file per boss id found in {@code config.yml}'s {@code bosses:} section. */
    private static int migrate(WeaponsPlugin plugin, File folder) {
        ConfigurationSection bosses = plugin.getConfig().getConfigurationSection("bosses");
        if (bosses == null) {
            return 0;
        }
        int written = 0;
        for (String bossId : bosses.getKeys(false)) {
            ConfigurationSection section = bosses.getConfigurationSection(bossId);
            if (section == null) {
                continue;
            }
            YamlConfiguration out = new YamlConfiguration();
            // Deep copy: getValues(true) flattens nested sections into dotted paths, which is exactly the
            // shape set() wants, and keeps meter.<id>.<key> style subtrees intact.
            section.getValues(true).forEach((key, value) -> {
                if (!(value instanceof ConfigurationSection)) {
                    out.set(key, value);
                }
            });
            out.options().setHeader(List.of(
                    "Tuning for boss '" + bossId + "'.",
                    "",
                    "Keys here are relative to the boss: 'max-health' means bosses." + bossId + ".max-health.",
                    "Values in this file override config.yml's bosses." + bossId + " section.",
                    "Reload with /bossreload — no restart needed."));
            try {
                out.save(new File(folder, bossId + ".yml"));
                written++;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write bosses/" + bossId + ".yml", e);
            }
        }
        return written;
    }

    /** Writes every per-boss file's leaves into {@code bosses.<id>.<key>} on the live config. */
    private static int overlay(WeaponsPlugin plugin, File folder) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return 0;
        }
        List<String> loaded = new ArrayList<>();
        for (File file : files) {
            String bossId = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            YamlConfiguration in = YamlConfiguration.loadConfiguration(file);
            for (String key : in.getKeys(true)) {
                if (in.isConfigurationSection(key)) {
                    continue;
                }
                plugin.getConfig().set("bosses." + bossId + "." + key, in.get(key));
            }
            loaded.add(bossId);
        }
        return loaded.size();
    }
}
