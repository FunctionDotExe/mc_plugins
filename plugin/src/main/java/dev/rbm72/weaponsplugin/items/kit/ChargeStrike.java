package dev.rbm72.weaponsplugin.items.kit;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Consumer;

/**
 * The one thing a spear ability needs that its own cast does not already hand it.
 * <p>
 * A spear's charge attack is vanilla's own: the player is moving under their own momentum and
 * {@link dev.rbm72.weaponsplugin.listeners.WeaponChargeListener} fires the weapon's ability1 the moment it
 * connects. The entity struck comes with that hit, so an ability that wants a <em>target</em> just uses it.
 * What no spear ability knows is where its caster will come to rest, which is the interesting spot for
 * anything a spear <em>plants</em> — a rod, a crystal, a spike thicket.
 * <p>
 * This helper does not move the player or apply damage. The charge is the game's; the payoff is the weapon's.
 */
public final class ChargeStrike {

    private ChargeStrike() {
    }

    /**
     * Runs {@code atRest} where the caster comes to a stop after a connect.
     * <p>
     * Waiting a fixed number of ticks rather than watching for {@code isOnGround} is deliberate: a charge
     * that ends in the air (off a ledge, off a boss's knockback) still has to resolve, and "wait until you
     * land" is how an ability silently never fires.
     */
    public static void afterCharge(WeaponsPlugin plugin, Player caster, int ticks, Consumer<Location> atRest) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (caster.isOnline()) {
                    atRest.accept(caster.getLocation());
                }
            }
        }.runTaskLater(plugin, Math.max(1, ticks));
    }
}
