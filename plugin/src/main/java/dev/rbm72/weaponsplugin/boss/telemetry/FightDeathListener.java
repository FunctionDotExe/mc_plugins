package dev.rbm72.weaponsplugin.boss.telemetry;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Records player deaths against whichever live fight owns the ground they died on.
 * <p>
 * Attributed by location rather than by damage source, because a boss fight kills people in ways the
 * boss is not the damager for — a pit fall, a lava floor the boss poured, a wither cloud left behind, an
 * add. All of those are the fight killing them, and a telemetry file that only counted direct boss hits
 * would report the safest-looking fights as the deadliest.
 */
public final class FightDeathListener implements Listener {

    private final WeaponsPlugin plugin;

    public FightDeathListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /** MONITOR: purely observational, and must not be the reason a death event goes wrong. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.bossManager().instanceCovering(player.getLocation()).ifPresent(instance ->
                plugin.fightLog().safely(() -> instance.telemetry().playerDied(player)));
    }
}
