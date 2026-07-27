package dev.rbm72.weaponsplugin.boss.ai;

import dev.rbm72.weaponsplugin.boss.Arena;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/**
 * Picks the boss's current target: the lowest-health-fraction player inside
 * the arena (approximates "pressure the weakest/support player"), skipping
 * spectators/creative and anyone who's left the arena or gone offline.
 * Re-run every tick, so a boss naturally switches off a target that dies,
 * disconnects, or walks out of range.
 */
public final class TargetSelector {

    private TargetSelector() {
    }

    public static Player select(Arena arena, Player previous) {
        List<Player> candidates = arena.playersInside().stream()
                .filter(Player::isOnline)
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .filter(p -> p.getGameMode() != GameMode.CREATIVE)
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.stream().min(Comparator.comparingDouble(TargetSelector::healthFraction)).orElse(previous);
    }

    private static double healthFraction(Player player) {
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        return player.getHealth() / maxHealth;
    }
}
