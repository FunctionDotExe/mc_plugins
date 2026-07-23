package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every weapon ability slot already answers to one of the four right-click combinations, so
 * accessory "personal abilities" (see {@link dev.rbm72.weaponsplugin.accessory.Accessory#hasPersonalAbility})
 * get their own trigger instead: double-tapping sneak within {@link #DOUBLE_TAP_WINDOW_MS}.
 */
public final class AccessoryPersonalAbilityListener implements Listener {

    private static final long DOUBLE_TAP_WINDOW_MS = 400;

    private final AccessoryManager accessories;
    private final Map<UUID, Long> lastSneakStartMs = new HashMap<>();

    public AccessoryPersonalAbilityListener(AccessoryManager accessories) {
        this.accessories = accessories;
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long lastStart = lastSneakStartMs.put(player.getUniqueId(), now);
        if (lastStart == null || now - lastStart > DOUBLE_TAP_WINDOW_MS) {
            return;
        }

        if (accessories.triggerPersonalAbilities(player)) {
            lastSneakStartMs.remove(player.getUniqueId());
        } else if (accessories.hasEquippedPersonalAbility(player)) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
        }
    }
}
