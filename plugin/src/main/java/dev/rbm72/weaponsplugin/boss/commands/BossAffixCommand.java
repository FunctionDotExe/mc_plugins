package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.modifier.BossModifier;
import dev.rbm72.weaponsplugin.boss.modifier.BossModifiers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Arms and disarms composable difficulty affixes:
 * {@code /bossaffix <id> <add|remove|toggle> <affix>}, {@code /bossaffix <id> clear},
 * {@code /bossaffix <id>} to see what is armed, {@code /bossaffix list} for the catalogue.
 * <p>
 * The general form of {@code /bosshardmode}, which stays as a shortcut for the one affix that had a
 * command of its own.
 */
public final class BossAffixCommand implements CommandExecutor, TabCompleter {

    private final WeaponsPlugin plugin;

    public BossAffixCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        BossModifiers modifiers = plugin.bossManager().modifiers();

        if (args.length == 0) {
            sendUsage(sender, modifiers);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            sendCatalogue(sender);
            return true;
        }

        Boss boss = plugin.bossManager().get(args[0]).orElse(null);
        if (boss == null) {
            sender.sendMessage(Component.text("Unknown boss: " + args[0], NamedTextColor.RED));
            return true;
        }

        if (args.length == 1) {
            sendArmed(sender, boss, modifiers);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("clear")) {
            modifiers.clear(boss.id());
            sender.sendMessage(Component.text("Cleared every affix on " + boss.id()
                    + " — takes effect on its next spawn.", NamedTextColor.YELLOW));
            return true;
        }
        if (args.length != 3 || !List.of("add", "remove", "toggle").contains(action)) {
            sendUsage(sender, modifiers);
            return true;
        }

        BossModifier affix = BossModifier.byId(args[2]).orElse(null);
        if (affix == null) {
            sender.sendMessage(Component.text("Unknown affix: " + args[2]
                    + " — try /bossaffix list", NamedTextColor.RED));
            return true;
        }

        boolean armedNow;
        switch (action) {
            case "add" -> {
                modifiers.set(boss.id(), affix, true);
                armedNow = true;
            }
            case "remove" -> {
                modifiers.set(boss.id(), affix, false);
                armedNow = false;
            }
            default -> armedNow = modifiers.toggle(boss.id(), affix);
        }
        sender.sendMessage(Component.text(affix.displayName() + (armedNow ? " ARMED on " : " disarmed on ")
                        + boss.id() + " — takes effect on its next spawn.",
                armedNow ? NamedTextColor.RED : NamedTextColor.YELLOW));
        sendArmed(sender, boss, modifiers);
        return true;
    }

    private void sendUsage(CommandSender sender, BossModifiers modifiers) {
        sender.sendMessage(Component.text("Usage: /bossaffix <id> <add|remove|toggle> <affix>", NamedTextColor.RED));
        sender.sendMessage(Component.text("       /bossaffix <id> clear   ·   /bossaffix <id>   ·   /bossaffix list",
                NamedTextColor.GRAY));
        List<String> armed = modifiers.armedBosses();
        if (!armed.isEmpty()) {
            sender.sendMessage(Component.text("Currently armed: " + String.join(", ", armed), NamedTextColor.LIGHT_PURPLE));
        }
    }

    private void sendCatalogue(CommandSender sender) {
        sender.sendMessage(Component.text("— Affixes (stackable, any combination) —", NamedTextColor.GOLD));
        for (BossModifier affix : BossModifier.values()) {
            sender.sendMessage(Component.text("  " + affix.id(), NamedTextColor.WHITE)
                    .append(Component.text(" — " + affix.displayName() + ": " + affix.description(),
                            NamedTextColor.GRAY)));
        }
    }

    private void sendArmed(CommandSender sender, Boss boss, BossModifiers modifiers) {
        Set<BossModifier> armed = modifiers.of(boss.id());
        if (armed.isEmpty()) {
            sender.sendMessage(Component.text("No affixes armed on " + boss.id() + ".", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Armed on " + boss.id() + ": ", NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", modifiers.names(boss.id())), NamedTextColor.LIGHT_PURPLE)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if ("list".startsWith(prefix)) {
                options.add("list");
            }
            plugin.bossManager().all().stream().map(Boss::id).filter(id -> id.startsWith(prefix)).forEach(options::add);
        } else if (args.length == 2) {
            for (String action : List.of("add", "remove", "toggle", "clear")) {
                if (action.startsWith(prefix)) {
                    options.add(action);
                }
            }
        } else if (args.length == 3) {
            for (BossModifier affix : BossModifier.values()) {
                if (affix.id().startsWith(prefix)) {
                    options.add(affix.id());
                }
            }
        }
        return options;
    }
}
