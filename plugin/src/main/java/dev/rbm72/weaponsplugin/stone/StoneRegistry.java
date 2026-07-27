package dev.rbm72.weaponsplugin.stone;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Holds every registered movement stone, keyed by id. Mirrors AccessoryRegistry. */
public final class StoneRegistry {

    private final Map<String, Stone> stones = new LinkedHashMap<>();

    public void register(Stone stone) {
        stones.put(stone.id(), stone);
    }

    public Optional<Stone> get(String id) {
        return Optional.ofNullable(stones.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Stone> identify(ItemStack item) {
        for (Stone stone : stones.values()) {
            if (stone.matches(item)) {
                return Optional.of(stone);
            }
        }
        return Optional.empty();
    }

    public Collection<Stone> all() {
        return stones.values();
    }
}
