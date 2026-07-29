package dev.rbm72.weaponsplugin.realm;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * A void world holding one walled arena centered on the origin, sized to exactly match the boss's
 * {@code arena-radius}. Driven entirely by an {@link ArenaTheme}: {@link FloorStyle} changes how the
 * open fight floor is actually shaped (flat rings, organic hummocks, or terraced up/down around the
 * dais) and {@link WallStyle} changes how the perimeter is actually built (uniform battlements,
 * jagged spikes, broken arches, sparse obelisks, or rolling mounds) — so two bosses sharing a
 * material palette still don't share a silhouette or a construction method. A short solid
 * containment ring is always guaranteed at the wall band regardless of style, so no arena can ever
 * let a player walk or fall out of it. Everything beyond the wall ring is left empty — the realm
 * exists only to host that one fight.
 */
public final class RealmChunkGenerator extends ChunkGenerator {

    static final int PLATFORM_Y = 64;
    static final int DAIS_RADIUS = 6;
    static final int DAIS_HEIGHT = 2;

    private static final int WALL_THICKNESS = 3;
    /** Every wall style guarantees at least this much solid height — the non-negotiable containment floor. */
    private static final int BASE_WALL_HEIGHT = 3;
    private static final int FULL_WALL_HEIGHT = 9;
    private static final int MERLON_BUCKET_DEGREES = 10;
    private static final int SPIKE_BUCKET_DEGREES = 15;
    private static final int ARCH_BUCKET_DEGREES = 20;
    private static final double OBELISK_SPACING_DEGREES = 45.0;
    private static final double OBELISK_WIDTH_DEGREES = 6.0;
    private static final int OBELISK_HEIGHT = 20;
    private static final double MOUND_FREQUENCY = 4.0;

    private static final double[] TOWER_ANGLES_DEG = {45, 135, 225, 315};
    private static final int TOWER_RADIUS = 5;
    private static final int TOWER_HEIGHT = 24;
    private static final int PILLAR_COUNT = 8;
    private static final double PILLAR_RING_FRACTION = 0.55;
    /** Moat band: a ring this many blocks wide, hugging the inside of the wall. */
    private static final int MOAT_WIDTH = 3;

    private static final int TERRACE_WIDTH = 5;
    private static final int TERRACE_STEP = 2;
    private static final int MAX_PIT_DEPTH = 8;

    /**
     * Beyond the wall/tower footprint there's nothing but void by design — but knockback, a dash
     * attack, or a flier going up and over the wall could otherwise send the boss (or a player)
     * sailing out into it forever. An invisible barrier floor and ceiling, extended well past the
     * visible arena, catches anything that gets knocked out before it can fall/fly away for good.
     */
    private static final int SAFETY_MARGIN = 40;
    private static final int SAFETY_FLOOR_OFFSET = -12;
    private static final int SAFETY_CEILING_OFFSET = 70;

    private final ArenaTheme theme;
    private final int radius;
    private final double[] towerX = new double[TOWER_ANGLES_DEG.length];
    private final double[] towerZ = new double[TOWER_ANGLES_DEG.length];
    private final int[] pillarX = new int[PILLAR_COUNT];
    private final int[] pillarZ = new int[PILLAR_COUNT];

    public RealmChunkGenerator(ArenaTheme theme, int radius) {
        this.theme = theme;
        this.radius = radius;
        for (int i = 0; i < TOWER_ANGLES_DEG.length; i++) {
            double angle = Math.toRadians(TOWER_ANGLES_DEG[i]);
            towerX[i] = radius * Math.cos(angle);
            towerZ[i] = radius * Math.sin(angle);
        }
        double pillarRing = radius * PILLAR_RING_FRACTION;
        for (int i = 0; i < PILLAR_COUNT; i++) {
            double angle = Math.toRadians(360.0 / PILLAR_COUNT * i);
            pillarX[i] = (int) Math.round(pillarRing * Math.cos(angle));
            pillarZ[i] = (int) Math.round(pillarRing * Math.sin(angle));
        }
    }

