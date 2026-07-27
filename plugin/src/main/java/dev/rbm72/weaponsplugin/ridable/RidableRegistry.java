package dev.rbm72.weaponsplugin.ridable;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Holds every registered ridable saddle, keyed by id. Mirrors AccessoryRegistry. */
public final class RidableRegistry {

    private final Map<String, Ridable> ridables = new LinkedHashMap<>();

    public void register(Ridable ridable) {
        ridables.put(ridable.id(), ridable);
    }

    public Optional<Ridable> get(String id) {
        return Optional.ofNullable(ridables.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Ridable> identify(ItemStack item) {
        for (Ridable ridable : ridables.values()) {
            if (ridable.matches(item)) {
                return Optional.of(ridable);
            }
        }
        return Optional.empty();
    }

    /** The saddle (if any) registered for the given live mob type. */
    public Optional<Ridable> forEntityType(org.bukkit.entity.EntityType type) {
        for (Ridable ridable : ridables.values()) {
            if (ridable.targetEntityType() == type) {
                return Optional.of(ridable);
            }
        }
        return Optional.empty();
    }

    public Collection<Ridable> all() {
        return ridables.values();
    }
}
