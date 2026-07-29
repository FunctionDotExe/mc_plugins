package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * The constant running under all eight phases (batch-5 §2): the Worldender is blind, a real Warden, and
 * tracks vibration for the whole fight. It attacks the last thing it heard. Standing still and sneaking
 * makes a player quiet; fighting, sprinting into thin ice, planting a rod or pouring a bucket makes them
 * loud — every phase-specific "loud" act registers through {@link #register} rather than inventing its
 * own targeting rule.
 * <p>
 * Deliberately its own copy of the Hollow Choir's {@code Noise} class rather than a shared reference to
 * it: that class is package-private inside {@code choir}, and promoting it to a shared package is a
 * design call for a later pass (see batch-5 §9) rather than something to do silently as a side effect of
 * building the capstone. The two are structurally close on purpose — this is the one place in the
 * roster where duplicating a working pattern is the safer move.
 * <p>
 * <b>The one deliberate difference from the Choir's model:</b> {@link #hunted()} never points the boss
 * at a sneaking player unless every combatant present is sneaking. The Choir has no such rule because it
 * has no sneak-driven counterplay of its own; the Worldender's whole thesis is "sneaking makes you
 * invisible to it", so the targeting rule has to actually honour that rather than merely track loudness.
 */
final class Vibration {

    /** One heard sound. {@code source} is null for noise a block/prop made rather than a body. */
    record Event(Location at, UUID source, double loudness, int tick) {
    }

    private static final int HISTORY = 24;

    private final WorldenderFight fight;
    private final Deque<Event> recent = new ArrayDeque<>();

    private Handler handler;
    private int tick;

    Vibration(WorldenderFight fight) {
        this.fight = fight;
    }

    void arm() {
        if (handler != null) {
            return;
        }
        handler = new Handler();
        fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
    }

    void pulse(int intervalTicks) {
        tick += intervalTicks;
        int memory = fight.config().num("vibration-memory-ticks", 120);
        recent.removeIf(event -> tick - event.tick() > memory);
    }

    /** Registers a sound and shows it — legibility requirement carried over from the Choir's model. */
    void register(Location at, Player source, double loudness) {
        if (at == null) {
            return;
        }
        recent.addLast(new Event(at.clone(), source == null ? null : source.getUniqueId(), loudness, tick));
        while (recent.size() > HISTORY) {
            recent.removeFirst();
        }
        Fx.coloredBurst(at.clone().add(0, 1, 0), WorldenderFight.SCULK_TEAL, 1.2f, (int) (6 * loudness), 0.3);
        Fx.burst(at.clone().add(0, 1, 0), Particle.SCULK_SOUL, 3, 0.3);
    }

    /** Public hook for a phase's own loud act — planting a rod, pouring a bucket, snapping through ice. */
    void registerLoud(Player source, double loudness) {
        register(source.getLocation(), source, loudness);
    }

    Event lastHeard() {
        return recent.isEmpty() ? null : recent.peekLast();
    }

    Location lastHeardAt() {
        Event event = lastHeard();
        return event == null ? null : event.at();
    }

    /**
     * The player the Worldender is currently hunting: whoever is nearest the last sound it heard, among
     * everyone <em>not</em> currently sneaking — sneaking is what "invisible to it" means for the whole
     * fight. Falls back to the nearest sneaking player only if literally nobody present is standing.
     */
    Player hunted() {
        Location at = lastHeardAt();
        if (at == null) {
            return null;
        }
        Player nearestStanding = null;
        double bestStanding = Double.MAX_VALUE;
        Player nearestAny = null;
        double bestAny = Double.MAX_VALUE;
        for (Player player : fight.combatants()) {
            if (player.getWorld() != at.getWorld()) {
                continue;
            }
            double distance = player.getLocation().distance(at);
            if (distance < bestAny) {
                bestAny = distance;
                nearestAny = player;
            }
            if (!player.isSneaking() && distance < bestStanding) {
                bestStanding = distance;
                nearestStanding = player;
            }
        }
        return nearestStanding != null ? nearestStanding : nearestAny;
    }

    /** True while {@code player} is not the one currently being hunted — the "someone else is louder" state. */
    boolean isSafeFor(Player player) {
        Player target = hunted();
        return target == null || !target.equals(player);
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        recent.clear();
    }

    private final class Handler implements Listener {

        /** Hitting it is loud — tanking this boss is self-defeating by construction, same as the Choir. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent event) {
            Player attacker = attackerOf(event);
            if (attacker == null) {
                return;
            }
            if (event.getEntity().equals(fight.instance().entity())) {
                register(attacker.getLocation(), attacker, fight.config().dbl("vibration-melee", 1.0));
            }
        }

        /** Range buys distance, not silence — P1's whole thesis. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onShoot(ProjectileLaunchEvent event) {
            if (!(event.getEntity().getShooter() instanceof Player shooter)) {
                return;
            }
            if (!fight.combatants().contains(shooter)) {
                return;
            }
            register(shooter.getLocation(), shooter, fight.config().dbl("vibration-ranged", 0.8));
        }

        private Player attackerOf(EntityDamageByEntityEvent event) {
            if (event.getDamager() instanceof Player player) {
                return player;
            }
            if (event.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
            if (event.getDamager() instanceof LivingEntity) {
                return null;
            }
            return null;
        }
    }
}
