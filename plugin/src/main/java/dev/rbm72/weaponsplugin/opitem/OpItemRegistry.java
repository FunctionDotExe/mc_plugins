package dev.rbm72.weaponsplugin.opitem;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Holds every registered operator item, keyed by id. Mirrors {@code ConsumableRegistry}. */
public final class OpItemRegistry {

    private final Map<String, OpItem> items = new LinkedHashMap<>();

    public void register(OpItem item) {
        items.put(item.id(), item);
    }

    public Optional<OpItem> get(String id) {
        return Optional.ofNullable(items.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<OpItem> identify(ItemStack stack) {
        for (OpItem item : items.values()) {
            if (item.matches(stack)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public Collection<OpItem> all() {
        return items.values();
    }
}
