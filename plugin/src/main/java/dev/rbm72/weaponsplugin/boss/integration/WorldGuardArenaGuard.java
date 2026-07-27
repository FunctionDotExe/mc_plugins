package dev.rbm72.weaponsplugin.boss.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Claims a temporary, build-locked WorldGuard region over a boss's arena for the duration of the
 * fight — stops players quarrying an escape route or griefing the arena mid-fight — and releases
 * it the instant the fight ends. Entirely optional: every method no-ops if WorldGuard isn't
 * installed, so the boss framework works identically without it.
 */
public final class WorldGuardArenaGuard {

    private WorldGuardArenaGuard() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    /** Adds a full-height, build-denied cuboid spanning the arena's radius. No-op if WorldGuard isn't installed. */
    public static void start(String bossId, Location center, double radius) {
        if (!available()) {
            return;
        }
        Impl.start(bossId, center, radius);
    }

    /** Removes the region added by {@link #start}. Safe to call even if it was never added, or WorldGuard isn't installed. */
    public static void stop(String bossId, World world) {
        if (!available() || world == null) {
            return;
        }
        Impl.stop(bossId, world);
    }

    /**
     * Every direct WorldGuard type reference lives in this nested class, which the JVM links only the
     * first time one of its methods actually runs — that is, only after {@link #available()} has
     * confirmed WorldGuard is present. If these references sat in the outer methods instead, merely
     * *calling* {@link #start} on a server without WorldGuard would fail to link the method
     * ({@link NoClassDefFoundError}) before the guard's early-return could ever execute — which
     * previously crashed boss spawning outright on any server that didn't have WorldGuard.
     */
    private static final class Impl {

        private static String regionId(String bossId) {
            return "bossfight-" + bossId;
        }

        static void start(String bossId, Location center, double radius) {
            World world = center.getWorld();
            if (world == null) {
                return;
            }
            RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return;
            }
            int r = (int) Math.ceil(radius);
            BlockVector3 min = BlockVector3.at(center.getBlockX() - r, world.getMinHeight(), center.getBlockZ() - r);
            BlockVector3 max = BlockVector3.at(center.getBlockX() + r, world.getMaxHeight(), center.getBlockZ() + r);
            ProtectedRegion region = new ProtectedCuboidRegion(regionId(bossId), min, max);
            region.setFlag(Flags.BUILD, StateFlag.State.DENY);
            // Above the server's default global region — an arena must lock even where the whole world normally allows building.
            region.setPriority(10);
            manager.addRegion(region);
        }

        static void stop(String bossId, World world) {
            RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return;
            }
            manager.removeRegion(regionId(bossId));
        }
    }
}
