package dev.rbm72.weaponsplugin.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one view of the whole weapon roster's numbers.
 * <p>
 * Fifty-seven weapons, each holding its damage and cooldowns in its own private fields behind its own
 * config keys, add up to a roster nobody can see. Per-file keys hide the distribution completely: there is
 * no way to ask "is this new legendary's burst out of line?" without opening the other fifty-six, so in
 * practice the question never gets asked and outliers are discovered by whoever finds them in combat. This
 * command answers it in one pass — ranked, with the roster's own median and spread as the yardstick.
 * <p>
 * <b>Every number here is a nominal comparison, not a simulation.</b> Real output depends on hit rate,
 * ability overlap, accessory cooldown scaling, crit variance and what is actually being hit. Chasing
 * simulation fidelity would produce a figure that looks authoritative and still cannot be compared across
 * two weapons with different ability shapes. So the sheet is deliberately a same-yardstick estimate: the
 * absolute values mean little, the ranking and the gaps mean a lot.
 * <p>
 * Weapons that have not yet declared a {@link Weapon#damageProfile()} are reported as <b>undeclared</b>
 * rather than as zero. An incomplete table that says so is useful; one that silently ranks unmigrated
 * weapons at the bottom would be actively misleading, and would make the §0.1 doctrine pass look like a
 * balance change.
 */
public final class WeaponBalanceCommand implements CommandExecutor, TabCompleter {

    /**
     * Assumed melee swings per second. Vanilla attack speed with a sword is 1.6/s; the roster's mauls and
     * axes are slower, but the attack-speed attribute is untouched by every weapon here, so one rate keeps
     * the melee term comparable instead of pretending to a precision the data does not support.
     */
    private static final double SWINGS_PER_SECOND = 1.6;

    /** Flags a weapon whose estimate sits this many times the roster median or beyond. */
    private static final double HIGH_OUTLIER_RATIO = 1.6;
    private static final double LOW_OUTLIER_RATIO = 0.55;

    private final WeaponsPlugin plugin;

    public WeaponBalanceCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /** One weapon's computed row. */
    private record Row(Weapon weapon, double meleeDps, double abilityDps, boolean declared,
                       int declaredSlots, int totalSlots) {

        double totalDps() {
            return meleeDps + abilityDps;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "chat";

        List<Row> rows = new ArrayList<>();
        for (Weapon weapon : plugin.weaponRegistry().all()) {
            rows.add(measure(weapon));
        }
        rows.sort(Comparator.comparingDouble(Row::totalDps).reversed());

        double median = median(rows.stream().filter(Row::declared).map(Row::totalDps).sorted().toList());

        switch (mode) {
            case "md", "markdown" -> writeFile(sender, rows, median, markdown(rows, median), "weapon-balance.md");
            case "csv" -> writeFile(sender, rows, median, csv(rows), "weapon-balance.csv");
            default -> sendChat(sender, rows, median);
        }
        return true;
    }

    /**
     * Computes one weapon's row.
     * <p>
     * Ability contribution is damage-per-cast divided by that slot's cooldown, which is the only shape that
     * lets a 6-damage 3-second ability be compared with a 30-damage 40-second one. A charge-gated ultimate
     * is costed at its cooldown floor: that is the fastest it can possibly recur, so the estimate stays an
     * upper bound rather than depending on how well a hypothetical player is playing.
     */
    private Row measure(Weapon weapon) {
        double meleeDps = weapon.effectiveMeleeDamage() * SWINGS_PER_SECOND;

        Map<Slot, Double> profile = weapon.damageProfile();
        double[] cooldowns = {
                weapon.ability1CooldownSeconds(), weapon.ability2CooldownSeconds(),
                weapon.ability3CooldownSeconds(), ultimateGate(weapon)};
        Slot[] slots = Slot.values();

        int usedSlots = 0;
        for (int i = 0; i < slots.length; i++) {
            if (cooldowns[i] > 0) {
                usedSlots++;
            }
        }

        double abilityDps = 0;
        int declaredSlots = 0;
        for (int i = 0; i < slots.length; i++) {
            Double damage = profile.get(slots[i]);
            if (damage == null || cooldowns[i] <= 0) {
                continue;
            }
            declaredSlots++;
            abilityDps += damage * weapon.rarity().statMultiplier() / cooldowns[i];
        }

        return new Row(weapon, meleeDps, abilityDps, declaredSlots > 0, declaredSlots, usedSlots);
    }

    /** A charged ultimate recurs no faster than its floor; a timed one, no faster than its cooldown. */
    private double ultimateGate(Weapon weapon) {
        return weapon.ultimateChargeSpec() != null
                ? weapon.ultimateChargeSpec().cooldownFloorSeconds()
                : weapon.ultimateCooldownSeconds();
    }

    private void sendChat(CommandSender sender, List<Row> rows, double median) {
        long declared = rows.stream().filter(Row::declared).count();
        sender.sendMessage(Component.text("Weapon balance — " + rows.size() + " weapons, "
                + declared + " with declared ability damage", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Median estimated DPS: "
                + String.format(Locale.ROOT, "%.1f", median)
                + "   (flagging above " + String.format(Locale.ROOT, "%.1f", median * HIGH_OUTLIER_RATIO)
                + " / below " + String.format(Locale.ROOT, "%.1f", median * LOW_OUTLIER_RATIO) + ")",
                NamedTextColor.GRAY));

        for (Row row : rows) {
            String flag = flagFor(row, median);
            NamedTextColor colour = flag.isEmpty() ? NamedTextColor.GRAY : NamedTextColor.YELLOW;
            sender.sendMessage(Component.text(String.format(Locale.ROOT,
                    "%-22s %-9s  dps %6.1f  (melee %5.1f + abil %5.1f)  %s",
                    row.weapon().id(),
                    row.weapon().rarity().label(),
                    row.totalDps(), row.meleeDps(), row.abilityDps(),
                    row.declared() ? flag : "undeclared " + row.declaredSlots() + "/" + row.totalSlots()),
                    row.declared() ? colour : NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(Component.text("/" + "weaponbalance md  — full table with cooldowns, "
                + "counterplay coverage and charge specs", NamedTextColor.DARK_GRAY));
    }

    /** Why this row is worth a second look, or empty when it sits in the roster's normal band. */
    private String flagFor(Row row, double median) {
        if (median <= 0 || !row.declared()) {
            return "";
        }
        double ratio = row.totalDps() / median;
        if (ratio >= HIGH_OUTLIER_RATIO) {
            return String.format(Locale.ROOT, "HIGH x%.2f", ratio);
        }
        if (ratio <= LOW_OUTLIER_RATIO) {
            return String.format(Locale.ROOT, "LOW x%.2f", ratio);
        }
        // Partial declaration is its own finding: the number shown is real but incomplete, so a weapon
        // that looks low may only be missing a slot.
        if (row.declaredSlots() < row.totalSlots()) {
            return "partial " + row.declaredSlots() + "/" + row.totalSlots();
        }
        return "";
    }

    private String markdown(List<Row> rows, double median) {
        StringBuilder out = new StringBuilder();
        out.append("# Weapon balance sheet\n\n")
                .append("Generated from the live registry — nominal same-yardstick estimates, not a simulation.\n")
                .append("Melee term assumes ").append(SWINGS_PER_SECOND).append(" swings/s. Ability term is ")
                .append("declared damage x rarity multiplier / cooldown, ultimate costed at its charge floor ")
                .append("where one exists.\n\n")
                .append("- Weapons: ").append(rows.size()).append('\n')
                .append("- With declared ability damage: ").append(rows.stream().filter(Row::declared).count())
                .append('\n')
                .append("- Median estimated DPS (declared only): ")
                .append(String.format(Locale.ROOT, "%.1f", median)).append("\n\n")
                .append("| Weapon | Rarity | Melee | Abil | DPS | vs median | CD a1/a2/a3/ult | Crit | Charge | Counterplay |\n")
                .append("|---|---|--:|--:|--:|--:|---|--:|---|---|\n");

        for (Row row : rows) {
            Weapon weapon = row.weapon();
            out.append("| `").append(weapon.id()).append("` | ")
                    .append(weapon.rarity().label()).append(" | ")
                    .append(String.format(Locale.ROOT, "%.1f", row.meleeDps())).append(" | ")
                    .append(row.declared() ? String.format(Locale.ROOT, "%.1f", row.abilityDps()) : "—")
                    .append(" | ")
                    .append(row.declared() ? String.format(Locale.ROOT, "%.1f", row.totalDps()) : "—")
                    .append(" | ")
                    .append(median > 0 && row.declared()
                            ? String.format(Locale.ROOT, "x%.2f", row.totalDps() / median) : "—")
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.0f/%.0f/%.0f/%.0f",
                            weapon.ability1CooldownSeconds(), weapon.ability2CooldownSeconds(),
                            weapon.ability3CooldownSeconds(), weapon.ultimateCooldownSeconds()))
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.0f%%x%.1f", weapon.critChance() * 100, weapon.critMultiplier()))
                    .append(" | ")
                    .append(weapon.ultimateChargeSpec() != null
                            ? weapon.ultimateChargeSpec().label() + " (floor "
                              + String.format(Locale.ROOT, "%.0fs", weapon.ultimateChargeSpec().cooldownFloorSeconds()) + ")"
                            : "cooldown")
                    .append(" | ")
                    .append(weapon.counterVerbs().isEmpty() ? "—" : weapon.counterVerbs().toString())
                    .append(" |\n");
        }

        out.append("\n## Undeclared ability damage\n\n")
                .append("These have not been through the §0.1 doctrine pass yet, so their ability term is ")
                .append("missing and their DPS column is not comparable:\n\n");
        List<String> undeclared = rows.stream().filter(row -> !row.declared())
                .map(row -> row.weapon().id()).sorted().toList();
        out.append(undeclared.isEmpty() ? "_none — the roster is fully declared._\n"
                : "`" + String.join("`, `", undeclared) + "`\n");

        out.append("\n## Counterplay coverage\n\n");
        for (Weapon.CounterVerb verb : Weapon.CounterVerb.values()) {
            List<String> answering = rows.stream()
                    .filter(row -> row.weapon().counterVerbs().contains(verb))
                    .map(row -> row.weapon().id()).sorted().toList();
            out.append("- **").append(verb.name()).append("** (").append(verb.description()).append("): ")
                    .append(answering.isEmpty() ? "_nothing answers this yet_"
                            : "`" + String.join("`, `", answering) + "`")
                    .append('\n');
        }

        out.append("\n## By rarity\n\n| Rarity | Count | Median DPS |\n|---|--:|--:|\n");
        for (Rarity rarity : Rarity.values()) {
            List<Double> band = rows.stream()
                    .filter(row -> row.weapon().rarity() == rarity && row.declared())
                    .map(Row::totalDps).sorted().toList();
            long count = rows.stream().filter(row -> row.weapon().rarity() == rarity).count();
            out.append("| ").append(rarity.label()).append(" | ").append(count).append(" | ")
                    .append(band.isEmpty() ? "—" : String.format(Locale.ROOT, "%.1f", median(band)))
                    .append(" |\n");
        }
        return out.toString();
    }

    private String csv(List<Row> rows) {
        StringBuilder out = new StringBuilder(
                "id,rarity,melee_dps,ability_dps,total_dps,declared_slots,used_slots,"
                + "cd_ability1,cd_ability2,cd_ability3,cd_ultimate,crit_chance,crit_multiplier,"
                + "charge_label,counter_verbs\n");
        for (Row row : rows) {
            Weapon weapon = row.weapon();
            out.append(weapon.id()).append(',')
                    .append(weapon.rarity().name()).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", row.meleeDps())).append(',')
                    .append(row.declared() ? String.format(Locale.ROOT, "%.2f", row.abilityDps()) : "").append(',')
                    .append(row.declared() ? String.format(Locale.ROOT, "%.2f", row.totalDps()) : "").append(',')
                    .append(row.declaredSlots()).append(',')
                    .append(row.totalSlots()).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", weapon.ability1CooldownSeconds())).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", weapon.ability2CooldownSeconds())).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", weapon.ability3CooldownSeconds())).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", weapon.ultimateCooldownSeconds())).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", weapon.critChance())).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", weapon.critMultiplier())).append(',')
                    .append(weapon.ultimateChargeSpec() != null ? weapon.ultimateChargeSpec().label() : "").append(',')
                    .append(weapon.counterVerbs().stream().map(Enum::name).sorted()
                            .reduce((a, b) -> a + ";" + b).orElse(""))
                    .append('\n');
        }
        return out.toString();
    }

    private void writeFile(CommandSender sender, List<Row> rows, double median, String content, String fileName) {
        Path target = plugin.getDataFolder().toPath().resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            sender.sendMessage(Component.text("Could not write " + fileName + ": " + e.getMessage(),
                    NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("Wrote " + rows.size() + " weapons to " + target
                + " (median DPS " + String.format(Locale.ROOT, "%.1f", median) + ")", NamedTextColor.GREEN));
    }

    /** Median of an already-sorted list, 0 for an empty one. Even counts average the middle pair. */
    private double median(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length <= 1 ? List.of("chat", "md", "csv") : List.of();
    }
}
