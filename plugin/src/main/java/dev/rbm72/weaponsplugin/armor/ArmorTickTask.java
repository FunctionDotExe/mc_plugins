package dev.rbm72.weaponsplugin.armor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Fires every registered armor set's onTick hook for each online player, twice a second. */
public final class ArmorTickTask extends BukkitRunnable {

    private final ArmorManager manager;

    public ArmorTickTask(ArmorManager manager) {
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
