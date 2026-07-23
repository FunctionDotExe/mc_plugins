package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.items.Weapon;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Brief lockout on ability casting right after a player swaps which weapon they're holding —
 * without it, a player mid-fight can bounce between two weapons' cooldown-free ability1 slots
 * instantly, turning "swap weapons" into a free extra ability cast every time.
 */
public final class WeaponSwitchLock {

    private static final long LOCK_MS = 500;

    private final Map<UUID, Long> lockedUntilMs = new HashMap<>();
    private final Map<UUID, String> lastWeaponId = new HashMap<>();

    /** Called whenever the item in a tracked hand changes; only arms the lock if a weapon identity actually changed. */
    public void onHeldItemChange(Player player, Weapon newWeapon) {
        UUID uuid = player.getUniqueId();
        String newId = newWeapon != null ? newWeapon.id() : null;
        String previous = lastWeaponId.put(uuid, newId);
        if (!Objects.equals(previous, newId) && (previous != null || newId != null)) {
            lockedUntilMs.put(uuid, System.currentTimeMillis() + LOCK_MS);
        }
    }

    public boolean isLocked(Player player) {
        Long until = lockedUntilMs.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }
}