    /** The boss/player spawn point sits one block above the raised center dais, not the outer floor. */
    static int spawnY() {
        return PLATFORM_Y + DAIS_HEIGHT + 1;
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        double wallInnerBound = radius - (WALL_THICKNESS - 1);
        double moatInnerBound = wallInnerBound - MOAT_WIDTH;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = baseX + x;
                int worldZ = baseZ + z;
                double dist = Math.hypot(worldX, worldZ);
                double towerDist = theme.towers() ? nearestTowerDist(worldX, worldZ) : Double.MAX_VALUE;
                boolean isTower = towerDist <= TOWER_RADIUS;
                boolean isDais = !isTower && dist <= DAIS_RADIUS;
                boolean isFloorArea = isTower || isDais || dist <= radius + 1;
                if (!isFloorArea) {
                    if (dist <= radius + 1 + SAFETY_MARGIN) {
                        chunkData.setBlock(x, PLATFORM_Y + SAFETY_FLOOR_OFFSET, z, Material.BARRIER);
                        chunkData.setBlock(x, PLATFORM_Y + SAFETY_CEILING_OFFSET, z, Material.BARRIER);
                    }
                    continue;
                }

                if (isDais) {
                    buildDaisColumn(chunkData, x, z);
                    continue;
                }

                boolean isWallBand = !isTower && dist > wallInnerBound && dist <= radius + 1;
                boolean isMoatBand = !isTower && !isWallBand && theme.moat() != null && !theme.floodInterior()
                        && dist > moatInnerBound && dist <= wallInnerBound;

                double angleDeg = (Math.toDegrees(Math.atan2(worldZ, worldX)) + 360) % 360;
                int surfaceY;
                if (isTower || isWallBand || isMoatBand) {
                    surfaceY = PLATFORM_Y;
                    fillFoundation(chunkData, x, z, surfaceY);
                    chunkData.setBlock(x, surfaceY, z, isMoatBand ? theme.moat() : floorMaterial(dist));
                } else {
                    int offset = theme.floodInterior() ? 0 : floorOffset(dist, angleDeg);
                    surfaceY = PLATFORM_Y + offset;
                    fillFoundation(chunkData, x, z, surfaceY);
                    chunkData.setBlock(x, surfaceY, z, theme.floodInterior() ? theme.moat() : floorMaterial(dist));
                }

                if (isTower) {
                    buildTower(chunkData, x, z, towerDist, surfaceY);
                } else if (isWallBand) {
                    buildWall(chunkData, x, z, dist, angleDeg, surfaceY);
                } else if (theme.pillar() != null && isPillar(worldX, worldZ)) {
                    buildPillar(chunkData, x, z, surfaceY);
                }
            }
        }
    }

    private void fillFoundation(ChunkData chunkData, int x, int z, int surfaceY) {
        chunkData.setBlock(x, surfaceY - 1, z, Material.DEEPSLATE);
        chunkData.setBlock(x, surfaceY - 2, z, Material.DEEPSLATE);
        chunkData.setBlock(x, surfaceY - 3, z, Material.BEDROCK);
    }

    /**
     * The dais is always a full solid pillar down to bedrock, never just a shallow foundation — a
     * {@code TERRACES_DOWN} floor sinks the surrounding ring up to {@link #MAX_PIT_DEPTH} blocks
     * below {@link #PLATFORM_Y}, and a shallow dais foundation would leave a hollow, walk-off-the-edge-
     * and-fall-through-open-air pocket directly under/around the throne island instead of a real cliff.
     */
    private void buildDaisColumn(ChunkData chunkData, int x, int z) {
        for (int y = PLATFORM_Y; y <= PLATFORM_Y + DAIS_HEIGHT; y++) {
            chunkData.setBlock(x, y, z, theme.dais());
        }
        int bottom = PLATFORM_Y - MAX_PIT_DEPTH - 4;
        for (int y = PLATFORM_Y - 1; y > bottom; y--) {
            chunkData.setBlock(x, y, z, Material.DEEPSLATE);
        }
        chunkData.setBlock(x, bottom, z, Material.BEDROCK);
    }

    /** Height offset (from {@link #PLATFORM_Y}) for the open fight floor only — wall/tower/moat footing never varies. */
    private int floorOffset(double dist, double angleDeg) {
        return switch (theme.floorStyle()) {
            case FLAT -> 0;
            case UNDULATE -> {
                double wave = Math.sin(Math.toRadians(angleDeg * 3)) + Math.cos(dist * 0.35);
                yield (int) Math.round(wave);
            }
            case TERRACES_UP -> {
                int steps = (int) ((radius - dist) / TERRACE_WIDTH);
                yield Math.min(DAIS_HEIGHT, steps * TERRACE_STEP);
            }
            case TERRACES_DOWN -> {
                int steps = (int) ((radius - dist) / TERRACE_WIDTH);
                yield -Math.min(MAX_PIT_DEPTH, steps * TERRACE_STEP);
            }
        };
    }

    /** Concentric banding — a coliseum floor, not a flat slab. */
    private Material floorMaterial(double dist) {
        int band = (int) ((dist - DAIS_RADIUS) / 4);
        return band % 2 == 0 ? theme.floorA() : theme.floorB();
    }

    private void buildWall(ChunkData chunkData, int x, int z, double dist, double angleDeg, int surfaceY) {
        int height = wallHeightFor(angleDeg);
        for (int y = surfaceY + 1; y <= surfaceY + height; y++) {
            Material mat = (y == surfaceY + Math.max(1, height / 2)) ? theme.wallAccent() : theme.wall();
            chunkData.setBlock(x, y, z, mat);
        }
        if (theme.wallStyle() == WallStyle.BATTLEMENT && dist > radius - 1) {
            int bucket = (int) (angleDeg / MERLON_BUCKET_DEGREES);
            if (bucket % 2 == 0) {
                chunkData.setBlock(x, surfaceY + height + 1, z, theme.wallAccent());
            }
        }
        // Inner face gets a lantern line at head height regardless of wall style, so the wall always
        // throws some light into the fight.
        if (dist < radius - (WALL_THICKNESS - 2)) {
            int bucket = (int) (angleDeg / (MERLON_BUCKET_DEGREES * 3));
            if (bucket % 2 == 0) {
                chunkData.setBlock(x, surfaceY + 3, z, theme.lantern());
            }
        }
    }

    private int wallHeightFor(double angleDeg) {
        return switch (theme.wallStyle()) {
            case BATTLEMENT -> FULL_WALL_HEIGHT;
            case SPIKES -> {
                int bucket = (int) (angleDeg / SPIKE_BUCKET_DEGREES);
                int spread = FULL_WALL_HEIGHT - BASE_WALL_HEIGHT;
                yield BASE_WALL_HEIGHT + new Random(bucket).nextInt(spread + 1);
            }
            case ARCHES -> {
                int bucket = (int) (angleDeg / ARCH_BUCKET_DEGREES);
                yield (bucket % 2 == 0) ? FULL_WALL_HEIGHT : BASE_WALL_HEIGHT;
            }
            case OBELISKS -> {
                double mod = angleDeg % OBELISK_SPACING_DEGREES;
                yield (mod < OBELISK_WIDTH_DEGREES) ? OBELISK_HEIGHT : BASE_WALL_HEIGHT;
            }
            case MOUNDS -> {
                double wave = (Math.sin(Math.toRadians(angleDeg * MOUND_FREQUENCY)) + 1) / 2.0;
                yield BASE_WALL_HEIGHT + (int) Math.round(wave * (FULL_WALL_HEIGHT - BASE_WALL_HEIGHT));
            }
        };
    }

    private void buildTower(ChunkData chunkData, int x, int z, double towerDist, int surfaceY) {
        for (int y = surfaceY + 1; y <= surfaceY + TOWER_HEIGHT; y++) {
            Material mat = (y == surfaceY + TOWER_HEIGHT / 3 || y == surfaceY + 2 * TOWER_HEIGHT / 3)
                    ? theme.wallAccent()
                    : theme.tower();
            chunkData.setBlock(x, y, z, mat);
        }
        // Tapering spire roof: each step up, only the columns still within the shrinking radius fill in.
        int step;
        for (step = 1; step <= TOWER_RADIUS && towerDist <= TOWER_RADIUS - step; step++) {
            chunkData.setBlock(x, surfaceY + TOWER_HEIGHT + step, z, theme.roof());
        }
        if (towerDist < 1.0) {
            chunkData.setBlock(x, surfaceY + TOWER_HEIGHT + step, z, theme.lantern());
        }
    }

    private boolean isPillar(int worldX, int worldZ) {
        for (int i = 0; i < pillarX.length; i++) {
            if (pillarX[i] == worldX && pillarZ[i] == worldZ) {
                return true;
            }
        }
        return false;
    }

    private void buildPillar(ChunkData chunkData, int x, int z, int surfaceY) {
        for (int y = surfaceY + 1; y <= surfaceY + FULL_WALL_HEIGHT; y++) {
            chunkData.setBlock(x, y, z, theme.pillar());
        }
        chunkData.setBlock(x, surfaceY + FULL_WALL_HEIGHT + 1, z, theme.lantern());
    }

    private double nearestTowerDist(int worldX, int worldZ) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < towerX.length; i++) {
            double dx = worldX - towerX[i];
            double dz = worldZ - towerZ[i];
            double d = Math.hypot(dx, dz);
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return theme.biome();
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(theme.biome());
            }
        };
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    // No shouldGenerateBedrock() override: the base method is deprecated and already defaults to false,
    // which is exactly what a flat walled realm wants — the floor is written by generateSurface.

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
