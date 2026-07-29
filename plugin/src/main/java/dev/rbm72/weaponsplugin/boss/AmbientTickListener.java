package dev.rbm72.weaponsplugin.boss;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Takes the hurt tilt off the vanilla environmental ticks a fight inflicts on purpose.
 * <p>
 * §0.1 says weaponise the real Minecraft object, so the roster does: real lava floods, real fire
 * trails, real powder snow, real drowning water. The cost of that is real vanilla damage ticks, and
 * every one of those rolls the client's camera. A player crossing an Inferno Warlord fire trail is on
 * fire for eight seconds and gets eight camera jolts out of it — the terrain mechanic reads as a
 * control-loss effect it was never meant to be.
 * <p>
 * The plugin's own damage-over-time already avoids this by going through {@link TickDamage}. This does
 * the same for the causes vanilla owns, and only inside a live arena: the damage is left exactly as
 * vanilla resolved it (armor, enchantments and Resistance are all in the number by the time this runs
 * at {@link EventPriority#HIGHEST}), the event is cancelled, and the health comes off directly.
 * Outside a fight, and for anything that could kill, the ordinary pipeline is untouched.
 * <p>
 * Deliberately excluded: {@code FALL} belongs to {@link ArenaSafetyListener}, and every impact cause —
 * explosions, projectiles, melee, {@code MAGIC} — keeps its tilt, because a hit is supposed to be felt.
 */
public final class AmbientTickListener implements Listener {

    /**
     * The recurring environmental costs. All of them tick on a vanilla timer while a state persists,
     * which is exactly the profile that turns a hurt tilt into a twitch.
     */
    private static final Set<EntityDamageEvent.DamageCause> AMBIENT = EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.FREEZE,
            EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.POISON,
            EntityDamageEvent.DamageCause.WITHER);

    private final BossManager bossManager;

    public AmbientTickListener(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    /**
     * True for the recurring environmental causes whose tilt this class strips. Exposed because the
     * same set defines "a tick, not a hit" for anything else that wants to take the flinch off one —
     * the Steadfast Anchor carries this treatment outside the arena for its wearer.
     */
    public static boolean isAmbient(EntityDamageEvent.DamageCause cause) {
        return AMBIENT.contains(cause);
    }

    /**
     * Runs at {@link EventPriority#HIGHEST} rather than {@code MONITOR} so the reduction every other
     * listener applied is already in {@code getFinalDamage()}, while cancelling is still legal.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAmbientTick(EntityDamageEvent event) {
        if (!isAmbient(event.getCause())) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (bossManager.instanceCovering(player.getLocation()).isEmpty()) {
            return;
        }
        if (TickDamage.applyResolved(player, event.getFinalDamage())) {
            event.setCancelled(true);
        }
    }
}
