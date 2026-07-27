package dev.rbm72.weaponsplugin.realm;

/**
 * How the perimeter reads above the mandatory solid containment base (always built regardless, so no
 * style can ever let a player fall/walk through the ring).
 */
public enum WallStyle {
    /** Straight uniform wall, alternating merlons on top — the classic castle look. */
    BATTLEMENT,
    /** Jagged, uneven spike heights per angle bucket — a crystalline/bony crown. */
    SPIKES,
    /** Only the short solid base is guaranteed; the tall upper wall exists in broken alternating sections. */
    ARCHES,
    /** Mostly a low rampart, with sparse tall monolith accents standing apart from it. */
    OBELISKS,
    /** Smooth sine-wave undulation instead of sharp crenellations — rolling, organic. */
    MOUNDS
}
