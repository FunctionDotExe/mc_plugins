package dev.rbm72.weaponsplugin.boss.meter;

import org.bukkit.entity.Player;

/**
 * What a full meter does to the player who filled it — frozen solid, struck by lightning and chaining
 * outward, ruptured, banished.
 * <p>
 * The meter itself has already reset the value before this runs (see {@link PlayerMeter}), so a payload
 * may safely kill the player, teleport them, or spend seconds of scheduled follow-up without the meter
 * re-firing underneath it.
 * <p>
 * Payloads must never <em>reward</em> the failure. A meter whose detonation is a net gain (a cleanse,
 * a damage window, a free reposition) inverts the entire point of the system: the fastest way to play
 * would be to cap it deliberately, and the boss's anti-facetank answer becomes a rotation cooldown.
 */
@FunctionalInterface
public interface MeterThreshold {

    /**
     * Called on the main thread the pulse a player's meter reaches its cap.
     *
     * @param meter  the meter that filled — reach {@link PlayerMeter#instance()} for the fight,
     *               {@link PlayerMeter#afflictions()} to hold the player in place, and
     *               {@link PlayerMeter#add} to spike other players' meters
     * @param player the player who filled it. Guaranteed a combatant and online at the moment of the
     *               call; anything scheduled from here must re-check both.
     */
    void fire(PlayerMeter meter, Player player);
}
