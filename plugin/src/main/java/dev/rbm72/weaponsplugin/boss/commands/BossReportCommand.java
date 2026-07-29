package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.telemetry.FightRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prints a finished fight's telemetry in a readable shape: {@code /bossreport} for the last one,
 * {@code /bossreport <id>} for the last of a particular boss, {@code /bossreport list} for what is in
 * memory.
 * <p>
 * The tuning questions this is built to answer, in order of how often they come up: which phase is
 * eating all the time, which mechanic is never engaged, and what is doing the killing. Everything else
 * lives in the JSON file.
 */
public final class BossReportCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault());
    /** Attacks listed per report, highest-use first — a full pool dump buries the signal. */
    private static final int TOP_ATTACKS = 6;

    private final WeaponsPlugin plugin;

    public BossReportCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sendList(sender);
            return true;
        }

        FightRecord record = args.length == 0 ? plugin.fightLog().last() : plugin.fightLog().last(args[0]);
        if (record == null) {
            sender.sendMessage(Component.text(args.length == 0
                    ? "No fights recorded this session yet."
                    : "No recorded fight for '" + args[0] + "' this session.", NamedTextColor.RED));
            return true;
        }
        send(sender, record);
        return true;
    }

    private void sendList(CommandSender sender) {
        List<FightRecord> recent = plugin.fightLog().recent();
        if (recent.isEmpty()) {
            sender.sendMessage(Component.text("No fights recorded this session yet.", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("— Recent fights (newest first) —", NamedTextColor.GOLD));
        for (FightRecord record : recent) {
            sender.sendMessage(Component.text("  " + STAMP.format(Instant.ofEpochMilli(record.startedAtMs()))
                            + "  " + record.bossId(), NamedTextColor.GRAY)
                    .append(Component.text("  " + outcome(record) + "  P" + record.deepestPhase()
                                    + "  " + record.durationMs() / 1000 + "s",
                            record.endReason().equals("DEFEATED") ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
    }

    private void send(CommandSender sender, FightRecord record) {
        sender.sendMessage(Component.text("— " + record.bossName() + " · "
                + STAMP.format(Instant.ofEpochMilli(record.startedAtMs())) + " —", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Outcome: ", NamedTextColor.GRAY)
                .append(Component.text(outcome(record),
                        record.endReason().equals("DEFEATED") ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("  ·  " + record.durationMs() / 1000 + "s"
                                + "  ·  reached phase " + record.deepestPhase()
                                + "  ·  boss left at " + Math.round(record.endHealthFraction() * 100) + "%",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("Players: ", NamedTextColor.GRAY)
                .append(Component.text(record.playersAtStart() + " at start, " + record.survivorsAtEnd()
                        + " standing at the end, " + record.deaths().size() + " death(s)", NamedTextColor.WHITE)));
        if (!record.modifiers().isEmpty()) {
            sender.sendMessage(Component.text("Affixes: ", NamedTextColor.GRAY)
                    .append(Component.text(String.join(", ", record.modifiers()), NamedTextColor.LIGHT_PURPLE)));
        }

        sender.sendMessage(Component.text("Phases:", NamedTextColor.GOLD));
        for (FightRecord.PhaseRecord phase : record.phases()) {
            NamedTextColor colour = !phase.hadMechanic() ? NamedTextColor.GRAY
                    : phase.mechanicBypassed() ? NamedTextColor.RED : NamedTextColor.GREEN;
            String mechanic = !phase.hadMechanic() ? "no objective"
                    : phase.mechanicBypassed() ? "OBJECTIVE NEVER ENGAGED (timed out)"
                    : phase.exposures() + " objective completion(s)";
            sender.sendMessage(Component.text("  P" + (phase.index() + 1) + " " + phase.name() + " — "
                    + phase.durationMs(record.startedAtMs() + record.durationMs()) / 1000 + "s, "
                    + phase.deaths() + " death(s), " + mechanic, colour));
        }

        if (!record.deaths().isEmpty()) {
            sender.sendMessage(Component.text("Deaths:", NamedTextColor.GOLD));
            for (FightRecord.DeathRecord death : record.deaths()) {
                sender.sendMessage(Component.text("  " + death.player() + " — " + death.killingAttack()
                        + " (" + death.phase() + ", " + death.msIntoFight() / 1000 + "s in)", NamedTextColor.RED));
            }
        }

        List<Map.Entry<String, Integer>> attacks = new ArrayList<>(record.attackTotals().entrySet());
        attacks.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        if (!attacks.isEmpty()) {
            sender.sendMessage(Component.text("Most-used attacks:", NamedTextColor.GOLD));
            for (Map.Entry<String, Integer> entry : attacks.subList(0, Math.min(TOP_ATTACKS, attacks.size()))) {
                sender.sendMessage(Component.text("  " + entry.getKey() + " ×" + entry.getValue(), NamedTextColor.WHITE));
            }
        }

        // Attacks the boss never got off at all. A whole authored pool that never fires is a phase whose
        // health band is too short for its own kit — invisible from inside the fight, obvious here.
        List<String> authored = new ArrayList<>();
        plugin.bossManager().get(record.bossId()).ifPresent(boss -> boss.phases()
                .forEach(phase -> phase.attacks().forEach(attack -> {
                    if (!authored.contains(attack.name())) {
                        authored.add(attack.name());
                    }
                })));
        List<String> unused = record.unusedAttacks(authored);
        if (!unused.isEmpty()) {
            sender.sendMessage(Component.text("Never fired (" + unused.size() + "/" + authored.size() + "): ",
                            NamedTextColor.YELLOW)
                    .append(Component.text(String.join(", ", unused), NamedTextColor.GRAY)));
        }

        sender.sendMessage(Component.text("Full JSON in plugins/WeaponsPlugin/telemetry/", NamedTextColor.DARK_GRAY));
    }

    private static String outcome(FightRecord record) {
        if (record.endReason().equals("DEFEATED")) {
            return "KILLED";
        }
        return record.wipe() ? "WIPE" : record.endReason();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if ("list".startsWith(prefix)) {
            options.add("list");
        }
        plugin.bossManager().all().stream().map(Boss::id).filter(id -> id.startsWith(prefix)).forEach(options::add);
        return options;
    }
}
