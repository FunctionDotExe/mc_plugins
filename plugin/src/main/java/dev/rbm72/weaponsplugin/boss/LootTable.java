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

    public LootTable guaranteed(Supplier<ItemStack> item) {
        guaranteed.add(item);
        return this;
    }

    /** @param chance in [0, 1] — e.g. 0.008 for a sub-1% cosmetic drop. */
    public LootTable weighted(double chance, Supplier<ItemStack> item) {
        weighted.add(new WeightedEntry(chance, item));
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
        Random random = new Random();
        List<RolledDrop> drops = new ArrayList<>();
        for (Supplier<ItemStack> item : guaranteed) {
            drops.add(new RolledDrop(item.get(), 1.0));
        }
        for (WeightedEntry entry : weighted) {
            if (random.nextDouble() < entry.chance()) {
                drops.add(new RolledDrop(entry.item().get(), entry.chance()));
            }
        }
        return drops;
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
