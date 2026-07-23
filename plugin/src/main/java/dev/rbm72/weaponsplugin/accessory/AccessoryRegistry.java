package dev.rbm72.weaponsplugin.accessory;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Holds every registered accessory, keyed by id. Mirrors WeaponRegistry. */
public final class AccessoryRegistry {

    private final Map<String, Accessory> accessories = new LinkedHashMap<>();

    public void register(Accessory accessory) {
        accessories.put(accessory.id(), accessory);
    }

    public Optional<Accessory> get(String id) {
        return Optional.ofNullable(accessories.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Accessory> identify(ItemStack item) {
        for (Accessory accessory : accessories.values()) {
            if (accessory.matches(item)) {
                return Optional.of(accessory);
            }
        }
        return Optional.empty();
    }

    public Collection<Accessory> all() {
        return accessories.values();
    }
}
