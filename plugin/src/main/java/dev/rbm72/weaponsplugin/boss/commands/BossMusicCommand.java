package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossMusic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Admin knob for {@link BossMusic#runtimeScale()} — the multiplier applied on top of each listening
 * client's own Music slider for a boss's looping theme track. Persists to {@code boss-audio.music-scale}
 * in config.yml so it survives a restart; 0 silences boss theme music entirely without touching whether
 * a boss has one.
 */
public final class BossMusicCommand implements CommandExecutor, TabCompleter {

    private final WeaponsPlugin plugin;

    public BossMusicCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text(
                    "Current boss music volume: " + BossMusic.runtimeScale() + " (usage: /bossmusic <0.0-2.0>)",
                    NamedTextColor.YELLOW));
            return true;
        }
        double requested;
        try {
            requested = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Not a number: " + args[0], NamedTextColor.RED));
            return true;
        }
        double clamped = Math.max(0.0, Math.min(2.0, requested));
        plugin.getConfig().set("boss-audio.music-scale", clamped);
        plugin.saveConfig();
        sender.sendMessage(Component.text("Boss music volume set to " + clamped + ".", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return List.of("0.0", "0.5", "1.0", "1.5", "2.0");
    }
}
