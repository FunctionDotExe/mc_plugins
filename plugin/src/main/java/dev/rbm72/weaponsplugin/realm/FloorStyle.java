package dev.rbm72.weaponsplugin.realm;

/** How the open fight floor between the dais and the wall is shaped — the wall/tower/pillar footing always stays flat regardless. */
public enum FloorStyle {
    /** Flat, banded rings — the original coliseum floor. */
    FLAT,
    /** Smooth organic hummocks (a trig-driven height wave, no two columns identical). */
    UNDULATE,
    /** Rises in flat stepped terraces toward the dais — a ziggurat. */
    TERRACES_UP,
    /** Sinks in flat stepped terraces toward the dais — a sunken pit/bowl around a raised throne island. */
    TERRACES_DOWN
}
