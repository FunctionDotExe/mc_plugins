package dev.rbm72.weaponsplugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player, in-memory toggle (resets on restart, same as cooldowns
 * themselves) that lets ops skip every weapon cooldown entirely while
 * testing.
 */
public final class OpCooldownCommand implements CommandExecutor {

    private final Set<UUID> bypassing = new HashSet<>();

    public boolean hasBypass(Player player) {
        return bypassing.contains(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (bypassing.remove(uuid)) {
            player.sendMessage(Component.text("Cooldown bypass: OFF", NamedTextColor.YELLOW));
        } else {
            bypassing.add(uuid);
            player.sendMessage(Component.text("Cooldown bypass: ON", NamedTextColor.GREEN));
        }
        return true;
    }
}
