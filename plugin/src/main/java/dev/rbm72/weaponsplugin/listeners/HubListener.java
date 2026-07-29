package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.gui.HubItem;
import dev.rbm72.weaponsplugin.gui.HubMenu;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Owns the nether-star hub item: hands it out and pins it to slot 9, opens the
 * hub menu on right-click, and blocks every action that would move, drop, or
 * swap it out of its slot. Also drops each player's accessory cache on quit.
 */
public final class HubListener implements Listener {

    private final WeaponsPlugin plugin;

    public HubListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        HubItem.ensure(plugin, event.getPlayer());
        resetStrandedScale(event.getPlayer());
        // Bonus hearts are a stored tally, not a saved attribute — see HeartManager#apply, which is
        // idempotent precisely so this can run on every join without stacking up.
        plugin.heartManager().apply(event.getPlayer());
    }

    /**
     * Safety net for weapons that temporarily resize the wielder (Soulcrown's Hollow Coronation,
     * Vitriol's Mitosis, Tearfall's Contraction): their revert task no-ops if the player logs out
     * mid-effect, which would otherwise leave that player permanently shrunk/grown on every future
     * join. Unconditionally flattening scale back to 1.0 on join is simpler and safer than trying to
     * track and re-arm every in-flight revert task across every weapon that ever does this.
     */
    private void resetStrandedScale(Player player) {
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null && scaleAttr.getBaseValue() != 1.0) {
            scaleAttr.setBaseValue(1.0);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            HubItem.ensure(plugin, player);
            // Deferred by a tick like the hub star: the respawn's own health reset lands first, so applying the
            // modifier here is what makes a player with bonus hearts respawn on the bar they actually have.
            plugin.heartManager().apply(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.accessoryManager().unload(event.getPlayer().getUniqueId());
        plugin.stoneManager().unload(event.getPlayer().getUniqueId());
        plugin.ridableManager().unload(event.getPlayer().getUniqueId());
        plugin.heartManager().unload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!HubItem.isHubItem(plugin, event.getItem())) {
            return;
        }
        event.setCancelled(true);
        player.openInventory(HubMenu.open(player));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    /** Stop the star from being picked up, moved, or hotbar-swapped inside any inventory view. */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (HubItem.isHubItem(plugin, event.getCurrentItem())
                || HubItem.isHubItem(plugin, event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        // Number-key swap targeting the pinned hotbar slot would pull the star out.
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == HubItem.SLOT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (HubItem.isHubItem(plugin, event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        ItemStack main = event.getMainHandItem();
        ItemStack off = event.getOffHandItem();
        if (HubItem.isHubItem(plugin, main) || HubItem.isHubItem(plugin, off)) {
            event.setCancelled(true);
        }
    }
}
