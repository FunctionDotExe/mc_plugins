package dev.rbm72.weaponsplugin.boss;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Bosses only take damage from players — fire, fall, drowning, other mobs,
 * and the void are all cancelled, so a boss fight can't be cheesed by
 * shoving it into a hazard, and can't die to an unrelated environmental
 * accident. Death clears vanilla drops/XP in favor of the boss's own
 * {@link LootTable}.
 */
public final class BossDamageListener implements Listener {

    private final BossManager bossManager;

    public BossDamageListener(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    /**
     * MONITOR, not the default NORMAL — {@code WeaponDamageListener}'s per-weapon
     * {@code onMeleeDamage} hooks (flat "boss bonus damage" additions, crit multipliers, etc.) run at
     * MONITOR too, and priority always wins over registration order regardless of tier. At NORMAL,
     * this handler's armored/exposed multiplier was applied BEFORE those weapon bonuses landed, so a
     * big enough flat bonus (Apotheosis, Soulharvester) tacked straight back on afterward and blew
     * through the reduction entirely — the vulnerability mechanic visibly did nothing against a
     * strong enough weapon. Registered after {@code WeaponDamageListener} in
     * {@code WeaponsPlugin#onEnable}, so within the shared MONITOR tier this runs second and gets the
     * actual final say on the damage number.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        bossManager.instanceForDamaged(event.getEntity()).ifPresent(instance -> {
            boolean fromPlayer = event instanceof EntityDamageByEntityEvent byEntity
                    && (byEntity.getDamager() instanceof Player
                            || (byEntity.getDamager() instanceof Projectile projectile
                                    && projectile.getShooter() instanceof Player));
            if (!fromPlayer) {
                event.setCancelled(true);
                return;
            }
            double multiplier = instance.damageMultiplier();
            double damage = multiplier != 1.0 ? event.getDamage() * multiplier : event.getDamage();
            // Hard floor: no amount of raw damage skips past this phase's mandatory weak-point cycle —
            // see BossInstance#clampToPhaseFloor. Applied last, after every other reduction/bonus.
            damage = instance.clampToPhaseFloor(damage);
            event.setDamage(damage);
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        bossManager.instanceFor(event.getEntity().getUniqueId()).ifPresent(instance -> {
            event.getDrops().clear();
            event.setDroppedExp(0);
            instance.end(BossInstance.EndReason.DEFEATED);
        });
    }
}
