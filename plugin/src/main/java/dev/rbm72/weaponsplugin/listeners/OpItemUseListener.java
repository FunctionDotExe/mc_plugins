package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.opitem.OpItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click to use a held {@link OpItem}.
 *
 * <p>Cancelling the event is not optional and not merely tidy. The op shelf is built on materials vanilla
 * already does something with — a potion is drunk, a melon slice is eaten — so an uncancelled interact would run
 * this plugin's grant <em>and</em> vanilla's consumption, emptying two items for one use and playing an
 * animation for something the server had already resolved. Cancelling makes the item inert to vanilla and this
 * listener the only thing that spends it.
 *
 * <p>The permission is re-checked here rather than trusted from wherever the item came from: an op item that
 * has been dropped, traded or left in a chest is still an op item, and it should do nothing in the hands of
 * someone who was never meant to have one.
 */
public final class OpItemUseListener implements Listener {

    private final WeaponsPlugin plugin;

    public OpItemUseListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * HIGH so the cancel lands before anything at default priority reads the item, but still ahead of the
     * MONITOR-priority observers that only watch.
     */
    @EventHandler(priority = EventPriority.HIGH)
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
        plugin.opItemRegistry().identify(item).ifPresent(opItem -> {
            // Cancelled whatever happens next: a refused use must not fall through to drinking the potion or
            // placing the block the player happened to be looking at.
            event.setCancelled(true);

            if (!player.hasPermission("weaponsplugin.op")) {
                player.sendMessage(Component.text("That item isn't yours to use.", NamedTextColor.RED));
                Fx.sound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                return;
            }

            if (!opItem.onUse(player)) {
                // onUse said nothing happened and has already explained why. The item stays in hand.
                return;
            }

            Fx.sound(player, opItem.useSound(), 1.0f, 1.0f);
            if (opItem.consumedOnUse()) {
                item.setAmount(item.getAmount() - 1);
            }
        });
    }
}
