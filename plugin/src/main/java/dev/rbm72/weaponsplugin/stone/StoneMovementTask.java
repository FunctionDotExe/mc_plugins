package dev.rbm72.weaponsplugin.stone;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The single per-tick loop every movement stone's frame-accurate work runs on.
 * <p>
 * Replaces {@code StoneWallRunTask}, which was a 1-tick timer that existed for one stone and hard-coded
 * that stone's physics inside itself. Once a second stone needed the same cadence — freezing water under a
 * sprint, catching a fall on a placed block, arming a double jump the tick you leave the ground — the
 * choice was a second full-roster 1-tick timer per stone or one loop that dispatches to
 * {@link Stone#onFastTick}. Each stone now owns its own physics again, in its own file, and there is
 * exactly one sweep over the online players.
 * <p>
 * Creative and spectator are skipped outright: every stone here writes velocity, flight flags or blocks
 * under the player, and all three mean something different (or nothing at all) in a game mode that already
 * flies.
 */
public final class StoneMovementTask extends BukkitRunnable {

    private final StoneManager manager;

    public StoneMovementTask(StoneManager manager) {
        this.manager = manager;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                continue;
            }
            manager.fastTick(player);
        }
    }
}
