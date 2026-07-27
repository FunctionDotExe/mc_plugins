package dev.rbm72.weaponsplugin.commands;

import dev.rbm72.weaponsplugin.consumable.Consumable;
import dev.rbm72.weaponsplugin.consumable.ConsumableRegistry;
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

/** Gives the sender a healing consumable by id, at full charges. */
public final class GiveConsumableCommand implements CommandExecutor, TabCompleter {

    private final ConsumableRegistry registry;

    public GiveConsumableCommand(ConsumableRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /giveconsumable <id>", NamedTextColor.RED));
            return true;
        }

        Optional<Consumable> consumable = registry.get(args[0]);
        if (consumable.isEmpty()) {
            player.sendMessage(Component.text("Unknown consumable: " + args[0], NamedTextColor.RED));
            return true;
        }

        player.getInventory().addItem(consumable.get().createItem());
        player.sendMessage(Component.text("You received the ", NamedTextColor.AQUA)
                .append(consumable.get().displayName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return registry.all().stream()
                .map(Consumable::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
