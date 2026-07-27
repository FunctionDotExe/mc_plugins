package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.consumable.Consumable;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Right-click to use a held {@link Consumable}, spending one of its charges.
 *
 * <p>Two independent gates, and they fail differently on purpose: no charges banked means waiting on
 * the recharge timer (told plainly on the action bar, since there's nothing on screen otherwise
 * explaining the refusal), while a use cooldown that's still running is vanilla's own item cooldown,
 * which the client already draws as a grey sweep across the icon — that one needs no words.
 */
public final class ConsumableUseListener implements Listener {

    private static final long NOTICE_MS = 1500;

    private final WeaponsPlugin plugin;

    public ConsumableUseListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        plugin.consumableRegistry().identify(item).ifPresent(consumable -> {
            // Consumed here regardless of the outcome below: a denied sip must not fall through to
            // placing the block the player happened to be looking at.
            event.setCancelled(true);

            if (player.hasCooldown(consumable.material())) {
                return;
            }
            if (!plugin.consumableManager().use(player, consumable, item)) {
                Fx.sound(player, consumable.emptySound(), 0.6f, 0.7f);
                double next = plugin.consumableManager().secondsToNextCharge(consumable, item);
                plugin.actionBarHub().flash(player, consumable.displayName()
                                .append(Component.text(String.format(Locale.ROOT, " — recharging, %.0fs", Math.ceil(next)),
                                        NamedTextColor.RED)),
                        NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
            }
        });
    }
}
