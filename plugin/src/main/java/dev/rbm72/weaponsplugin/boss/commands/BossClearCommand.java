package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.boss.BossManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Ends every live boss fight and restores every arena immediately, as if nobody ever pulled it. */
public final class BossClearCommand implements CommandExecutor {

    private final BossManager bossManager;

    public BossClearCommand(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int cleared = bossManager.clearAll();
        if (cleared == 0) {
            sender.sendMessage(Component.text("No live boss fights — every arena is already fresh.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Cleared " + cleared + " live boss fight(s) — arenas restored.", NamedTextColor.AQUA));
        }
        return true;
    }
}
