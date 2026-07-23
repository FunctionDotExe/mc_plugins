package dev.rbm72.weaponsplugin.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.gui.HubItem;
import dev.rbm72.weaponsplugin.gui.HubMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Opens the hub menu and restores the nether-star item if it's missing. */
public final class HubCommand implements CommandExecutor {

    private final WeaponsPlugin plugin;

    public HubCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        HubItem.ensure(plugin, player);
        player.openInventory(HubMenu.open(player));
        return true;
    }
}
