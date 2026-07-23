package dev.rbm72.weaponsplugin.accessory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Fires every equipped accessory's onEquipTick hook for each online player, twice a second. */
public final class AccessoryTickTask extends BukkitRunnable {

    private final AccessoryManager manager;

    public AccessoryTickTask(AccessoryManager manager) {
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
