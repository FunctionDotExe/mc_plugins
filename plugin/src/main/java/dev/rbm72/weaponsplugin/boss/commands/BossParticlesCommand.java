package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Admin knob for {@link Fx#runtimeScale()} — the extra multiplier on top of the plugin's baseline
 * particle counts, for servers/players finding boss fights too visually noisy. Persists to
 * {@code fx.particle-scale} in config.yml so it survives a restart; takes effect immediately since
 * {@code Fx} reads it live rather than caching it at startup.
 */
public final class BossParticlesCommand implements CommandExecutor, TabCompleter {

    private final WeaponsPlugin plugin;

    public BossParticlesCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text(
                    "Current particle scale: " + Fx.runtimeScale() + " (usage: /bossparticles <0.1-2.0>)",
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
        double clamped = Math.max(0.1, Math.min(2.0, requested));
        plugin.getConfig().set("fx.particle-scale", clamped);
        plugin.saveConfig();
        sender.sendMessage(Component.text("Boss particle scale set to " + clamped + ".", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return List.of("0.25", "0.5", "0.75", "1.0", "1.5", "2.0");
    }
}
