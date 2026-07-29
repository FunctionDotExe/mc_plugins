package dev.rbm72.weaponsplugin.structuregen;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.realm.Realm;
import dev.rbm72.weaponsplugin.realm.RealmCrystalItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Builds one procedural dungeon: a {@link DungeonLayoutGenerator} room graph painted with the
 * target boss's own {@link dev.rbm72.weaponsplugin.realm.ArenaTheme}, a themed grunt
 * ({@link DungeonMobProfile}) garrisoning every room but the treasure room, a side loot chest in
 * some of those rooms, and that realm's crystal (see {@link RealmCrystalItem}) sealed behind a wall
 * that only opens once every room's grunts are dead (tracked by {@link DungeonInstance}).
 * <p>
 * This writes blocks directly (no {@code Grief}/ledger involvement): unlike boss-fight arenas, a
 * dungeon is meant to persist, not restore after a fight ends, so it sits deliberately outside the
 * boss-engine grief contract.
 */
public final class DungeonBuilder {

    private static final int ROOM_MIN_SIZE = 7;
    private static final int ROOM_MAX_SIZE = 11;
    private static final int ROOM_MIN_HEIGHT = 5;
    private static final int ROOM_MAX_HEIGHT = 8;
    private static final int CORRIDOR_WIDTH = 3;
    private static final int CORRIDOR_HEIGHT = 5;

    private DungeonBuilder() {
    }

    /** Where a dungeon's surface dig-site opens up, and where its crystal ended up. */
    public record DungeonResult(Location entrance, Location treasure) {
    }

    /**
     * Picks a random underground point near {@code near}: a random bearing/distance within
     * {@code radius}, with depth measured down from that column's actual surface height rather than
     * a fixed Y, so it still lands underground on a mountain and doesn't bury itself in bedrock in a
     * low valley.
     */
    public static Location randomUndergroundAnchor(World world, Location near, double radius, int minDepth, int maxDepth) {
        Random rng = new Random();
        double angle = rng.nextDouble(0, Math.PI * 2);
        double distance = rng.nextDouble(radius * 0.35, radius);
        int x = near.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = near.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        int surfaceY = world.getHighestBlockYAt(x, z);
        int depth = minDepth + rng.nextInt(Math.max(1, maxDepth - minDepth + 1));
        int y = Math.max(world.getMinHeight() + 8, surfaceY - depth);
        return new Location(world, x, y, z);
    }

