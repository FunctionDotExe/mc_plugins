package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.progress.BossClearRecord;
import dev.rbm72.weaponsplugin.boss.progress.PlayerProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/**
 * {@code /bossleaderboard} ranks the server by total kills; {@code /bossleaderboard <id>} ranks one
 * boss by fastest clear. Reads the persistent ledger, so it is a record of the server's whole history
 * rather than of this session.
 */
public final class BossLeaderboardCommand implements CommandExecutor, TabCompleter {

    private static final int ROWS = 10;

    private final WeaponsPlugin plugin;

    public BossLeaderboardCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(Component.text("Usage: /bossleaderboard [boss id]", NamedTextColor.RED));
            return true;
        }
        if (args.length == 1) {
            sendBossBoard(sender, args[0]);
            return true;
        }

        List<PlayerProgress> rows = plugin.bossProgress().leaderboard();
        if (rows.isEmpty()) {
            sender.sendMessage(Component.text("Nobody has killed a boss yet.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("— Boss kills —", NamedTextColor.GOLD));
        int rank = 1;
        for (PlayerProgress row : rows.subList(0, Math.min(ROWS, rows.size()))) {
            sender.sendMessage(Component.text("  " + rank++ + ". ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(row.name(), NamedTextColor.WHITE))
                    .append(Component.text(" — " + row.totalKills() + " kill(s), "
                            + row.distinctClears() + " boss(es) beaten", NamedTextColor.GRAY)));
        }
        return true;
    }

    private void sendBossBoard(CommandSender sender, String bossId) {
        Boss boss = plugin.bossManager().get(bossId).orElse(null);
        if (boss == null) {
            sender.sendMessage(Component.text("Unknown boss: " + bossId, NamedTextColor.RED));
            return;
        }
        List<PlayerProgress> rows = plugin.bossProgress().leaderboard(boss.id());
        if (rows.isEmpty()) {
            sender.sendMessage(Component.text("No timed clears of ", NamedTextColor.GRAY)
                    .append(boss.displayName())
                    .append(Component.text(" yet.", NamedTextColor.GRAY)));
            return;
        }
        sender.sendMessage(Component.text("— Fastest clears: ", NamedTextColor.GOLD)
                .append(boss.displayName()).append(Component.text(" —", NamedTextColor.GOLD)));
        int rank = 1;
        for (PlayerProgress row : rows.subList(0, Math.min(ROWS, rows.size()))) {
            BossClearRecord record = row.record(boss.id());
            sender.sendMessage(Component.text("  " + rank++ + ". ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(row.name(), NamedTextColor.WHITE))
                    .append(Component.text(" — " + formatDuration(record.fastestMs())
                            + " (" + record.kills() + " kill(s))", NamedTextColor.GRAY)));
        }
    }

    /** {@code m:ss}, which is how a clear time is actually spoken about. */
    static String formatDuration(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000);
        return totalSeconds / 60 + ":" + String.format(Locale.ROOT, "%02d", totalSeconds % 60);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.bossManager().all().stream().map(Boss::id).filter(id -> id.startsWith(prefix)).toList();
    }
}
