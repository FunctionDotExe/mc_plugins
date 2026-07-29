package dev.rbm72.weaponsplugin.boss.telemetry;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One fight, written down as it happens.
 * <p>
 * Seventeen bosses running twelve to eighteen mechanics each is past the point where tuning by
 * recollection works: "that phase feels bad" and "nobody ever does that mechanic" are the same report
 * from the outside, and they need opposite fixes. This is the instrumentation that tells them apart —
 * per phase, how long it took, which attacks actually fired, whether its objective was completed or
 * quietly bypassed on the floor-lock timeout, and what killed whom.
 * <p>
 * The single most valuable field here is {@link PhaseRecord#mechanicBypassed} — a phase whose objective
 * times out every run is a phase whose mechanic is either unreadable or impossible, and that is
 * invisible from inside the fight.
 * <p>
 * Every mutator is called from the main thread (fight tick, damage event, death event) and none of them
 * may throw: telemetry that can break a boss fight is worse than no telemetry. Callers go through
 * {@link FightLog#safely} rather than guarding each call site.
 */
public final class FightRecord {

    /** One phase's slice of the fight. */
    public static final class PhaseRecord {

        private final String name;
        private final int index;
        private final double entryHealthFraction;
        private final long enteredAtMs;
        private final Map<String, Integer> attacks = new LinkedHashMap<>();
        private long endedAtMs;
        private int exposures;
        private int deaths;
        private boolean mechanicBypassed;
        private boolean hadMechanic;

        PhaseRecord(String name, int index, double entryHealthFraction, long enteredAtMs, boolean hadMechanic) {
            this.name = name;
            this.index = index;
            this.entryHealthFraction = entryHealthFraction;
            this.enteredAtMs = enteredAtMs;
            this.hadMechanic = hadMechanic;
        }

        public String name() {
            return name;
        }

        public int index() {
            return index;
        }

        public long durationMs(long fallbackEndMs) {
            return Math.max(0L, (endedAtMs == 0L ? fallbackEndMs : endedAtMs) - enteredAtMs);
        }

        public int exposures() {
            return exposures;
        }

        public int deaths() {
            return deaths;
        }

        public boolean hadMechanic() {
            return hadMechanic;
        }

        /**
         * True when this phase handed over its health seam on the floor-lock timeout instead of because
         * its objective was completed. Over a handful of runs this is the whole tuning signal: a mechanic
         * bypassed every time is one players never even attempt.
         */
        public boolean mechanicBypassed() {
            return mechanicBypassed;
        }

        public Map<String, Integer> attacks() {
            return attacks;
        }
    }

    /** One player death, with the boss's attack that was last credited with hitting them. */
    public record DeathRecord(String player, String phase, String killingAttack, long msIntoFight) {
    }

    private final String bossId;
    private final String bossName;
    private final String worldName;
    private final long startedAtMs;
    private final int playersAtStart;
    private final List<String> modifiers;

    private final List<PhaseRecord> phases = new ArrayList<>();
    private final List<DeathRecord> deaths = new ArrayList<>();
    private final Map<String, Integer> attackTotals = new LinkedHashMap<>();
    private final Map<String, Double> damageByPlayer = new LinkedHashMap<>();
    private final Set<String> participants = new LinkedHashSet<>();
    private final List<String> events = new ArrayList<>();
    /**
     * Who last hit each player, and with what. The only reliable route to "what killed them" — a death
     * event can name the damage type but never the mechanic, and "killed by MAGIC" is exactly the
     * uninformative answer that made wipes untunable.
     */
    private final Map<String, String> lastHitBy = new LinkedHashMap<>();

    private long endedAtMs;
    private String endReason = "UNFINISHED";
    private double endHealthFraction;
    private int survivorsAtEnd;

    FightRecord(String bossId, String bossName, String worldName, int playersAtStart, List<String> modifiers) {
        this.bossId = bossId;
        this.bossName = bossName;
        this.worldName = worldName;
        this.startedAtMs = System.currentTimeMillis();
        this.playersAtStart = playersAtStart;
        this.modifiers = List.copyOf(modifiers);
    }

    // ---------------------------------------------------------------- recording

    public void enterPhase(String name, int index, double healthFraction, boolean hasMechanic) {
        long now = System.currentTimeMillis();
        PhaseRecord previous = currentPhase();
        if (previous != null) {
            previous.endedAtMs = now;
        }
        phases.add(new PhaseRecord(name, index, healthFraction, now, hasMechanic));
    }

    public void attackUsed(String attackName) {
        attackTotals.merge(attackName, 1, Integer::sum);
        PhaseRecord phase = currentPhase();
        if (phase != null) {
            phase.attacks.merge(attackName, 1, Integer::sum);
        }
    }

    /** The phase's objective was actually completed (or partially, for a multi-step one). */
    public void exposure() {
        PhaseRecord phase = currentPhase();
        if (phase != null) {
            phase.exposures++;
        }
    }

    /** The floor-lock valve fired: this phase was handed over with its objective untouched. */
    public void mechanicBypassed() {
        PhaseRecord phase = currentPhase();
        if (phase != null) {
            phase.mechanicBypassed = true;
        }
    }

    public void playerDamagedBoss(Player player, double damage) {
        participants.add(player.getName());
        damageByPlayer.merge(player.getName(), Math.max(0.0, damage), Double::sum);
    }

    /** Records that {@code attackName} was the boss's most recent hit on this player. */
    public void bossHitPlayer(Player player, String attackName) {
        participants.add(player.getName());
        lastHitBy.put(player.getName(), attackName == null ? "unknown" : attackName);
    }

    public void playerDied(Player player) {
        PhaseRecord phase = currentPhase();
        if (phase != null) {
            phase.deaths++;
        }
        deaths.add(new DeathRecord(player.getName(),
                phase == null ? "-" : phase.name(),
                lastHitBy.getOrDefault(player.getName(), "unknown"),
                System.currentTimeMillis() - startedAtMs));
    }

    public void eventFired(String eventId, double healthFraction) {
        events.add(eventId + "@" + String.format(Locale.ROOT, "%.2f", healthFraction));
    }

    void finish(String reason, double healthFraction, int survivors) {
        this.endedAtMs = System.currentTimeMillis();
        this.endReason = reason;
        this.endHealthFraction = healthFraction;
        this.survivorsAtEnd = survivors;
        PhaseRecord phase = currentPhase();
        if (phase != null && phase.endedAtMs == 0L) {
            phase.endedAtMs = endedAtMs;
        }
    }

    // ---------------------------------------------------------------- reading

    public PhaseRecord currentPhase() {
        return phases.isEmpty() ? null : phases.get(phases.size() - 1);
    }

    public String bossId() {
        return bossId;
    }

    public String bossName() {
        return bossName;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public long durationMs() {
        return Math.max(0L, (endedAtMs == 0L ? System.currentTimeMillis() : endedAtMs) - startedAtMs);
    }

    public String endReason() {
        return endReason;
    }

    public double endHealthFraction() {
        return endHealthFraction;
    }

    public int playersAtStart() {
        return playersAtStart;
    }

    public int survivorsAtEnd() {
        return survivorsAtEnd;
    }

    public List<String> modifiers() {
        return modifiers;
    }

    public List<PhaseRecord> phases() {
        return List.copyOf(phases);
    }

    public List<DeathRecord> deaths() {
        return List.copyOf(deaths);
    }

    public Map<String, Integer> attackTotals() {
        return Map.copyOf(attackTotals);
    }

    public Map<String, Double> damageByPlayer() {
        return Map.copyOf(damageByPlayer);
    }

    public Set<String> participants() {
        return Set.copyOf(participants);
    }

    /** The deepest phase this fight ever reached, 1-based — the headline number of a wipe recap. */
    public int deepestPhase() {
        int deepest = 0;
        for (PhaseRecord phase : phases) {
            deepest = Math.max(deepest, phase.index() + 1);
        }
        return deepest;
    }

    /**
     * True for a run that ended with the boss alive and nobody left standing — the case worth a recap.
     * A despawn with survivors present is an admin cleaning up, not a wipe.
     */
    public boolean wipe() {
        return !"DEFEATED".equals(endReason) && !deaths.isEmpty() && survivorsAtEnd == 0;
    }

    /** Attacks that never fired once — a phase pool the boss never actually got to use. */
    public List<String> unusedAttacks(List<String> authoredAttackNames) {
        List<String> unused = new ArrayList<>();
        for (String name : authoredAttackNames) {
            if (!attackTotals.containsKey(name)) {
                unused.add(name);
            }
        }
        return unused;
    }

    String toJson() {
        Json json = new Json();
        long end = endedAtMs == 0L ? System.currentTimeMillis() : endedAtMs;
        json.object(() -> {
            json.field("boss", bossId);
            json.field("bossName", bossName);
            json.field("world", worldName);
            json.field("startedAtMs", startedAtMs);
            json.field("endedAtMs", end);
            json.field("durationMs", durationMs());
            json.field("endReason", endReason);
            json.field("endHealthFraction", endHealthFraction);
            json.field("playersAtStart", playersAtStart);
            json.field("survivorsAtEnd", survivorsAtEnd);
            json.field("wipe", wipe());
            json.field("deepestPhase", deepestPhase());
            json.strings("modifiers", modifiers);
            json.strings("participants", participants);
            json.strings("events", events);
            json.counts("attackTotals", attackTotals);
            json.array("phases", phases, phase -> json.object(() -> {
                json.field("index", phase.index());
                json.field("name", phase.name());
                json.field("entryHealthFraction", phase.entryHealthFraction);
                json.field("durationMs", phase.durationMs(end));
                json.field("hadMechanic", phase.hadMechanic());
                json.field("mechanicCompletions", phase.exposures());
                json.field("mechanicBypassed", phase.mechanicBypassed());
                json.field("deaths", phase.deaths());
                json.counts("attacks", phase.attacks());
            }));
            json.array("deaths", deaths, death -> json.object(() -> {
                json.field("player", death.player());
                json.field("phase", death.phase());
                json.field("killingAttack", death.killingAttack());
                json.field("msIntoFight", death.msIntoFight());
            }));
            json.array("damageByPlayer", damageByPlayer.entrySet(), entry -> json.object(() -> {
                json.field("player", entry.getKey());
                json.field("damage", entry.getValue());
            }));
        });
        return json.toString();
    }
}
