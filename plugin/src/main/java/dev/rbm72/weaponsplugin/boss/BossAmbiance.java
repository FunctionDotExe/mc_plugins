package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Optional themed flavor for a fight: a looping ambient particle/sound and,
 * if enabled, a temporary biome swap over the arena footprint. The biome
 * swap only ever touches {@link Biome} — never blocks — and is snapshotted
 * on start and restored exactly on end, so a fight never leaves a permanent
 * mark on the world.
 */
public final class BossAmbiance {

    private static final int BIOME_SAMPLE_SPACING = 4;
    private static final long AMBIENT_INTERVAL_TICKS = 40L;

    private final Particle ambientParticle;
    private final String ambientSoundKey;
    private final Sound ambientSoundFallback;
    private final boolean biomeSwap;
    private final Biome themedBiome;

    private BossAmbiance(Particle ambientParticle, String ambientSoundKey, Sound ambientSoundFallback,
                          boolean biomeSwap, Biome themedBiome) {
        this.ambientParticle = ambientParticle;
        this.ambientSoundKey = ambientSoundKey;
        this.ambientSoundFallback = ambientSoundFallback;
        this.biomeSwap = biomeSwap;
        this.themedBiome = themedBiome;
    }

    public static BossAmbiance none() {
        return new BossAmbiance(null, null, null, false, null);
    }

    public static BossAmbiance of(Particle ambientParticle, String ambientSoundKey, Sound ambientSoundFallback,
                                   boolean biomeSwap, Biome themedBiome) {
        return new BossAmbiance(ambientParticle, ambientSoundKey, ambientSoundFallback, biomeSwap, themedBiome);
    }

    /** Starts the ambient loop (tracked on {@code instance} for cleanup) and snapshots biomes if enabled. */
    Handle start(BossInstance instance) {
        if (ambientParticle == null) {
            return null;
        }

        Arena arena = instance.arena();
        World world = arena.world();
        Map<Long, Biome> snapshot = new HashMap<>();
        int y = arena.center().getBlockY();

        if (biomeSwap && world != null) {
            int centerX = arena.center().getBlockX();
            int centerZ = arena.center().getBlockZ();
            int radius = (int) Math.round(arena.radius());
            for (int dx = -radius; dx <= radius; dx += BIOME_SAMPLE_SPACING) {
                for (int dz = -radius; dz <= radius; dz += BIOME_SAMPLE_SPACING) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    snapshot.put(packed(x, z), world.getBiome(x, y, z));
                    world.setBiome(x, y, z, themedBiome);
                }
            }
        }

        BukkitTask task = instance.plugin().getServer().getScheduler().runTaskTimer(instance.plugin(), () -> {
            Location loc = instance.entity().getLocation();
            Fx.burst(loc.clone().add(0, 1, 0), ambientParticle, 6, 1.2);
            BossAudio.play(loc, ambientSoundKey, ambientSoundFallback, 0.6f, 1.0f);
        }, AMBIENT_INTERVAL_TICKS, AMBIENT_INTERVAL_TICKS);
        instance.trackTask(task);

        return new Handle(snapshot, world, y);
    }

    private static long packed(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    final class Handle {
        private final Map<Long, Biome> snapshot;
        private final World world;
        private final int y;

        private Handle(Map<Long, Biome> snapshot, World world, int y) {
            this.snapshot = snapshot;
            this.world = world;
            this.y = y;
        }

        void end() {
            if (world == null) {
                return;
            }
            for (Map.Entry<Long, Biome> entry : snapshot.entrySet()) {
                long key = entry.getKey();
                int x = (int) (key >> 32);
                int z = (int) key;
                world.setBiome(x, y, z, entry.getValue());
            }
        }
    }
}
