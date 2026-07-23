package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.ability.WeaponSwitchLock;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/** Feeds every mainhand hotbar swap and offhand swap into {@link WeaponSwitchLock}. */
public final class WeaponSwitchListener implements Listener {

    private final WeaponRegistry registry;
    private final WeaponSwitchLock switchLock;

    public WeaponSwitchListener(WeaponRegistry registry, WeaponSwitchLock switchLock) {
        this.registry = registry;
        this.switchLock = switchLock;
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        Weapon newWeapon = registry.identify(newItem).orElse(null);
        switchLock.onHeldItemChange(player, newWeapon);
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        // The swap hasn't applied yet at event-fire time, so the item headed to the mainhand is
        // whatever is currently in the offhand.
        Weapon newWeapon = registry.identify(event.getOffHandItem()).orElse(null);
        switchLock.onHeldItemChange(player, newWeapon);
    }
}
