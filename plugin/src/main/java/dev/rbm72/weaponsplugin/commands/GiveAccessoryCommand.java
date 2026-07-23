package dev.rbm72.weaponsplugin.commands;

import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.accessory.AccessoryRegistry;
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

/** Gives the sender a custom accessory by id. */
public final class GiveAccessoryCommand implements CommandExecutor, TabCompleter {

    private final AccessoryRegistry registry;

    public GiveAccessoryCommand(AccessoryRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /giveaccessory <id>", NamedTextColor.RED));
            return true;
        }

        Optional<Accessory> accessory = registry.get(args[0]);
        if (accessory.isEmpty()) {
            player.sendMessage(Component.text("Unknown accessory: " + args[0], NamedTextColor.RED));
            return true;
        }

        player.getInventory().addItem(accessory.get().createItem());
        player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA).append(accessory.get().displayName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return registry.all().stream()
                .map(Accessory::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
