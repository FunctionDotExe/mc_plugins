package dev.rbm72.weaponsplugin.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks consecutive weapon hits an attacker lands on the same victim within a rolling window —
 * the same last-action-timestamp pattern {@code AccessoryPersonalAbilityListener} uses for
 * double-tap sneak, applied to melee hits instead of sneak toggles. A hit past the window, or on
 * a different victim than the last one, resets the streak back to 1 rather than continuing it, so
 * kiting between targets can't farm a free finisher.
 */
public final class ComboTracker {

    private static final long COMBO_WINDOW_MS = 3000;

    private record State(UUID victim, long lastHitMs, int streak) {
    }

    private final Map<UUID, State> states = new HashMap<>();

    /** Registers a hit and returns the resulting streak length (1 = no combo yet). */
    public int registerHit(UUID attacker, UUID victim) {
        long now = System.currentTimeMillis();
        State previous = states.get(attacker);
        int streak = (previous != null && previous.victim().equals(victim) && now - previous.lastHitMs() <= COMBO_WINDOW_MS)
                ? previous.streak() + 1
                : 1;
        states.put(attacker, new State(victim, now, streak));
        return streak;
    }
}
