package dev.rbm72.weaponsplugin.realm;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Biome;

/**
 * The look of one realm's arena: its floor shape, its wall style, what it's built from, whether it
 * floods or moats, what environment/biome it sits in, and what drifts through its sky.
 * {@link RealmChunkGenerator} is one generic builder driven entirely by these choices — but
 * {@link FloorStyle} and {@link WallStyle} change the actual construction (banded vs. terraced vs.
 * undulating floor; battlement vs. spikes vs. broken arches vs. obelisks vs. mounds), not just the
 * materials, so bosses sharing a palette family still don't share a silhouette.
 *
 * @param towers             whether the four corner towers get built at all — skipped for
 *                           fliers/area-denial bosses that want the silhouette open, not boxed in.
 * @param pillar             nullable — {@code null} skips the inner pillar ring entirely.
 * @param moat               nullable — a fluid-filled ring at the foot of the wall, or (if
 *                           {@code floodInterior}) the fluid that floods the entire arena floor instead.
 * @param floodInterior      true submerges the whole floor (except the dais) shin-deep in {@code moat} —
 *                           an underwater-colosseum look. Requires {@code moat} to be a fluid.
 * @param emberParticle      what drifts high over the arena; {@code DUST} uses {@code emberColor},
 *                           anything else ignores it. {@code null} disables the ambient loop entirely.
 * @param forceRain          keeps real weather on and permanently storming over this one realm instead
 *                           of the usual clear/frozen sky.
 * @param periodicLightning  strikes a harmless visual-only lightning bolt near the boss every so often.
 */
public record ArenaTheme(
        FloorStyle floorStyle,
        WallStyle wallStyle,
        Material floorA,
        Material floorB,
        Material dais,
        Material wall,
        Material wallAccent,
        boolean towers,
        Material tower,
        Material roof,
        Material lantern,
        Material pillar,
        Material moat,
        boolean floodInterior,
        World.Environment environment,
        Biome biome,
        Particle emberParticle,
        Color emberColor,
        boolean forceRain,
        boolean periodicLightning
) {
}
