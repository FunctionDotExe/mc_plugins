package dev.rbm72.weaponsplugin.boss.progress;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Everything one player has beaten. Mutated only through {@link BossProgressStore}. */
public final class PlayerProgress {

    private final UUID uuid;
    private String name;
    private final Map<String, BossClearRecord> records = new HashMap<>();

    PlayerProgress(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    /** Last name this player was seen under — the leaderboard's display name, since a UUID is useless to read. */
    public String name() {
        return name == null || name.isBlank() ? uuid.toString().substring(0, 8) : name;
    }

    void setName(String name) {
        this.name = name;
    }

    /** Never null — a boss this player has never fought reads as {@link BossClearRecord#NONE}. */
    public BossClearRecord record(String bossId) {
        return records.getOrDefault(bossId, BossClearRecord.NONE);
    }

    void put(String bossId, BossClearRecord record) {
        records.put(bossId, record);
    }

    public Map<String, BossClearRecord> records() {
        return Collections.unmodifiableMap(records);
    }

    /** Total kills across the whole roster — the leaderboard's default ranking. */
    public int totalKills() {
        int total = 0;
        for (BossClearRecord record : records.values()) {
            total += record.kills();
        }
        return total;
    }

    /**
     * How many <em>distinct</em> bosses this player has cleared at least once. This, not
     * {@link #totalKills()}, is what a progression gate counts: farming one boss sixteen times is not
     * the same accomplishment as beating sixteen different ones, and the gate exists to say the latter.
     */
    public int distinctClears() {
        int distinct = 0;
        for (BossClearRecord record : records.values()) {
            if (record.cleared()) {
                distinct++;
            }
        }
        return distinct;
    }

    /** {@link #distinctClears()} not counting {@code exceptId} — a boss never gates on itself. */
    public int distinctClearsExcluding(String exceptId) {
        int distinct = 0;
        for (Map.Entry<String, BossClearRecord> entry : records.entrySet()) {
            if (!entry.getKey().equals(exceptId) && entry.getValue().cleared()) {
                distinct++;
            }
        }
        return distinct;
    }
}
