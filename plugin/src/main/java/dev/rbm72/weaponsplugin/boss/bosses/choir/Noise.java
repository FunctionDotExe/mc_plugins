package dev.rbm72.weaponsplugin.boss.bosses.choir;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The novel system of batch 3 (§6.1, item 13) and the thing the whole Hollow Choir depends on: a record
 * of <b>who made a sound, where, and how recently</b>, which is the only input its targeting has. The
 * Choir is blind. It attacks the last thing it heard.
 * <p>
 * <b>What counts as noise, and the one thing that deliberately does not.</b> Hitting the Choir is loud.
 * Firing a bow is loud — §4.6's genuinely novel anti-ranged answer is that range provides no anonymity.
 * Striking a note block or a bell is the loudest thing in the arena, which is what makes misdirection
 * work at all. <b>Sprinting is not noise</b>, and never will be: batch 3 §0's deconfliction note requires
 * that the Choir never punish sprinting, because the Plague Warden's sculk already owns "be quiet" and
 * these two must never blur into the same fight.
 * <p>
 * Legibility is the design constraint (§4.8): if players cannot tell what the boss heard, the fight is
 * unfair. So every registered event draws a visible pulse at its source, and {@link Instruments} echoes
 * the last-heard point continuously.
 */
final class Noise {

    /** One heard sound. {@code source} is null for noise a block made rather than a body. */
    record Event(Location at, UUID source, double loudness, int tick) {
    }

    private static final int HISTORY = 24;

    private final ChoirFight fight;
    private final Deque<Event> recent = new ArrayDeque<>();

    private Handler handler;
    private int tick;

    Noise(ChoirFight fight) {
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
        int memory = fight.config().num("noise-memory-ticks", 120);
        recent.removeIf(event -> tick - event.tick() > memory);
    }

    /** Registers a sound and shows it — the pulse is not decoration, it is how the rule stays readable. */
    void register(Location at, Player source, double loudness) {
        if (at == null) {
            return;
        }
        recent.addLast(new Event(at.clone(), source == null ? null : source.getUniqueId(), loudness, tick));
        while (recent.size() > HISTORY) {
            recent.removeFirst();
        }
        Fx.coloredBurst(at.clone().add(0, 1, 0), ChoirFight.PALE_VIOLET, 1.2f, (int) (6 * loudness), 0.3);
        Fx.burst(at.clone().add(0, 1, 0), Particle.NOTE, 3, 0.3);
    }

    /** The last thing it heard, or null in genuine silence — which is a real and useful state for players. */
    Event lastHeard() {
        return recent.isEmpty() ? null : recent.peekLast();
    }

    Location lastHeardAt() {
        Event event = lastHeard();
        return event == null ? null : event.at();
    }

    /**
     * Whoever has been loudest inside the memory window — the Vex swarm's target (§4.4). Summed rather
     * than "most recent" so a player quietly plinking a note block does not out-shout the one actually
     * hitting the boss.
     */
    Player loudest() {
        Map<UUID, Double> totals = new HashMap<>();
        for (Event event : recent) {
            if (event.source() == null) {
                continue;
            }
            totals.merge(event.source(), event.loudness(), Double::sum);
        }
        Player best = null;
        double bestScore = 0;
        for (Player player : fight.combatants()) {
            double score = totals.getOrDefault(player.getUniqueId(), 0.0);
            if (score > bestScore) {
                bestScore = score;
                best = player;
            }
        }
        return best;
    }

    /**
     * The player the Choir is currently hunting: whoever is nearest the last sound it heard. A note block
     * struck across the arena therefore pulls it away from the people hitting it, which is the entire
     * misdirection loop expressed in one method.
     */
    Player hunted() {
        Location at = lastHeardAt();
        if (at == null) {
            return null;
        }
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : fight.combatants()) {
            if (player.getWorld() != at.getWorld()) {
                continue;
            }
            double distance = player.getLocation().distance(at);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /**
     * True when the last sound was made somewhere nobody is standing — a successful misdirection, and
     * P1's exit condition counts these.
     */
    boolean lastHeardIsEmpty() {
        Location at = lastHeardAt();
        if (at == null) {
            return false;
        }
        double clear = fight.config().dbl("misdirect-clear-radius", 6.0);
        for (Player player : fight.combatants()) {
            if (player.getWorld() == at.getWorld() && player.getLocation().distance(at) <= clear) {
                return false;
            }
        }
        return true;
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        recent.clear();
    }

    private final class Handler implements Listener {

        /** Hitting it is loud. Tanking this boss is therefore self-defeating by construction (§4.6). */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent event) {
            Player attacker = attackerOf(event);
            if (attacker == null) {
                return;
            }
            if (event.getEntity().equals(fight.instance().entity())) {
                register(attacker.getLocation(), attacker, fight.config().dbl("noise-melee", 1.0));
            }
        }

        /** Range buys distance, not silence. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onShoot(ProjectileLaunchEvent event) {
            if (!(event.getEntity().getShooter() instanceof Player shooter)) {
                return;
            }
            if (!fight.combatants().contains(shooter)) {
                return;
            }
            register(shooter.getLocation(), shooter, fight.config().dbl("noise-ranged", 0.8));
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
