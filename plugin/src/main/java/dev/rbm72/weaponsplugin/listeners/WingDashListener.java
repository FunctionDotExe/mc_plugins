package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.sets.WyrmwingPlate;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wyrmwing Plate's full-set bonus hijacks the vanilla "double-tap space to fly" gesture
 * ({@link WyrmwingPlate#onTick} arms it by setting allowFlight while the full set is worn):
 * instead of entering creative-style flight, this cancels that and launches the player in a
 * single wing-dash burst, gated by a cooldown so it reads as a controlled ability, not free flight.
 */
public final class WingDashListener implements Listener {

    private static final long COOLDOWN_MS = 6000;
    private static final double LAUNCH_SPEED = 1.5;
    private static final int GLIDE_TICKS = 30;

    private final ArmorManager armor;
    private final WyrmwingPlate wyrmwingPlate;
    private final Map<UUID, Long> nextDashReadyMs = new HashMap<>();

    public WingDashListener(ArmorManager armor, WyrmwingPlate wyrmwingPlate) {
        this.armor = armor;
        this.wyrmwingPlate = wyrmwingPlate;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!armor.fullSet(player, wyrmwingPlate)) {
            return;
        }

        event.setCancelled(true);
        player.setAllowFlight(false);

        long now = System.currentTimeMillis();
        Long readyAt = nextDashReadyMs.get(player.getUniqueId());
        if (readyAt != null && now < readyAt) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.4f, 0.8f);
            return;
        }
        nextDashReadyMs.put(player.getUniqueId(), now + COOLDOWN_MS);

        Vector direction = player.getLocation().getDirection().normalize().multiply(LAUNCH_SPEED).setY(0.85);
        player.setVelocity(direction);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, GLIDE_TICKS, 0, false, true, true));

        Fx.sound(player, Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.2f);
        Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.4f);
        Fx.point(player.getLocation(), Particle.CLOUD, 20);
        Fx.coloredBurst(player.getLocation(), WyrmwingPlate.SCALE_COLOR, 1.6f, 30, 0.6);
    }
}
