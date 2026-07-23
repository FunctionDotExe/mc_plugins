package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossManager;
import dev.rbm72.weaponsplugin.boss.LootTable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/** Read-only lookup so players can check a boss's stats and drop odds without digging through config.yml. */
public final class BossInfoCommand implements CommandExecutor, TabCompleter {

    private final BossManager bossManager;

    public BossInfoCommand(BossManager bossManager) {
        this.bossManager = bossManager;
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
        sender.sendMessage(Component.text("Hard mode: ", NamedTextColor.GRAY)
                .append(bossManager.isHardMode(boss.id())
                        ? Component.text("ENABLED", NamedTextColor.RED)
                        : Component.text("off", NamedTextColor.WHITE)));

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
