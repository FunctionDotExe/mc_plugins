package dev.rbm72.weaponsplugin.commands;

import dev.rbm72.weaponsplugin.opitem.OpItem;
import dev.rbm72.weaponsplugin.opitem.OpItemRegistry;
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

/** Gives the sender an operator item by id. The command-line door to the same shelf the hub menu shows. */
public final class GiveOpItemCommand implements CommandExecutor, TabCompleter {

    private final OpItemRegistry registry;

    public GiveOpItemCommand(OpItemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /giveopitem <id>", NamedTextColor.RED));
            return true;
        }

        Optional<OpItem> item = registry.get(args[0]);
        if (item.isEmpty()) {
            player.sendMessage(Component.text("Unknown operator item: " + args[0], NamedTextColor.RED));
            return true;
        }

        player.getInventory().addItem(item.get().createItem());
        player.sendMessage(Component.text("You received the ", NamedTextColor.GOLD).append(item.get().displayName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return registry.all().stream()
                .map(OpItem::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
