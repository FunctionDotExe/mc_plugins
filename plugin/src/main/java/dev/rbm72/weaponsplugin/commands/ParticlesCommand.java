package dev.rbm72.weaponsplugin.commands;

import dev.rbm72.weaponsplugin.fx.PlayerParticlePrefs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Personal particle density control — every player sets their own weapon-hit and boss-ability
 * particle volume independently of everyone else's, unlike the server-wide admin knob in
 * {@link dev.rbm72.weaponsplugin.boss.commands.BossParticlesCommand}. Stored on the player's own
 * {@link PlayerParticlePrefs} so it survives relogs and restarts.
 */
public final class ParticlesCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text(
                    "Your particle scale — weapon: " + PlayerParticlePrefs.weaponPercent(player) + "%, boss: "
                            + PlayerParticlePrefs.bossPercent(player) + "% (usage: /particles <weapon|boss> <0-200>)",
                    NamedTextColor.YELLOW));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /particles <weapon|boss> <0-200>", NamedTextColor.RED));
            return true;
        }

        String category = args[0].toLowerCase(java.util.Locale.ROOT);
        if (!category.equals("weapon") && !category.equals("boss")) {
            player.sendMessage(Component.text("Category must be 'weapon' or 'boss'.", NamedTextColor.RED));
            return true;
        }

        int requested;
        try {
            requested = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Not a number: " + args[1], NamedTextColor.RED));
            return true;
        }

        int clamped = PlayerParticlePrefs.clamp(requested);
        if (category.equals("weapon")) {
            PlayerParticlePrefs.setWeaponPercent(player, clamped);
            player.sendMessage(Component.text("Your weapon particle scale set to " + clamped + "%.", NamedTextColor.GREEN));
        } else {
            PlayerParticlePrefs.setBossPercent(player, clamped);
            player.sendMessage(Component.text("Your boss particle scale set to " + clamped + "%.", NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("weapon", "boss");
        }
        if (args.length == 2) {
            return List.of("0", "25", "50", "75", "100", "150", "200");
        }
        return List.of();
    }
}
