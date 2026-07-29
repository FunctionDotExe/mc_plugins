package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.harness.BossDryRun;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /bosstest <id>} force-runs every attack of one boss and asserts the fight leaks nothing;
 * {@code /bosstest all} walks the whole roster one boss at a time.
 * <p>
 * Deliberately destructive: it spawns a real fight in the world you are standing in, which will damage
 * terrain (and roll it back), so run it somewhere you do not mind that happening — a realm, or a scratch
 * world. That is the trade for a check that exercises the real code paths rather than a mocked
 * approximation of them.
 */
public final class BossTestCommand implements CommandExecutor, TabCompleter {

    /** Gap between bosses in an {@code all} run — long enough for the previous rollback to finish. */
    private static final long BETWEEN_BOSSES_TICKS = 100L;

    private final WeaponsPlugin plugin;

    public BossTestCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can run a dry run — the boss needs a target.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /bosstest <id|all>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            List<Boss> roster = new ArrayList<>(plugin.bossManager().all());
            player.sendMessage(Component.text("Dry-running the whole roster (" + roster.size()
                    + " bosses). This will take a while and will wreck the terrain here.", NamedTextColor.AQUA));
            runAll(player, roster, 0);
            return true;
        }

        Boss boss = plugin.bossManager().get(args[0]).orElse(null);
        if (boss == null) {
            player.sendMessage(Component.text("Unknown boss: " + args[0], NamedTextColor.RED));
            return true;
        }
        new BossDryRun(plugin, player, boss).start();
        return true;
    }

    /**
     * One boss at a time, spaced out. Sequential rather than concurrent because the leak assertions count
     * entities and scheduled tasks near the arena — two overlapping runs would each see the other's props
     * and tasks and report them as each other's leaks.
     */
    private void runAll(Player player, List<Boss> roster, int index) {
        if (index >= roster.size()) {
            player.sendMessage(Component.text("Roster dry run finished.", NamedTextColor.AQUA));
            return;
        }
        Boss boss = roster.get(index);
        new BossDryRun(plugin, player, boss).start();
        long spacing = BETWEEN_BOSSES_TICKS + estimateTicks(boss);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> runAll(player, roster, index + 1), spacing);
    }

    /** Rough upper bound on how long one boss's dry run takes, so the next one does not start on top of it. */
    private static long estimateTicks(Boss boss) {
        int attacks = 0;
        for (var phase : boss.phases()) {
            attacks += phase.attacks().size();
        }
        // 120 ticks per attack is the harness's own per-attack ceiling; padding beyond that is the
        // settle window plus the arena rollback.
        return attacks * 120L + 80L;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if ("all".startsWith(prefix)) {
            options.add("all");
        }
        plugin.bossManager().all().stream().map(Boss::id).filter(id -> id.startsWith(prefix)).forEach(options::add);
        return options;
    }
}
