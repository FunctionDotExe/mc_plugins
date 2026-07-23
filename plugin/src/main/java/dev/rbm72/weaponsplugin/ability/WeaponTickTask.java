package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Fires each held weapon's onTick passive hook every 10 ticks (0.5s), plus a shared idle-hold sparkle keyed to rarity. */
public final class WeaponTickTask extends BukkitRunnable {

    private final WeaponRegistry registry;

    public WeaponTickTask(WeaponRegistry registry) {
        this.registry = registry;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, 10L, 10L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            registry.identify(player.getInventory().getItemInMainHand()).ifPresent(weapon -> {
                weapon.onTick(player);
                ambientSparkle(player, weapon);
            });
        }
    }

    /** Rarity-colored dust drifting off the held weapon — higher rarity weapons sparkle harder. */
    private void ambientSparkle(Player player, Weapon weapon) {
        Location handLoc = player.getEyeLocation()
                .add(player.getLocation().getDirection().multiply(0.5))
                .subtract(0, 0.3, 0);
        int count = 1 + weapon.rarity().ordinal();
        Fx.coloredBurst(handLoc, weapon.rarity().bukkitColor(), 0.6f, count, 0.06);
    }
}
