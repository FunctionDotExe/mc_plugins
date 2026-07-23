package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/** Fires the held weapon's onBlockBreak passive hook — drives tool-style weapons (e.g. a pickaxe reskin with an AoE break). */
public final class WeaponBlockBreakListener implements Listener {

    private final WeaponRegistry registry;

    public WeaponBlockBreakListener(WeaponRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Weapon weapon = registry.identify(player.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null) {
            return;
        }
        weapon.onBlockBreak(player, event);
    }
}
