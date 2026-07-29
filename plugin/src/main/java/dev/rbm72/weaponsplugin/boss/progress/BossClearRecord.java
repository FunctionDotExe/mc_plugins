package dev.rbm72.weaponsplugin.boss.progress;

/**
 * One player's history with one boss. Immutable — {@link BossProgressStore} replaces the record
 * rather than mutating it, so a caller holding an old one can never see a half-updated state.
 *
 * @param kills         how many times this player has been credited with the kill
 * @param firstClearMs  epoch millis of their first-ever clear, or 0 if they have never cleared it
 * @param lastClearMs   epoch millis of their most recent clear, or 0
 * @param fastestMs     shortest fight (spawn to death) they were present for, or 0 if unknown
 */
public record BossClearRecord(int kills, long firstClearMs, long lastClearMs, long fastestMs) {

    public static final BossClearRecord NONE = new BossClearRecord(0, 0L, 0L, 0L);

    public boolean cleared() {
        return kills > 0;
    }

    /**
     * This record plus one more kill of {@code durationMs}. A duration of 0 (or negative — a clock
     * that went backwards across a restart) is treated as unknown and leaves {@link #fastestMs}
     * alone, so a bad reading can never plant an unbeatable "0ms" record on the leaderboard.
     */
    public BossClearRecord withClear(long nowMs, long durationMs) {
        long first = firstClearMs == 0L ? nowMs : firstClearMs;
        long fastest = durationMs <= 0L ? fastestMs
                : (fastestMs == 0L ? durationMs : Math.min(fastestMs, durationMs));
        return new BossClearRecord(kills + 1, first, nowMs, fastest);
    }
}
