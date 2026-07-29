package dev.rbm72.weaponsplugin.structuregen;

import org.bukkit.World;

import java.util.Random;

/**
 * Interior styles a generated dungeon room can take. Each carves a rectangular footprint
 * {@code [x0, x0+w) x [y0, y0+h) x [z0, z0+d)} — floor at {@code y0}, ceiling at {@code y0+h-1} —
 * the same shell every archetype shares, varied only in what fills the volume between them. That
 * shared shell is what {@link DungeonBuilder} lines corridors up against.
 */
public enum RoomArchetype {

    /** Plain box: floor, four walls, flat ceiling, one floor-standing light at center. */
    BOX {
        @Override
        void build(World world, int x0, int y0, int z0, int w, int h, int d, DungeonPalette palette, Random rng) {
            DungeonBuilder.shell(world, x0, y0, z0, w, h, d, palette);
            DungeonBuilder.set(world, x0 + w / 2, y0 + 1, z0 + d / 2, palette.light());
        }
    },

    /** Box shell plus a grid of full-height pillars every 4 blocks — needs a taller room to read well. */
    PILLAR_HALL {
        @Override
        void build(World world, int x0, int y0, int z0, int w, int h, int d, DungeonPalette palette, Random rng) {
            DungeonBuilder.shell(world, x0, y0, z0, w, h, d, palette);
            for (int px = 2; px < w - 1; px += 4) {
                for (int pz = 2; pz < d - 1; pz += 4) {
                    for (int py = 1; py < h - 1; py++) {
                        DungeonBuilder.set(world, x0 + px, y0 + py, z0 + pz, palette.pillar());
                    }
                }
            }
            DungeonBuilder.set(world, x0 + w / 2, y0 + 1, z0 + d / 2, palette.light());
        }
    },

    /** Box shell with a stepped, corbelled ceiling — each top course insets one block, accent-trimmed. */
    VAULTED {
        @Override
        void build(World world, int x0, int y0, int z0, int w, int h, int d, DungeonPalette palette, Random rng) {
            DungeonBuilder.shell(world, x0, y0, z0, w, h, d, palette);
            int steps = Math.min(2, Math.max(0, (h - 3) / 2));
            for (int s = 1; s <= steps; s++) {
                int y = y0 + h - 1 - s;
                for (int x = x0 + s; x < x0 + w - s; x++) {
                    DungeonBuilder.set(world, x, y, z0 + s, palette.wallAccent());
                    DungeonBuilder.set(world, x, y, z0 + d - 1 - s, palette.wallAccent());
                }
                for (int z = z0 + s; z < z0 + d - s; z++) {
                    DungeonBuilder.set(world, x0 + s, y, z, palette.wallAccent());
                    DungeonBuilder.set(world, x0 + w - 1 - s, y, z, palette.wallAccent());
                }
            }
            DungeonBuilder.set(world, x0 + w / 2, y0 + 1, z0 + d / 2, palette.light());
        }
    };

    abstract void build(World world, int x0, int y0, int z0, int w, int h, int d, DungeonPalette palette, Random rng);

    public static RoomArchetype random(Random rng) {
        RoomArchetype[] values = values();
        return values[rng.nextInt(values.length)];
    }
}
