package dev.rbm72.weaponsplugin.items;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ShieldRegistry {

    private final Map<String, Shield> shields = new LinkedHashMap<>();

    public void register(Shield shield) {
        shields.put(shield.id(), shield);
    }

    public Optional<Shield> get(String id) {
        return Optional.ofNullable(shields.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Shield> identify(ItemStack item) {
        for (Shield shield : shields.values()) {
            if (shield.matches(item)) {
                return Optional.of(shield);
            }
        }
        return Optional.empty();
    }

    public Collection<Shield> all() {
        return shields.values();
    }
}
