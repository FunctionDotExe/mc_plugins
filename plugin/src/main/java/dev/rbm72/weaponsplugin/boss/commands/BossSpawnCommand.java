package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossManager;
import dev.rbm72.weaponsplugin.gui.BossMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** No args opens the boss catalog GUI; {@code /bossspawn <id>} keeps working directly for macros/consoles. */
public final class BossSpawnCommand implements CommandExecutor, TabCompleter {

    private final WeaponsPlugin plugin;
    private final BossManager bossManager;

    public BossSpawnCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
        this.bossManager = plugin.bossManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.openInventory(BossMenu.open(plugin, player));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /bossspawn [id]", NamedTextColor.RED));
            return true;
        }

        bossManager.spawn(args[0], player.getLocation()).ifPresentOrElse(
                instance -> player.sendMessage(Component.text("Spawned ", NamedTextColor.AQUA)
                        .append(instance.boss().displayName())),
                () -> player.sendMessage(Component.text(
                        "Unknown boss, or that boss is already live: " + args[0], NamedTextColor.RED)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return bossManager.all().stream()
                .map(Boss::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
