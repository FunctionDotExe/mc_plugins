package dev.rbm72.weaponsplugin.boss;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Every boss sound goes through a namespaced key instead of a direct
 * {@link Sound} reference, so a future resource pack can register a real
 * custom sound under that key without touching any boss/attack code. Until
 * then every key falls back to a vanilla sound.
 */
public final class BossAudio {

    private static final Map<String, Sound> OVERRIDES = new HashMap<>();

    private BossAudio() {
    }

    public static void play(Location loc, String key, Sound fallback, float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(loc, OVERRIDES.getOrDefault(key, fallback), volume, pitch);
    }
}
