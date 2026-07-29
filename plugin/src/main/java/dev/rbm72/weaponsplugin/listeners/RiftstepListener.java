package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.stone.StoneManager;
import dev.rbm72.weaponsplugin.stone.stones.RiftstepStone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Waives the 5 fall damage vanilla charges an ender-pearl thrower, for pearls that came from
 * {@link RiftstepStone}.
 * <p>
 * It has to be done in two halves because the damage arrives as an ordinary {@code FALL} hit with nothing on
 * it identifying the pearl: the teleport is the only event that names the cause, so a pearl arrival is
 * recorded there and the very next fall hit for that player is cancelled. The window is
 * {@link #WAIVER_WINDOW_MS} — vanilla applies the damage in the same tick as the teleport, so anything longer
 * would start eating real fall damage from a genuine drop moments later, and anything based on "the player
 * has the stone equipped" alone would waive fall damage permanently.
 * <p>
 * Gated on the stone still being equipped, so unsocketing it mid-flight lands you the vanilla way.
 */
public final class RiftstepListener implements Listener {

    private static final long WAIVER_WINDOW_MS = 150;

    private final StoneManager stones;
    private final Map<UUID, Long> pearlArrivalMs = new HashMap<>();

    public RiftstepListener(StoneManager stones) {
        this.stones = stones;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (!stones.hasEquipped(player, RiftstepStone.class)) {
            return;
        }
        pearlArrivalMs.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long arrivedAt = pearlArrivalMs.remove(player.getUniqueId());
        if (arrivedAt != null && System.currentTimeMillis() - arrivedAt <= WAIVER_WINDOW_MS) {
            event.setCancelled(true);
        }
    }

    /**
     * A pearl that lands somewhere flat never produces the fall hit that would consume its marker, so without
     * this the map keeps one stale entry per player who ever used the stone.
     */
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        pearlArrivalMs.remove(event.getPlayer().getUniqueId());
    }
}
