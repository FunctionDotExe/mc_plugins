package dev.rbm72.weaponsplugin.commands;

import dev.rbm72.weaponsplugin.opitem.HeartManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and edits a player's bonus-heart tally — the same number a
 * {@link dev.rbm72.weaponsplugin.opitem.opitems.HeartVessel} writes to.
 * <p>
 * Two permission levels, because the two uses are different. Anyone may run bare {@code /hearts} to see what
 * they are carrying: it is their own health bar, and a stat with no way to read it is a bug report waiting to
 * happen. Changing a tally — their own or anyone else's — needs {@code weaponsplugin.hearts}, since it is the
 * same grant the operator shelf hands out and should not be self-serve.
 */
public final class HeartsCommand implements CommandExecutor, TabCompleter {

    private static final String EDIT_PERMISSION = "weaponsplugin.hearts";

    private final HeartManager hearts;

    public HeartsCommand(HeartManager hearts) {
        this.hearts = hearts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Usage: /hearts <add|remove|set|show> <amount> [player]", NamedTextColor.RED));
                return true;
            }
            showTally(sender, player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (action.equals("show")) {
            Player target = args.length >= 2 ? resolve(sender, args[1]) : (sender instanceof Player self ? self : null);
            if (target == null) {
                sender.sendMessage(Component.text("Usage: /hearts show [player]", NamedTextColor.RED));
                return true;
            }
            if (target != sender && !sender.hasPermission(EDIT_PERMISSION)) {
                sender.sendMessage(Component.text("You can only check your own hearts.", NamedTextColor.RED));
                return true;
            }
            showTally(sender, target);
            return true;
        }

        if (!action.equals("add") && !action.equals("remove") && !action.equals("set")) {
            sender.sendMessage(Component.text("Usage: /hearts [add|remove|set <amount> [player]] [show [player]]", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission(EDIT_PERMISSION)) {
            sender.sendMessage(Component.text("You don't have permission to change hearts.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /hearts " + action + " <amount> [player]", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Not a number: " + args[1], NamedTextColor.RED));
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(Component.text("Amount must not be negative — use remove instead.", NamedTextColor.RED));
            return true;
        }

        Player target = args.length >= 3 ? resolve(sender, args[2]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            sender.sendMessage(Component.text("Name a player: /hearts " + action + " " + amount + " <player>", NamedTextColor.RED));
            return true;
        }

        int before = hearts.hearts(target.getUniqueId());
        int after = switch (action) {
            case "add" -> hearts.add(target, amount);
            case "remove" -> hearts.add(target, -amount);
            default -> hearts.set(target, amount);
        };

        if (after == before) {
            sender.sendMessage(Component.text("No change — " + target.getName() + " is at "
                    + before + " (cap " + hearts.maxHearts() + ").", NamedTextColor.YELLOW));
            return true;
        }

        sender.sendMessage(Component.text(target.getName() + ": " + before + " ➜ " + after
                + " bonus heart" + (after == 1 ? "" : "s"), NamedTextColor.GOLD));
        if (target != sender) {
            target.sendMessage(Component.text("Your bonus hearts: " + before + " ➜ " + after, NamedTextColor.GOLD));
        }
        return true;
    }

    private void showTally(CommandSender sender, Player target) {
        int count = hearts.hearts(target.getUniqueId());
        sender.sendMessage(Component.text("♥ ", NamedTextColor.RED)
                .append(Component.text(target.getName() + ": " + count + " / " + hearts.maxHearts()
                        + " bonus hearts", NamedTextColor.GOLD)));
        if (sender.hasPermission(EDIT_PERMISSION)) {
            sender.sendMessage(Component.text("  /hearts add|remove|set <amount> [player]", NamedTextColor.DARK_GRAY));
        }
    }

    private Player resolve(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage(Component.text("No online player named " + name + ".", NamedTextColor.RED));
        }
        return target;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> actions = new ArrayList<>(List.of("show"));
            if (sender.hasPermission(EDIT_PERMISSION)) {
                actions.addAll(List.of("add", "remove", "set"));
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return actions.stream().filter(a -> a.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            return onlineNames(args[1]);
        }
        if (args.length == 2) {
            return List.of("1", "2", "5", "10");
        }
        if (args.length == 3) {
            return onlineNames(args[2]);
        }
        return List.of();
    }

    private List<String> onlineNames(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
