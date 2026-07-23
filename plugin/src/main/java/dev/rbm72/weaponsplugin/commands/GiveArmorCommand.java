package dev.rbm72.weaponsplugin.commands;

import dev.rbm72.weaponsplugin.armor.ArmorPiece;
import dev.rbm72.weaponsplugin.armor.ArmorRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Gives the sender a custom armor piece by id. */
public final class GiveArmorCommand implements CommandExecutor, TabCompleter {

    private final ArmorRegistry registry;

    public GiveArmorCommand(ArmorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /givearmor <id>", NamedTextColor.RED));
            return true;
        }

        Optional<ArmorPiece> piece = registry.getPiece(args[0]);
        if (piece.isEmpty()) {
            player.sendMessage(Component.text("Unknown armor piece: " + args[0], NamedTextColor.RED));
            return true;
        }

        player.getInventory().addItem(piece.get().createItem());
        player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(piece.get().displayName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return registry.allPieces().stream()
                .map(ArmorPiece::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
