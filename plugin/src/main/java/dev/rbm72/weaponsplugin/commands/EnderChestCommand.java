package dev.rbm72.weaponsplugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /enderchest} opens your own ender chest; {@code /enderchest <name>}
 * lets a permitted op view and edit an online player's ender chest live.
 */
public final class EnderChestCommand implements CommandExecutor, TabCompleter {

    private static final String OTHERS_PERMISSION = "weaponsplugin.enderchest.others";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.openInventory(player.getEnderChest());
            return true;
        }

        if (!player.hasPermission(OTHERS_PERMISSION)) {
            player.sendMessage(Component.text("You don't have permission to view other players' ender chests.", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("Player '" + args[0] + "' is not online.", NamedTextColor.RED));
            return true;
        }

        player.openInventory(target.getEnderChest());
        player.sendMessage(Component.text("Viewing ", NamedTextColor.AQUA)
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text("'s ender chest.", NamedTextColor.AQUA)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
