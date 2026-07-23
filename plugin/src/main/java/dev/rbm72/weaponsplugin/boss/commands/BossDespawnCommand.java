package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class BossDespawnCommand implements CommandExecutor, TabCompleter {

    private final BossManager bossManager;

    public BossDespawnCommand(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /bossdespawn <id>", NamedTextColor.RED));
            return true;
        }

        if (bossManager.despawn(args[0])) {
            sender.sendMessage(Component.text("Despawned: " + args[0], NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("No live boss with id: " + args[0], NamedTextColor.RED));
        }
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
