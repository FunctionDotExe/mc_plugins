package dev.rbm72.weaponsplugin.stone;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Fires every equipped stone's onEquipTick hook for each online player, twice a second. */
public final class StoneTickTask extends BukkitRunnable {

    private final StoneManager manager;

    public StoneTickTask(StoneManager manager) {
        this.manager = manager;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, 10L, 10L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            manager.tick(player);
        }
    }
}
