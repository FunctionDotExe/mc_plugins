package dev.rbm72.weaponsplugin.items;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class WeaponRegistry {

    private final Map<String, Weapon> weapons = new LinkedHashMap<>();

    public void register(Weapon weapon) {
        weapons.put(weapon.id(), weapon);
    }

    public Optional<Weapon> get(String id) {
        return Optional.ofNullable(weapons.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Weapon> identify(ItemStack item) {
        for (Weapon weapon : weapons.values()) {
            if (weapon.matches(item)) {
                return Optional.of(weapon);
            }
        }
        return Optional.empty();
    }

    public Collection<Weapon> all() {
        return weapons.values();
    }
}
