package dev.rbm72.weaponsplugin.boss;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Enforces the roster-wide rule that <b>nothing in a boss fight kills by falling</b>.
 * <p>
 * Half the encounter designs remove the floor — rifts, craters, collapsing ice, tunnel mouths, piston
 * walls. Every one of those is meant to cost the player a chunk of health and a few seconds of
 * repositioning, not to end their run because gravity finished the job. A design that reads "the
 * floor gives out" has to be a <em>punishing</em> mechanic, never a removing one, or players stop
 * engaging with the arena at all and fight from whatever ledge feels safest — which is precisely the
 * camping behaviour the terrain mechanics exist to break.
 * <p>
 * So fall damage is cancelled outright for anyone standing in a live fight, and
 * {@link BossInstance#enforcePitFloor()} applies the real punishment instead: one heavy hit, then a
 * lift back onto solid ground.
 */
public final class ArenaSafetyListener implements Listener {

    private final BossManager bossManager;

    public ArenaSafetyListener(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (bossManager.instanceCovering(player.getLocation()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
    }
}
