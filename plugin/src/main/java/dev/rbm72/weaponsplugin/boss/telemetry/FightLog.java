package dev.rbm72.weaponsplugin.boss.telemetry;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Opens a {@link FightRecord} per fight, keeps the recent ones in memory for {@code /bossreport}, and
 * writes each finished one to {@code plugins/WeaponsPlugin/telemetry/} as JSON.
 * <p>
 * Files rather than only memory, because the reports worth reading are the ones nobody was watching:
 * a phase whose mechanic times out on every run shows up as a pattern across twenty fights, not in the
 * one you happened to type a command after. Old files are pruned by count so the folder cannot grow
 * without bound on a busy server.
 */
public final class FightLog {

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final WeaponsPlugin plugin;
    private final File folder;
    private final Deque<FightRecord> recent = new ArrayDeque<>();

    public FightLog(WeaponsPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "telemetry");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("boss-telemetry.enabled", true);
    }

    private int memoryLimit() {
        return Math.max(1, plugin.getConfig().getInt("boss-telemetry.keep-in-memory", 25));
    }

    private int fileLimit() {
        return Math.max(0, plugin.getConfig().getInt("boss-telemetry.keep-files", 400));
    }

    /**
     * Starts recording a fight. Returns a live record even when telemetry is switched off — a disabled
     * recorder still answers every {@code record.x()} call, it just never reaches disk. That keeps the
     * fight code free of {@code if (telemetry != null)} at a dozen call sites, which is exactly where a
     * null check eventually gets forgotten and takes a boss down with it.
     */
    public FightRecord begin(String bossId, String bossName, String worldName, int players, List<String> modifiers) {
        FightRecord record = new FightRecord(bossId, bossName, worldName, players, modifiers);
        recent.addLast(record);
        while (recent.size() > memoryLimit()) {
            recent.removeFirst();
        }
        return record;
    }

    /** Closes a record out and writes it. Never throws — a fight must end cleanly whatever the disk does. */
    public void finish(FightRecord record, String reason, double healthFraction, int survivors) {
        safely(() -> record.finish(reason, healthFraction, survivors));
        if (!enabled()) {
            return;
        }
        safely(() -> write(record));
    }

    /** The most recent fight, finished or still running, or null if none this session. */
    public FightRecord last() {
        return recent.peekLast();
    }

    /** The most recent fight of one boss, or null. */
    public FightRecord last(String bossId) {
        String key = bossId.toLowerCase(Locale.ROOT);
        FightRecord found = null;
        for (FightRecord record : recent) {
            if (record.bossId().equals(key)) {
                found = record;
            }
        }
        return found;
    }

    /** Newest-first, for a {@code /bossreport list}. */
    public List<FightRecord> recent() {
        List<FightRecord> list = new ArrayList<>(recent);
        list.sort(Comparator.comparingLong(FightRecord::startedAtMs).reversed());
        return list;
    }

    /**
     * Runs a telemetry step with its exception swallowed and logged. Every recording call site in the
     * fight goes through here: instrumentation that can abort a phase transition or cancel a hit is a
     * strictly worse trade than instrumentation that occasionally misses a row.
     */
    public void safely(Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Boss telemetry threw — the fight is unaffected.", e);
        }
    }

    private void write(FightRecord record) {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the boss telemetry folder — this fight will not be logged.");
            return;
        }
        String name = FILE_STAMP.format(Instant.ofEpochMilli(record.startedAtMs())) + "-" + record.bossId()
                + (record.wipe() ? "-wipe" : "") + ".json";
        try {
            Files.writeString(new File(folder, name).toPath(), record.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write boss telemetry file " + name, e);
            return;
        }
        prune();
    }

    /** Keeps the newest {@link #fileLimit()} files. 0 disables pruning entirely. */
    private void prune() {
        int limit = fileLimit();
        if (limit <= 0) {
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length <= limit) {
            return;
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < sorted.size() - limit; i++) {
            File stale = sorted.get(i);
            if (!stale.delete()) {
                plugin.getLogger().warning("Could not prune old telemetry file " + stale.getName());
            }
        }
    }
}
