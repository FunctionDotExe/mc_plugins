package dev.rbm72.weaponsplugin.structuregen;

import dev.rbm72.weaponsplugin.realm.ArenaTheme;
import org.bukkit.Material;

/**
 * The block set one dungeon is painted with. Derived from a boss's existing {@link ArenaTheme}
 * rather than authored separately, so every dungeon automatically matches the look of that boss's
 * realm arena — same wall material, same accent, same light source, same pillar stone.
 */
public record DungeonPalette(
        Material wall,
        Material wallAccent,
        Material floorA,
        Material floorB,
        Material ceiling,
        Material light,
        Material pillar,
        Material trim
) {

    public static DungeonPalette fromTheme(ArenaTheme theme) {
        Material ceiling = theme.roof() != null ? theme.roof() : theme.wall();
        Material pillar = theme.pillar() != null ? theme.pillar() : theme.wallAccent();
        Material light = theme.lantern() != null ? theme.lantern() : Material.GLOWSTONE;
        return new DungeonPalette(theme.wall(), theme.wallAccent(), theme.floorA(), theme.floorB(),
                ceiling, light, pillar, theme.dais());
    }

    /** Alternates floorA/floorB in a loose checker so the floor doesn't read as one flat texture. */
    public Material floorAt(int x, int z) {
        return ((x + z) & 1) == 0 ? floorA : floorB;
    }
}