    /** @return the dungeon's entrance/treasure locations, or empty if this boss has no realm. */
    public static Optional<DungeonResult> build(WeaponsPlugin plugin, String bossId, Location anchor) {
        Optional<Realm> realmOpt = plugin.realmRegistry().byBossId(bossId);
        if (realmOpt.isEmpty()) {
            return Optional.empty();
        }
        Realm realm = realmOpt.get();
        DungeonPalette palette = DungeonPalette.fromTheme(realm.theme());
        DungeonMobProfile mobProfile = DungeonMobProfiles.get(bossId);
        World world = anchor.getWorld();
        Random rng = new Random();

        int roomCount = plugin.getConfig().getInt("structures.room-count", 8);
        int cellSpacing = plugin.getConfig().getInt("structures.cell-spacing", 14);
        int mobsPerRoom = plugin.getConfig().getInt("structures.mobs-per-room", 3);
        double lootChestChance = plugin.getConfig().getDouble("structures.loot-chest-chance", 0.4);
        DungeonLayoutGenerator.Layout layout = DungeonLayoutGenerator.generate(Math.max(2, roomCount), rng);

        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();

        DungeonInstance instance = DungeonInstance.start(plugin, world);
        Map<DungeonLayoutGenerator.Cell, int[]> footprints = new HashMap<>();
        Map<DungeonLayoutGenerator.Cell, Integer> roomIndexByCell = new HashMap<>();
        Location treasureLocation = null;
        Location entranceLocation = null;

        List<DungeonLayoutGenerator.Cell> cells = layout.cells();
        for (int roomIndex = 0; roomIndex < cells.size(); roomIndex++) {
            DungeonLayoutGenerator.Cell cell = cells.get(roomIndex);
            roomIndexByCell.put(cell, roomIndex);

            int w = ROOM_MIN_SIZE + rng.nextInt(ROOM_MAX_SIZE - ROOM_MIN_SIZE + 1);
            int d = ROOM_MIN_SIZE + rng.nextInt(ROOM_MAX_SIZE - ROOM_MIN_SIZE + 1);
            int h = ROOM_MIN_HEIGHT + rng.nextInt(ROOM_MAX_HEIGHT - ROOM_MIN_HEIGHT + 1);
            int x0 = baseX + cell.gx() * cellSpacing - w / 2;
            int z0 = baseZ + cell.gz() * cellSpacing - d / 2;
            int y0 = baseY;

            boolean isTreasure = cell.equals(layout.treasure());
            RoomArchetype archetype = isTreasure ? RoomArchetype.PILLAR_HALL : RoomArchetype.random(rng);
            archetype.build(world, x0, y0, z0, w, h, d, palette, rng);
            int[] footprint = {x0, y0, z0, w, h, d};
            footprints.put(cell, footprint);

            if (isTreasure) {
                treasureLocation = placeCrystal(plugin, world, footprint, palette, realm);
                instance.registerRoom(roomIndex, 0);
            } else {
                int spawned = spawnGarrison(world, footprint, mobProfile, mobsPerRoom, rng, instance, roomIndex);
                instance.registerRoom(roomIndex, spawned);
                if (rng.nextDouble() < lootChestChance) {
                    placeLootChest(plugin, world, footprint, rng);
                }
            }
            if (cell.equals(layout.start())) {
                entranceLocation = carveEntranceShaft(world, footprint, palette);
            }
        }

        for (DungeonLayoutGenerator.Cell[] edge : layout.edges()) {
            int[] roomA = footprints.get(edge[0]);
            int[] roomB = footprints.get(edge[1]);
            carveCorridor(world, roomA, roomB, palette);
            if (edge[0].equals(layout.treasure())) {
                sealDoorway(world, roomA, roomB, palette, instance);
            } else if (edge[1].equals(layout.treasure())) {
                sealDoorway(world, roomB, roomA, palette, instance);
            }
        }

        if (entranceLocation == null || treasureLocation == null) {
            return Optional.empty();
        }
        return Optional.of(new DungeonResult(entranceLocation, treasureLocation));
    }

    /** Cancels every dungeon's watchdog task — called from {@code WeaponsPlugin#onDisable()}. */
    public static void shutdownAll() {
        DungeonInstance.shutdownAll();
    }

