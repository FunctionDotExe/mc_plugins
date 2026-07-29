package dev.rbm72.weaponsplugin.items.kit;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Consumer;

/**
 * The two things a spear ability needs to know about a lunge it did not itself perform.
 * <p>
 * A spear's charged release is vanilla's own movement: the player is thrown forward by the game, and
 * {@link dev.rbm72.weaponsplugin.listeners.WeaponLungeListener} fires the weapon's ability1 at the moment
 * that starts. So unlike every other ability in the roster, a spear ability has no idea where its caster
 * will end up or what they will run into — it has to watch the lunge happen. Both helpers here do that
 * watching, because five weapons doing it by hand is five chances to leak a timer.
 * <p>
 * Neither helper moves the player or applies damage. The lunge is the game's; the payoff is the weapon's.
 */
public final class LungeStrike {

    /** What a lunge ran into, and where the collision happened. */
    public interface Contact {
        void hit(LivingEntity victim, Location at);
    }

    private LungeStrike() {
    }

    /**
     * Reports the first living thing the caster's lunge reaches, once, then stops watching.
     * <p>
     * First-contact rather than sweep-everything on purpose: a spear thrust that connects with one target
     * is the fantasy, and a lunge through a mob pack should not hit six things for full damage because the
     * caster happened to aim into a crowd. Weapons that want the crowd hit do their own AoE off the contact
     * location.
     *
     * @param radius how close counts as contact — roughly the lunge's own reach
     * @param ticks  how long the lunge is watched for; nothing fires if it connects with nothing
     */
    public static void onFirstContact(WeaponsPlugin plugin, Player caster, double radius, int ticks, Contact contact) {
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (!caster.isOnline() || elapsed >= ticks) {
                    cancel();
                    return;
                }
                for (Entity nearby : caster.getNearbyEntities(radius, radius, radius)) {
                    if (!(nearby instanceof LivingEntity victim) || victim.equals(caster) || victim.isDead()) {
                        continue;
                    }
                    cancel();
                    contact.hit(victim, victim.getLocation());
                    return;
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Runs {@code atRest} where the caster comes to a stop, whether or not the lunge hit anything.
     * <p>
     * The landing spot is the interesting one for anything a spear <em>plants</em> — a rod, a crystal, a
     * spike thicket. Waiting a fixed number of ticks rather than watching for {@code isOnGround} is
     * deliberate: a lunge that ends in the air (off a ledge, off a boss's knockback) still has to resolve,
     * and "wait until you land" is how an ability silently never fires.
     */
    public static void afterLunge(WeaponsPlugin plugin, Player caster, int ticks, Consumer<Location> atRest) {
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
