package dev.rbm72.weaponsplugin.boss;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * A boss's looping theme track, played as a custom named sound under {@link SoundCategory#MUSIC} so it
 * respects each client's own music slider. Unlike {@link BossAudio}, there is no vanilla fallback for a
 * full theme song — a client without the pack simply hears nothing — so this has its own admin volume
 * knob ({@code boss-audio.music-scale}, {@code /bossmusic}) rather than reusing {@code custom-sounds}.
 * <p>
 * The pack has no way to loop a long track by itself, so this re-triggers playback on a repeating task
 * timed to the track's own length ({@link Boss#themeMusicLoopSeconds()}) — the same re-trigger trick
 * {@link BossAmbiance} uses for its short ambient loop, just timed to a multi-minute track instead of a
 * few seconds.
 */
public final class BossMusic {

    private static final String NAMESPACE = "weaponsplugin";

    private static Plugin plugin;

    private BossMusic() {
    }

    /** Wired from {@code WeaponsPlugin#onEnable}. */
    public static void init(Plugin instance) {
        plugin = instance;
    }

    /** Admin multiplier on top of each client's own Music slider — {@code /bossmusic <0.0-2.0>}. */
    public static double runtimeScale() {
        if (plugin == null) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(2.0, plugin.getConfig().getDouble("boss-audio.music-scale", 1.0)));
    }

    /** Starts the loop for {@code instance}'s boss if it declared a theme key, or returns null. */
    static Handle start(BossInstance instance) {
        Boss boss = instance.boss();
        String key = boss.themeMusicKey();
        double loopSeconds = boss.themeMusicLoopSeconds();
        if (key == null || plugin == null || loopSeconds <= 0.0) {
            return null;
        }
        String soundName = NAMESPACE + ":" + key;
        long intervalTicks = Math.max(1L, Math.round(loopSeconds * 20.0));

        Runnable playOnce = () -> {
            if (runtimeScale() <= 0.0) {
                return;
            }
            float volume = (float) runtimeScale();
            for (Player player : instance.arena().playersInside()) {
                // Played at the listener's own location: full volume, no positional falloff — a
                // theme track isn't meant to fade as you walk to the arena's edge.
                player.playSound(player.getLocation(), soundName, SoundCategory.MUSIC, volume, 1.0f);
            }
        };
        playOnce.run();
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, playOnce, intervalTicks, intervalTicks);
        instance.trackTask(task);
        return new Handle(soundName);
    }

    /** Cuts the track for everyone currently hearing it — the repeating task alone only stops future replays. */
    static final class Handle {
        private final String soundName;

        private Handle(String soundName) {
            this.soundName = soundName;
        }

        void end() {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.stopSound(soundName, SoundCategory.MUSIC);
            }
        }
    }
}
