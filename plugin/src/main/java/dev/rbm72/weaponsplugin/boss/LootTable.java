package dev.rbm72.weaponsplugin.boss;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/** Guaranteed entries always drop; weighted entries roll independently against their own chance. */
public final class LootTable {

    private record WeightedEntry(double chance, Supplier<ItemStack> item) {
    }

    /** One rolled drop plus the odds it dropped at — 1.0 for guaranteed entries, its own chance otherwise. Used for rare-drop broadcasts and {@code /bossinfo}. */
    public record RolledDrop(ItemStack item, double chance) {
    }

    private final List<Supplier<ItemStack>> guaranteed = new ArrayList<>();
    private final List<WeightedEntry> weighted = new ArrayList<>();
    private final List<Supplier<ItemStack>> firstClear = new ArrayList<>();

    public LootTable guaranteed(Supplier<ItemStack> item) {
        guaranteed.add(item);
        return this;
    }

    /** @param chance in [0, 1] — e.g. 0.008 for a sub-1% cosmetic drop. */
    public LootTable weighted(double chance, Supplier<ItemStack> item) {
        weighted.add(new WeightedEntry(chance, item));
        return this;
    }

    /**
     * Handed <em>directly to each player</em> the first time they personally clear this boss, and never
     * again — not dropped on the ground, so a repeat clearer standing next to them cannot pick it up.
     * <p>
     * The only entry kind that needs the persistent kill ledger
     * ({@link dev.rbm72.weaponsplugin.boss.progress.BossProgressStore}) to mean anything, and the reason
     * that ledger had to exist before first-clear rewards could: "have they beaten this before" is not a
     * question a fight can answer from its own state.
     */
    public LootTable firstClear(Supplier<ItemStack> item) {
        firstClear.add(item);
        return this;
    }

    public List<ItemStack> roll() {
        List<ItemStack> drops = new ArrayList<>();
        for (RolledDrop drop : rollWithOdds()) {
            drops.add(drop.item());
        }
        return drops;
    }

    /** Same roll as {@link #roll()} but keeps each drop's odds — lets callers flag a rare hit for a broadcast. */
    public List<RolledDrop> rollWithOdds() {
        return rollWithOdds(1.0);
    }

    /**
     * @param guaranteedChance odds each guaranteed entry actually drops this time, in [0, 1]. 1.0 is the
     *        normal case — a first clear for anybody present. Below 1.0 is a pure farm run (every player
     *        in the arena has killed this boss before), where the signature item becomes a roll rather
     *        than a certainty. That difference is the whole of "first-clear vs repeat loot": without it a
     *        group's hundredth kill pays exactly like their first, and the roster has no progression
     *        curve at all, only a treadmill. See {@code Boss#repeatClearSignatureChance()}.
     */
    public List<RolledDrop> rollWithOdds(double guaranteedChance) {
        Random random = new Random();
        List<RolledDrop> drops = new ArrayList<>();
        for (Supplier<ItemStack> item : guaranteed) {
            if (guaranteedChance >= 1.0 || random.nextDouble() < guaranteedChance) {
                drops.add(new RolledDrop(item.get(), Math.min(1.0, Math.max(0.0, guaranteedChance))));
            }
        }
        for (WeightedEntry entry : weighted) {
            if (random.nextDouble() < entry.chance()) {
                drops.add(new RolledDrop(entry.item().get(), entry.chance()));
            }
        }
        return drops;
    }

    /** Every first-clear entry, freshly built. One call per first-clearing player — they each get their own copy. */
    public List<ItemStack> rollFirstClear() {
        List<ItemStack> items = new ArrayList<>();
        for (Supplier<ItemStack> item : firstClear) {
            items.add(item.get());
        }
        return items;
    }

    public boolean hasFirstClear() {
        return !firstClear.isEmpty();
    }

    /** Every weighted entry's odds and a sample item (for display name/type) — never rolled, purely descriptive. Used by {@code /bossinfo}. */
    public List<RolledDrop> describeWeighted() {
        List<RolledDrop> entries = new ArrayList<>();
        for (WeightedEntry entry : weighted) {
            entries.add(new RolledDrop(entry.item().get(), entry.chance()));
        }
        return entries;
    }

    /** True if this table has at least one guaranteed entry — {@code /bossinfo} shows "always drops" without naming it (item identity is decided by the boss, not the table). */
    public boolean hasGuaranteed() {
        return !guaranteed.isEmpty();
    }
}