    private static int spawnGarrison(World world, int[] room, DungeonMobProfile profile, int count, Random rng,
                                      DungeonInstance instance, int roomIndex) {
        int x0 = room[0];
        int y0 = room[1];
        int z0 = room[2];
        int w = room[3];
        int d = room[5];
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int x = x0 + 2 + rng.nextInt(Math.max(1, w - 4));
            int z = z0 + 2 + rng.nextInt(Math.max(1, d - 4));
            Location spot = new Location(world, x + 0.5, y0 + 1, z + 0.5);
            if (!(world.spawnEntity(spot, profile.base()) instanceof LivingEntity mob)) {
                continue;
            }
            mob.customName(profile.label());
            mob.setCustomNameVisible(false);
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(true);
            var healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(profile.health());
                mob.setHealth(profile.health());
            }
            if (profile.handItem() != null && mob.getEquipment() != null) {
                mob.getEquipment().setItemInMainHand(new ItemStack(profile.handItem()));
                mob.getEquipment().setItemInMainHandDropChance(0f);
            }
            instance.trackMob(mob, roomIndex, profile.ability());
            spawned++;
        }
        return spawned;
    }

    private static void placeLootChest(WeaponsPlugin plugin, World world, int[] room, Random rng) {
        int x0 = room[0];
        int y0 = room[1];
        int z0 = room[2];
        List<Weapon> pool = plugin.weaponRegistry().all().stream()
                .filter(weapon -> weapon.rarity() == Rarity.COMMON || weapon.rarity() == Rarity.RARE)
                .toList();
        if (pool.isEmpty()) {
            return;
        }
        int cx = x0 + 2;
        int cz = z0 + 2;
        Block chestBlock = world.getBlockAt(cx, y0 + 1, cz);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            Weapon weapon = pool.get(rng.nextInt(pool.size()));
            chest.getBlockInventory().setItem(0, weapon.createItem());
            chest.update();
        }
    }

    private static Location placeCrystal(WeaponsPlugin plugin, World world, int[] room,
                                          DungeonPalette palette, Realm realm) {
        int x0 = room[0];
        int y0 = room[1];
        int z0 = room[2];
        int w = room[3];
        int d = room[5];
        int cx = x0 + w / 2;
        int cz = z0 + d / 2;
        set(world, cx, y0 + 1, cz, palette.pillar());
        Block chestBlock = world.getBlockAt(cx, y0 + 2, cz);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            ItemStack crystal = RealmCrystalItem.create(plugin, realm);
            chest.getBlockInventory().setItem(0, crystal);
            chest.update();
        }
        return new Location(world, cx + 0.5, y0 + 3, cz + 0.5);
    }

    /**
     * Carves only the gap between the two rooms' wall footprints (plus the one boundary wall column
     * on each end, so the wall itself becomes the doorway) — never the center-to-center span, which
     * would drive a corridor wall straight through both rooms' interiors.
     */
    private static void carveCorridor(World world, int[] fromRoom, int[] toRoom, DungeonPalette palette) {
        if (fromRoom == null || toRoom == null) {
            return;
        }
        int fromCx = fromRoom[0] + fromRoom[3] / 2;
        int fromCz = fromRoom[2] + fromRoom[5] / 2;
        int toCx = toRoom[0] + toRoom[3] / 2;
        int toCz = toRoom[2] + toRoom[5] / 2;
        int y0 = fromRoom[1];
        int half = CORRIDOR_WIDTH / 2;

        boolean alongX = fromCz == toCz;
        if (alongX) {
            int[] left = fromCx <= toCx ? fromRoom : toRoom;
            int[] right = fromCx <= toCx ? toRoom : fromRoom;
            int xStart = left[0] + left[3] - 1;
            int xEnd = right[0];
            if (xEnd <= xStart) {
                return;
            }
            for (int x = xStart; x <= xEnd; x++) {
                carveCorridorSlice(world, x, fromCz, y0, half, palette, true);
            }
        } else {
            int[] near = fromCz <= toCz ? fromRoom : toRoom;
            int[] far = fromCz <= toCz ? toRoom : fromRoom;
            int zStart = near[2] + near[5] - 1;
            int zEnd = far[2];
            if (zEnd <= zStart) {
                return;
            }
            for (int z = zStart; z <= zEnd; z++) {
                carveCorridorSlice(world, fromCx, z, y0, half, palette, false);
            }
        }
    }

    private static void carveCorridorSlice(World world, int cx, int cz, int y0, int half,
                                            DungeonPalette palette, boolean alongX) {
        for (int off = -half; off <= half; off++) {
            int x = alongX ? cx : cx + off;
            int z = alongX ? cz + off : cz;
            set(world, x, y0, z, palette.floorAt(x, z));
            set(world, x, y0 + CORRIDOR_HEIGHT - 1, z, palette.ceiling());
            for (int y = y0 + 1; y < y0 + CORRIDOR_HEIGHT - 1; y++) {
                set(world, x, y, z, Material.AIR);
            }
            if (off == -half || off == half) {
                for (int y = y0 + 1; y < y0 + CORRIDOR_HEIGHT - 1; y++) {
                    set(world, x, y, z, palette.wall());
                }
            }
        }
    }

    /**
     * Plugs the treasure room's one doorway with its own wall material right after
     * {@link #carveCorridor} opens it, and hands the plugged block coordinates to {@code instance} so
     * it can knock them back out once every other room is cleared.
     */
    private static void sealDoorway(World world, int[] treasureRoom, int[] otherRoom,
                                     DungeonPalette palette, DungeonInstance instance) {
        int treasureCx = treasureRoom[0] + treasureRoom[3] / 2;
        int treasureCz = treasureRoom[2] + treasureRoom[5] / 2;
        int otherCx = otherRoom[0] + otherRoom[3] / 2;
        int otherCz = otherRoom[2] + otherRoom[5] / 2;
        int y0 = treasureRoom[1];
        int half = CORRIDOR_WIDTH / 2;
        boolean alongX = treasureCz == otherCz;

        if (alongX) {
            int doorX = treasureCx <= otherCx ? treasureRoom[0] + treasureRoom[3] - 1 : treasureRoom[0];
            for (int off = -half; off <= half; off++) {
                int z = treasureCz + off;
                for (int y = y0 + 1; y < y0 + CORRIDOR_HEIGHT - 1; y++) {
                    set(world, doorX, y, z, palette.wall());
                    instance.addGateBlock(doorX, y, z);
                }
            }
        } else {
            int doorZ = treasureCz <= otherCz ? treasureRoom[2] + treasureRoom[5] - 1 : treasureRoom[2];
            for (int off = -half; off <= half; off++) {
                int x = treasureCx + off;
                for (int y = y0 + 1; y < y0 + CORRIDOR_HEIGHT - 1; y++) {
                    set(world, x, y, doorZ, palette.wall());
                    instance.addGateBlock(x, y, doorZ);
                }
            }
        }
    }

    /**
     * Bores a real ladder-shaft from the start room's ceiling up to the surface at that column, so an
     * underground dungeon has a findable dig site rather than a sealed box nobody can reach without
     * noclip. Punches straight up through whatever terrain is in the way; the top block becomes an
     * open mouth ringed with the palette's accent stone so it reads as a landmark, not a random hole.
     */
    private static Location carveEntranceShaft(World world, int[] startRoom, DungeonPalette palette) {
        int cx = startRoom[0] + startRoom[3] / 2 + 2;
        int cz = startRoom[2] + startRoom[5] / 2;
        int ceilingY = startRoom[1] + startRoom[4] - 1;
        int surfaceY = world.getHighestBlockYAt(cx, cz);

        BlockData ladder = Material.LADDER.createBlockData();
        if (ladder instanceof Directional directional) {
            directional.setFacing(BlockFace.NORTH);
        }
        for (int y = ceilingY; y < surfaceY; y++) {
            world.getBlockAt(cx, y, cz).setBlockData(ladder, false);
        }
        set(world, cx, surfaceY, cz, Material.AIR);

        int[][] rim = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : rim) {
            set(world, cx + offset[0], surfaceY, cz + offset[1], palette.wallAccent());
        }
        return new Location(world, cx + 0.5, surfaceY + 1, cz + 0.5);
    }

    /** Full box shell: floor, walls, ceiling, hollowed interior. Shared by every {@link RoomArchetype}. */
    static void shell(World world, int x0, int y0, int z0, int w, int h, int d, DungeonPalette palette) {
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                boolean edgeX = x == x0 || x == x0 + w - 1;
                boolean edgeZ = z == z0 || z == z0 + d - 1;
                set(world, x, y0, z, palette.floorAt(x, z));
                set(world, x, y0 + h - 1, z, palette.ceiling());
                for (int y = y0 + 1; y < y0 + h - 1; y++) {
                    if (edgeX || edgeZ) {
                        boolean corner = edgeX && edgeZ;
                        set(world, x, y, z, corner ? palette.wallAccent() : palette.wall());
                    } else {
                        set(world, x, y, z, Material.AIR);
                    }
                }
            }
        }
    }

    static void set(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
