package dev.rbm72.weaponsplugin.boss;

import org.bukkit.Sound;
import org.bukkit.entity.Entity;
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
            Player attacker = playerBehind(event);
            if (attacker == null) {
                event.setCancelled(true);
                return;
            }
            double multiplier = instance.damageMultiplier();
            double damage = multiplier != 1.0 ? event.getDamage() * multiplier : event.getDamage();
            // The active phase mechanic's per-attacker rule, between the global multiplier and the
            // floor. A duel phase rejects everyone but its chosen opponent here; a positional phase
            // scales by where this particular player is standing. The global multiplier above cannot
            // express either, since it knows nothing about who is swinging.
            damage = instance.filterMechanicDamage(attacker, damage);
            // Hard floor: no amount of raw damage skips past this phase's mandatory mechanic —
            // see BossInstance#clampToPhaseFloor. Applied last, after every other reduction/bonus.
            damage = instance.clampToPhaseFloor(damage);
            event.setDamage(damage);

            // Let the active mechanic see the resolved hit — a reaction mechanic needs the hit itself,
            // not just the clock, and a pacing one needs to know how fast damage is arriving.
            instance.notifyGateDamaged(attacker, damage);

            // Blocked entirely (armored while weak points stand, or a trial has it invulnerable): give
            // the attacker a clear "shielded" deflect cue so a zero-damage hit reads as intentional, not
            // broken — the mechanic is telling them to break the weak points, not that combat is bugged.
            if (damage <= 0.0 && instance.signalDeflectReady()) {
                instance.entity().getWorld().playSound(
                        instance.entity().getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.7f);
            }
        });
    }

    /**
     * Applies the attacking boss's {@link Boss#outgoingDamageMultiplier()} to every hit it lands on a
     * player. Centralised here rather than in the 100-odd attack classes: they all deal their damage
     * as {@code victim.damage(amount, ctx.boss())}, so every one of those hits — melee, AoE pulse, or
     * boss-fired projectile — passes through this single event with the boss as the damager.
     *
     * <p>HIGH, not MONITOR: this is a source-side scalar on what the boss swings for, so it has to
     * land before the player's own armor/resistance/shield reductions get their say, exactly as a
     * bigger base hit would. MONITOR would apply it last and multiply the already-mitigated number,
     * which would blow straight through defenses that are supposed to work.
     */
    /**
     * The player responsible for this hit, unwrapping a projectile to its shooter, or null if the
     * damage did not come from a player at all (fire, fall, drowning, another mob, the void) — those
     * are cancelled outright so a boss can never be cheesed into a hazard or lost to an accident.
     */
    private static Player playerBehind(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        bossManager.instanceFor(damager.getUniqueId()).ifPresent(instance -> {
            double multiplier = instance.boss().outgoingDamageMultiplier();
            if (multiplier != 1.0) {
                event.setDamage(event.getDamage() * multiplier);
            }
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
