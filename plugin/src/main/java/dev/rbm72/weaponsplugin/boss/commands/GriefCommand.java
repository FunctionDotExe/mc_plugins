package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Server-wide kill switch for boss grief: while on, every boss falls back to its cosmetic-only
 * particle/sound effects regardless of its own {@code grief} config flag — no block breaking,
 * craters, thrown blocks, or real explosions from any fight.
 */
public final class GriefCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean nowDisabled = !Grief.isGloballyDisabled();
        Grief.setGloballyDisabled(nowDisabled);
        sender.sendMessage(Component.text("Boss grief is now ", NamedTextColor.GOLD)
                .append(nowDisabled
                        ? Component.text("DISABLED for every boss", NamedTextColor.RED)
                        : Component.text("back to each boss's own setting", NamedTextColor.GREEN))
                .append(Component.text(".", NamedTextColor.GOLD)));
        return true;
    }
}
