package dev.rbm72.weaponsplugin.consumable;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/** Shared healing math for consumables — restoring health without ever overshooting the health bar. */
public final class Healing {

    private Healing() {
    }

    /** Heals {@code amount} half-hearts, capped at the player's max health. */
    public static void heal(Player player, double amount) {
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    /** Heals {@code fraction} of the player's max health — scales with buffs instead of a flat number that stops mattering. */
    public static void healFraction(Player player, double fraction) {
        heal(player, maxHealth(player) * fraction);
    }

    public static double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}
