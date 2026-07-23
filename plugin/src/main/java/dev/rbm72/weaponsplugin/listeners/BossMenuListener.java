package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.gui.BossMenu;
import dev.rbm72.weaponsplugin.gui.BossMenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/** Handles clicks in the boss menu: page navigation, spawning, and the shift-click hard mode toggle. */
public final class BossMenuListener implements Listener {

    private final WeaponsPlugin plugin;

    public BossMenuListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BossMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        if (BossMenu.isPageButton(plugin, clicked)) {
            holder.setPage(holder.page() + BossMenu.readPageDelta(plugin, clicked));
            BossMenu.render(plugin, player, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (!player.hasPermission("weaponsplugin.boss.spawn")) {
            return;
        }

        String bossId = BossMenu.readBossId(plugin, clicked);
        if (bossId == null) {
            return;
        }

        if (event.isShiftClick()) {
            boolean nowHardMode = plugin.bossManager().toggleHardMode(bossId);
            player.sendMessage(Component.text("Hard mode for that boss is now ", NamedTextColor.GOLD)
                    .append(nowHardMode ? Component.text("ENABLED", NamedTextColor.RED) : Component.text("off", NamedTextColor.WHITE)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
            BossMenu.render(plugin, player, holder);
            return;
        }

        plugin.bossManager().spawn(bossId, player.getLocation()).ifPresentOrElse(
                instance -> {
                    player.sendMessage(Component.text("Spawned ", NamedTextColor.AQUA).append(instance.boss().displayName()));
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.3f);
                    plugin.getServer().getScheduler().runTask(plugin, () -> BossMenu.render(plugin, player, holder));
                },
                () -> {
                    player.sendMessage(Component.text("That boss is already live.", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BossMenuHolder) {
            event.setCancelled(true);
        }
    }
}
