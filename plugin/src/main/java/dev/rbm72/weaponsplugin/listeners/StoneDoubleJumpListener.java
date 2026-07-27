package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.stone.StoneManager;
import dev.rbm72.weaponsplugin.stone.stones.SkyleapStone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * Answers the vanilla double-tap-space gesture {@link SkyleapStone#onEquipTick} arms via
 * {@code allowFlight} (mirrors {@code WingDashListener}'s hijack of the same gesture for
 * Wyrmwing Plate). Always cancels the event — this stone never grants real flight — and only
 * applies the boost if a jump is actually available (i.e. not already used this airtime).
 */
public final class StoneDoubleJumpListener implements Listener {

    private final StoneManager stones;
    private final SkyleapStone skyleapStone;

    public StoneDoubleJumpListener(StoneManager stones, SkyleapStone skyleapStone) {
        this.stones = stones;
        this.skyleapStone = skyleapStone;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) {
            return;
        }
        Player player = event.getPlayer();
        if (!stones.hasEquipped(player, SkyleapStone.class)) {
            return;
        }

        event.setCancelled(true);
        player.setAllowFlight(false);

        if (skyleapStone.consumeJump(player)) {
            skyleapStone.applyBoost(player);
        }
    }
}
