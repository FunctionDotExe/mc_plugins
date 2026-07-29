package dev.rbm72.weaponsplugin.boss.progress;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The per-player boss kill ledger — the one piece of boss state that deliberately outlives the fight,
 * the session, and the server process.
 * <p>
 * Everything downstream of "what has this player actually beaten" hangs off this: the Worldender's
 * clear gate ({@link dev.rbm72.weaponsplugin.boss.Boss#requiredClears()}), first-clear-only loot,
 * what {@code /bossinfo} shows you about your own history, and {@code /bossleaderboard}. It was
 * deferred three times because each of those reads as a separate feature; they are all the same
 * table.
 * <p>
 * One YAML file per player under {@code plugins/WeaponsPlugin/progress/}, matching
 * {@code AccessoryManager}'s layout. Every file is read once at startup rather than lazily on first
 * access, because the leaderboard needs every player's row at the same time — including players who
 * are offline, which is most of them — and a lazy cache would have to walk the folder anyway. Writes
 * are write-through on each credited kill: a kill is exactly the moment a crash must not lose.
 */
public final class BossProgressStore {

    private final WeaponsPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerProgress> byUuid = new HashMap<>();

    public BossProgressStore(WeaponsPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "progress");
        loadAll();
    }

    /** Never null: a player with no history yet gets an empty row created on demand. */
    public PlayerProgress progress(UUID uuid) {
        return byUuid.computeIfAbsent(uuid, id -> new PlayerProgress(id, null));
    }

    public PlayerProgress progress(Player player) {
        PlayerProgress progress = progress(player.getUniqueId());
        // Names change; the file is the only place we can keep up with that, and the leaderboard is
        // unreadable without it. Refreshed on every lookup rather than only at login, so a rename shows
        // up on this player's next kill without needing its own event hook.
        if (!player.getName().equals(progress.name())) {
            progress.setName(player.getName());
        }
        return progress;
    }

    public BossClearRecord record(UUID uuid, String bossId) {
        return progress(uuid).record(bossId.toLowerCase(Locale.ROOT));
    }

    /**
     * Credits {@code player} with a kill of {@code bossId} and persists it immediately.
     *
     * @param durationMs how long the fight ran, for the fastest-clear column; 0 if unknown
     * @return true if this was that player's <em>first</em> clear of this boss — the caller's cue to
     *         hand out first-clear loot. Never true twice for the same player/boss pair, which is the
     *         whole reason first-clear rewards need this table to exist at all.
     */
    public boolean recordKill(Player player, String bossId, long durationMs) {
        String key = bossId.toLowerCase(Locale.ROOT);
        PlayerProgress progress = progress(player);
        progress.setName(player.getName());
        BossClearRecord before = progress.record(key);
        progress.put(key, before.withClear(System.currentTimeMillis(), durationMs));
        save(progress);
        return !before.cleared();
    }

    /**
     * Rows for every player who has ever killed anything, ranked by total kills (ties broken by
     * distinct bosses beaten, then name, so the order is stable between calls).
     */
    public List<PlayerProgress> leaderboard() {
        List<PlayerProgress> rows = new ArrayList<>(byUuid.values());
        rows.removeIf(row -> row.totalKills() == 0);
        rows.sort(Comparator.comparingInt(PlayerProgress::totalKills).reversed()
                .thenComparing(Comparator.comparingInt(PlayerProgress::distinctClears).reversed())
                .thenComparing(PlayerProgress::name));
        return rows;
    }

    /**
     * Rows for one boss, ranked by fastest clear (fastest first). Players who have killed it but whose
     * fights were never timed are excluded rather than sorted to the bottom — a missing time is not a
     * slow time, and showing it as one would be a lie about somebody's run.
     */
    public List<PlayerProgress> leaderboard(String bossId) {
        String key = bossId.toLowerCase(Locale.ROOT);
        List<PlayerProgress> rows = new ArrayList<>(byUuid.values());
        rows.removeIf(row -> row.record(key).fastestMs() <= 0L);
        rows.sort(Comparator.comparingLong((PlayerProgress row) -> row.record(key).fastestMs())
                .thenComparing(PlayerProgress::name));
        return rows;
    }

    // ------------------------------------------------------------------ disk

    private void loadAll() {
        if (!folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            UUID uuid;
            try {
                uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
            } catch (IllegalArgumentException e) {
                // Not one of ours (a stray file, a hand-edited name). Skipping beats failing startup.
                plugin.getLogger().warning("Ignoring non-UUID file in progress folder: " + file.getName());
                continue;
            }
            byUuid.put(uuid, read(uuid, file));
        }
        plugin.getLogger().info(() -> "Loaded boss progress for " + byUuid.size() + " player(s)");
    }

    private PlayerProgress read(UUID uuid, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        PlayerProgress progress = new PlayerProgress(uuid, config.getString("name"));
        ConfigurationSection bosses = config.getConfigurationSection("bosses");
        if (bosses == null) {
            return progress;
        }
        for (String bossId : bosses.getKeys(false)) {
            ConfigurationSection row = bosses.getConfigurationSection(bossId);
            if (row == null) {
                continue;
            }
            progress.put(bossId.toLowerCase(Locale.ROOT), new BossClearRecord(
                    row.getInt("kills"),
                    row.getLong("first-clear-ms"),
                    row.getLong("last-clear-ms"),
                    row.getLong("fastest-ms")));
        }
        return progress;
    }

    private void save(PlayerProgress progress) {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the boss progress folder — kills will not persist.");
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", progress.name());
        for (Map.Entry<String, BossClearRecord> entry : progress.records().entrySet()) {
            BossClearRecord record = entry.getValue();
            String path = "bosses." + entry.getKey() + ".";
            config.set(path + "kills", record.kills());
            config.set(path + "first-clear-ms", record.firstClearMs());
            config.set(path + "last-clear-ms", record.lastClearMs());
            config.set(path + "fastest-ms", record.fastestMs());
        }
        try {
            config.save(new File(folder, progress.uuid() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save boss progress for " + progress.uuid() + " — that kill may not survive a restart.", e);
        }
    }
}
