package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Wing-membrane sub-health: batch-2 §4.4's "shoot them — this is what eventually grounds it for P4",
 * tracked entirely separately from the boss's real health bar.
 * <p>
 * This is deliberately <em>not</em> a redirect of real damage — an arrow still deals its full, normal
 * hit to the boss's actual health through the ordinary {@code BossDamageListener} pipeline, because
 * ranged is this fight's mandatory baseline damage source and nothing here should make an arrow hit
 * for less than a melee one. The membrane pool is a second, parallel pressure valve keyed off the same
 * hits: every arrow that lands while the boss is airborne also chips this pool, and once it empties the
 * boss is forced into a bonus grounded window on top of whatever P2's perch-denial or P3's HP threshold
 * would otherwise have earned the group. Left alone, the pool slowly regenerates — "it stays aerial
 * longer if untouched, so the fight drags" is expressed by that regen, not by the pool doing nothing.
 * <p>
 * Fight-scoped ("all fight" per the mechanics table) rather than owned by one phase, so pressure kept
 * up in P1 is not wasted the moment P2 starts.
 */
final class WingMembranes {

    private final DragonFight fight;

    private double hp;
    private double maxHp;
    private long lastHitMs;
    private boolean shreddedSignal;
    private Handler handler;

    WingMembranes(DragonFight fight) {
        this.fight = fight;
    }

    void arm() {
        maxHp = fight.config().dbl("membrane-max-hp-per-player", 55.0) * fight.playerCount();
        hp = maxHp;
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    double fraction() {
        return maxHp <= 0.0 ? 1.0 : Math.max(0.0, Math.min(1.0, hp / maxHp));
    }

    /**
     * True exactly once per depletion — the caller (the active phase mechanic) consumes it to trigger a
     * bonus grounded window, and the flag clears itself so a phase mechanic polling every pulse cannot
     * fire the window twice off one empty reading.
     */
    boolean consumeShreddedSignal() {
        if (shreddedSignal) {
            shreddedSignal = false;
            return true;
        }
        return false;
    }

    void pulse(int intervalTicks) {
        if (hp >= maxHp) {
            return;
        }
        long now = System.currentTimeMillis();
        long delayMs = (long) (fight.config().dbl("membrane-regen-delay-seconds", 4.0) * 1000);
        if (now - lastHitMs < delayMs) {
            return;
        }
        double regenPerSecond = fight.config().dbl("membrane-regen-per-second", 2.5) * fight.playerCount();
        hp = Math.min(maxHp, hp + regenPerSecond * (intervalTicks / 20.0));
    }

    void discard() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
    }

    private void onArrowHit(Location at, double damage) {
        if (fight.aerial().state() == AerialRig.AerialState.GROUNDED) {
            // The wings aren't the point once it's already down on the ground.
            return;
        }
        lastHitMs = System.currentTimeMillis();
        if (hp <= 0.0) {
            return;
        }
        hp = Math.max(0.0, hp - damage);
        Fx.coloredBurst(at, DragonFight.EMBER, 1.0f, 8, 0.4);
        Fx.burst(at, Particle.CLOUD, 4, 0.3);
        if (hp <= 0.0) {
            shreddedSignal = true;
            Fx.coloredBurst(at, DragonFight.DRAGON_RED, 2.2f, 40, 0.8);
            Fx.sound(at, Sound.ENTITY_PHANTOM_HURT, 1.4f, 0.6f);
        }
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDamaged(EntityDamageByEntityEvent event) {
            if (!event.getEntity().equals(fight.instance().entity())) {
                return;
            }
            if (!(event.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player)) {
                return;
            }
            onArrowHit(event.getEntity().getLocation().add(0, 1.4, 0), event.getFinalDamage());
        }
    }
}
