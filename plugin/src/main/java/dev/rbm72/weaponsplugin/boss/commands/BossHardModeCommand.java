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

/** Admin toggle exposing {@link dev.rbm72.weaponsplugin.boss.BossInstance#empower} as a standing "hard mode" for a boss id. */
public final class BossHardModeCommand implements CommandExecutor, TabCompleter {

    private final BossManager bossManager;

    public BossHardModeCommand(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /bosshardmode <id>", NamedTextColor.RED));
            return true;
        }
        if (bossManager.get(args[0]).isEmpty()) {
            sender.sendMessage(Component.text("Unknown boss: " + args[0], NamedTextColor.RED));
            return true;
        }

        boolean nowHard = bossManager.toggleHardMode(args[0]);
        sender.sendMessage(nowHard
                ? Component.text("Hard mode ENABLED for " + args[0] + " — takes effect on its next spawn.", NamedTextColor.RED)
                : Component.text("Hard mode disabled for " + args[0] + ".", NamedTextColor.YELLOW));
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
