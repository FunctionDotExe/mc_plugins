package dev.rbm72.weaponsplugin.armor;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Holds every registered armor set, keyed by id. Mirrors WeaponRegistry / AccessoryRegistry. */
public final class ArmorRegistry {

    private final Map<String, ArmorSet> sets = new LinkedHashMap<>();

    public void register(ArmorSet set) {
        sets.put(set.id(), set);
    }

    public Optional<ArmorSet> get(String id) {
        return Optional.ofNullable(sets.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<ArmorPiece> identifyPiece(ItemStack item) {
        for (ArmorSet set : sets.values()) {
            for (ArmorPiece piece : set.pieces().values()) {
                if (piece.matches(item)) {
                    return Optional.of(piece);
                }
            }
        }
        return Optional.empty();
    }

    /** Every piece across every set, flattened — used for /givearmor lookups and tab-completion. */
    public Collection<ArmorPiece> allPieces() {
        List<ArmorPiece> all = new ArrayList<>();
        for (ArmorSet set : sets.values()) {
            all.addAll(set.pieces().values());
        }
        return all;
    }

    public Optional<ArmorPiece> getPiece(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        for (ArmorPiece piece : allPieces()) {
            if (piece.id().equals(lower)) {
                return Optional.of(piece);
            }
        }
        return Optional.empty();
    }

    public Collection<ArmorSet> all() {
        return sets.values();
    }
}
