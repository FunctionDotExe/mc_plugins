package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Reloads config.yml only — every boss/weapon/attack tunable is already read live off
 * {@code plugin.getConfig()} on each use (see {@code Boss#configDouble} et al.), so this alone
 * makes tuning changes take effect immediately without the full-server risk of vanilla {@code /reload}.
 */
public final class BossReloadCommand implements CommandExecutor {

    private final WeaponsPlugin plugin;

    public BossReloadCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.reloadConfig();
        sender.sendMessage(Component.text("config.yml reloaded — boss/weapon tuning updated.", NamedTextColor.GREEN));
        return true;
    }
}
