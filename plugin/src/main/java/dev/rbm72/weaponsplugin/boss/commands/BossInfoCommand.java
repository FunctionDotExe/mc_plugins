package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossManager;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.modifier.BossModifier;
import dev.rbm72.weaponsplugin.boss.progress.BossClearRecord;
import dev.rbm72.weaponsplugin.boss.progress.PlayerProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only lookup so players can check a boss's stats and drop odds without digging through config.yml,
 * plus their own history with it — kills, first clear, personal best, and whether its clear gate is open.
 */
public final class BossInfoCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter CLEAR_DATE =
            DateTimeFormatter.ofPattern("MMM d yyyy").withZone(ZoneId.systemDefault());

    private final WeaponsPlugin plugin;
    private final BossManager bossManager;

    public BossInfoCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
        this.bossManager = plugin.bossManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /bossinfo <id>", NamedTextColor.RED));
            return true;
        }

        Boss boss = bossManager.get(args[0]).orElse(null);
        if (boss == null) {
            sender.sendMessage(Component.text("Unknown boss: " + args[0], NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("— ", NamedTextColor.DARK_GRAY).append(boss.displayName())
                .append(Component.text(" —", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("Health: ", NamedTextColor.GRAY)
                .append(Component.text((long) boss.maxHealth() + " HP (base, before group-size/hard-mode scaling)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Arena radius: ", NamedTextColor.GRAY)
                .append(Component.text(boss.arenaRadius() + " blocks", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Phases: ", NamedTextColor.GRAY)
                .append(Component.text(boss.phases().size(), NamedTextColor.WHITE)));
        Set<BossModifier> affixes = bossManager.modifiers().of(boss.id());
        sender.sendMessage(Component.text("Affixes: ", NamedTextColor.GRAY)
                .append(affixes.isEmpty()
                        ? Component.text("none", NamedTextColor.WHITE)
                        : Component.text(String.join(", ", bossManager.modifiers().names(boss.id())),
                                NamedTextColor.RED)));

        sendProgress(sender, boss);

        sender.sendMessage(Component.text("Loot:", NamedTextColor.GOLD));
        LootTable lootTable = boss.lootTable();
        if (lootTable.hasGuaranteed()) {
            sender.sendMessage(Component.text("  Always drops its signature item", NamedTextColor.WHITE));
        }
        for (LootTable.RolledDrop entry : lootTable.describeWeighted()) {
            String name = itemName(entry.item());
            sender.sendMessage(Component.text("  " + name + ": ", NamedTextColor.WHITE)
                    .append(Component.text(String.format("%.2f%%", entry.chance() * 100), NamedTextColor.YELLOW)));
        }
        return true;
    }

    /**
     * The asking player's own history with this boss, and the state of its clear gate.
     * <p>
     * Shown here rather than in a command of its own because this is where players already look before a
     * pull, and because a gate whose requirement is invisible until you are refused at the door is a
     * worse gate than no gate — the requirement is the content pointer.
     */
    private void sendProgress(CommandSender sender, Boss boss) {
        if (!(sender instanceof Player player)) {
            return;
        }
        PlayerProgress progress = plugin.bossProgress().progress(player);
        BossClearRecord record = progress.record(boss.id());

        if (record.cleared()) {
            String best = record.fastestMs() > 0
                    ? ", best " + BossLeaderboardCommand.formatDuration(record.fastestMs()) : "";
            sender.sendMessage(Component.text("Your record: ", NamedTextColor.GRAY)
                    .append(Component.text(record.kills() + " kill(s), first on "
                            + CLEAR_DATE.format(Instant.ofEpochMilli(record.firstClearMs())) + best,
                            NamedTextColor.AQUA)));
        } else {
            sender.sendMessage(Component.text("Your record: ", NamedTextColor.GRAY)
                    .append(Component.text("never beaten", NamedTextColor.WHITE)));
        }

        int required = boss.requiredClears();
        if (required <= 0) {
            return;
        }
        int have = progress.distinctClearsExcluding(boss.id());
        sender.sendMessage(have >= required
                ? Component.text("Clear gate: ", NamedTextColor.GRAY)
                        .append(Component.text("open (" + have + "/" + required + " bosses beaten)", NamedTextColor.GREEN))
                : Component.text("Clear gate: ", NamedTextColor.GRAY)
                        .append(Component.text("LOCKED — beat " + (required - have) + " more distinct boss(es) ("
                                + have + "/" + required + ")", NamedTextColor.RED)));
    }

    private static String itemName(org.bukkit.inventory.ItemStack item) {
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return LegacyComponentSerializer.legacySection().serialize(meta.displayName());
        }
        return item.getType().toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return bossManager.all().stream()
                .map(Boss::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
