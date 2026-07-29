package dev.rbm72.weaponsplugin.structuregen;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking a sealed {@link DungeonMapItem} generates its dungeon at a random underground spot
 * near the player and swaps the held item for a real, location-baked map — this is the point a
 * cartographer-bought map actually costs the world any blocks, not the moment it's purchased.
 */
public final class DungeonMapRevealListener implements Listener {

    private final WeaponsPlugin plugin;

    public DungeonMapRevealListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        String bossId = DungeonMapItem.sealedBossId(plugin, item);
        if (bossId == null) {
            return;
        }
        event.setCancelled(true);

        var player = event.getPlayer();
        if (plugin.bossManager().get(bossId).isEmpty()) {
            player.sendMessage(Component.text("This map's boss no longer exists. Nothing happened.", NamedTextColor.RED));
            return;
        }

        double radius = plugin.getConfig().getDouble("structures.search-radius", 250.0);
        int minDepth = plugin.getConfig().getInt("structures.min-depth", 20);
        int maxDepth = plugin.getConfig().getInt("structures.max-depth", 45);
        Location anchor = DungeonBuilder.randomUndergroundAnchor(player.getWorld(), player.getLocation(),
                radius, minDepth, maxDepth);

        DungeonBuilder.build(plugin, bossId, anchor).ifPresentOrElse(result -> {
            Location entrance = result.entrance();
            var boss = plugin.bossManager().get(bossId).orElseThrow();
            ItemStack revealed = DungeonMapItem.createRevealed(boss.displayName(), entrance);
            player.getInventory().setItemInMainHand(revealed);
            player.sendMessage(Component.text("The map reveals a dig site at ", NamedTextColor.GOLD)
                    .append(Component.text(entrance.getBlockX() + ", " + entrance.getBlockY() + ", "
                            + entrance.getBlockZ(), NamedTextColor.AQUA))
                    .append(Component.text(".", NamedTextColor.GOLD)));
        }, () -> player.sendMessage(Component.text("That boss has no realm to seal a crystal for.", NamedTextColor.RED)));
    }
}
