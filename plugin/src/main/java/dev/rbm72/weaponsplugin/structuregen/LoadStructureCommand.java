package dev.rbm72.weaponsplugin.structuregen;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /loadstructure <bossId>} generates a themed dungeon underground near the sender, ending in
 * a chest holding that boss's realm crystal, and reports the surface entrance coordinates so an
 * admin can {@code /tp} straight there instead of digging for it.
 */
public final class LoadStructureCommand implements CommandExecutor, TabCompleter {

    private final WeaponsPlugin plugin;

    public LoadStructureCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /loadstructure <bossId>", NamedTextColor.RED));
            return true;
        }
        String bossId = args[0].toLowerCase(Locale.ROOT);
        if (plugin.bossManager().get(bossId).isEmpty()) {
            player.sendMessage(Component.text("Unknown boss: " + bossId, NamedTextColor.RED));
            return true;
        }

        double radius = plugin.getConfig().getDouble("structures.search-radius", 250.0);
        int minDepth = plugin.getConfig().getInt("structures.min-depth", 20);
        int maxDepth = plugin.getConfig().getInt("structures.max-depth", 45);
        Location anchor = DungeonBuilder.randomUndergroundAnchor(player.getWorld(), player.getLocation(),
                radius, minDepth, maxDepth);

        DungeonBuilder.build(plugin, bossId, anchor).ifPresentOrElse(
                result -> {
                    Location entrance = result.entrance();
                    player.sendMessage(Component.text("Dungeon generated. Entrance at ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text(entrance.getBlockX() + ", " + entrance.getBlockY() + ", "
                                    + entrance.getBlockZ(), NamedTextColor.AQUA))
                            .append(Component.text(" — crystal is sealed inside until every room is cleared.",
                                    NamedTextColor.LIGHT_PURPLE)));
                },
                () -> player.sendMessage(Component.text("That boss has no realm to seal a crystal for: " + bossId,
                        NamedTextColor.RED)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.bossManager().all().stream()
                .map(Boss::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
