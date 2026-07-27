package dev.rbm72.weaponsplugin.consumable;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Holds every registered consumable, keyed by id. Mirrors {@code StoneRegistry}. */
public final class ConsumableRegistry {

    private final Map<String, Consumable> consumables = new LinkedHashMap<>();

    public void register(Consumable consumable) {
        consumables.put(consumable.id(), consumable);
    }

    public Optional<Consumable> get(String id) {
        return Optional.ofNullable(consumables.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Consumable> identify(ItemStack item) {
        for (Consumable consumable : consumables.values()) {
            if (consumable.matches(item)) {
                return Optional.of(consumable);
            }
        }
        return Optional.empty();
    }

    public Collection<Consumable> all() {
        return consumables.values();
    }
}
