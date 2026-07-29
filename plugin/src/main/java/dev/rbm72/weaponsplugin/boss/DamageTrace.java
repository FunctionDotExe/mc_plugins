package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * "What is ticking me?" — a per-player damage tracer for diagnosing a fight, toggled with
 * {@code /bossdamagetrace}.
 * <p>
 * A plain {@link EntityDamageEvent} log is not enough here, because this plugin takes health two
 * different ways on purpose and only one of them is an event:
 * <ul>
 *   <li>A <b>hit</b> goes through the vanilla pipeline, fires the event, and rolls the client's camera.</li>
 *   <li>A <b>tick</b> goes through {@link TickDamage}, which writes health directly precisely so it
 *       <em>does not</em> tilt the camera. It fires no event at all and is invisible to any listener.</li>
 * </ul>
 * So this watches both: a MONITOR listener records the events, and a 1-tick task diffs health and
 * absorption to catch the direct writes. Each reported line says which path the damage took, and
 * therefore whether it could have tilted your screen:
 * <pre>
 *   -3.0 HP  TILT    cause=ENTITY_ATTACK  by=Worldender      &lt;- vanilla hit, camera rolled
 *   -3.0 HP  silent  cause=FIRE_TICK      cancelled          &lt;- event fired, we cancelled it, health taken by us
 *   -3.0 HP  silent  direct health write (TickDamage)        &lt;- no event at all
 * </pre>
 * A run of {@code TILT} lines a few ticks apart is the bug to chase; a run of {@code silent} lines is
 * the engine working as designed. Lines go to the traced player and to the console, so a fight can be
 * traced by one person and read back out of the log.
 */
public final class DamageTrace implements Listener {

    /** Health/absorption changes smaller than this are float noise, not damage. */
    private static final double EPSILON = 0.01;
    /**
     * How often to print the state line — the answer to "why can't I jump/move", which no damage log
     * can give you. A held player is usually held by something that never touches health at all: a
     * potion effect, freeze ticks, or a velocity someone overwrites every tick.
     */
    private static final int STATE_INTERVAL_TICKS = 20;

    private final WeaponsPlugin plugin;
    private final Map<UUID, State> traced = new HashMap<>();
    private BukkitTask task;

    public DamageTrace(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /** One traced player's last-known pools, plus whatever event fired since the last sample. */
    private static final class State {
        double lastHealth;
        double lastAbsorption;
        String cause;
        String damager;
        boolean cancelled;
        int events;
        int tick;

        State(Player player) {
            this.lastHealth = player.getHealth();
            this.lastAbsorption = player.getAbsorptionAmount();
        }

        void clearEvent() {
            cause = null;
            damager = null;
            cancelled = false;
            events = 0;
        }
    }

    /** @return true if tracing is now on for this player, false if it was just turned off. */
    public boolean toggle(Player player) {
        if (traced.remove(player.getUniqueId()) != null) {
            stopIfIdle();
            return false;
        }
        traced.put(player.getUniqueId(), new State(player));
        start();
        return true;
    }

    public boolean isTracing(Player player) {
        return traced.containsKey(player.getUniqueId());
    }

    /**
     * Runs at {@link EventPriority#MONITOR} so {@code cancelled} reflects the final verdict of every
     * other listener — that flag is the whole point, since a cancelled damage event is one that never
     * reached the client and so never tilted anything.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        State state = traced.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.events++;
        state.cause = event.getCause().name();
        state.cancelled = event.isCancelled();
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity source = byEntity.getDamager();
            state.damager = source.getName() + "/" + source.getType();
        }
    }

    private void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 1L, 1L);
    }

    private void stopIfIdle() {
        if (!traced.isEmpty() || task == null) {
            return;
        }
        task.cancel();
        task = null;
    }

    /**
     * One sample. Health is compared against the previous tick rather than trusting the event's damage
     * number: absorption, Resistance and armor all sit between "the event said 8" and "you actually
     * lost 3", and the health delta is the number the player is complaining about.
     */
    private void sample() {
        for (Iterator<Map.Entry<UUID, State>> it = traced.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, State> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }
            State state = entry.getValue();
            state.tick++;
            double health = player.getHealth();
            double absorption = player.getAbsorptionAmount();
            double lost = (state.lastHealth + state.lastAbsorption) - (health + absorption);
            state.lastHealth = health;
            state.lastAbsorption = absorption;

            if (state.tick % STATE_INTERVAL_TICKS == 0) {
                reportState(player, state);
            }

            // An uncancelled event with no health change still tilted the camera (absorption soaked it,
            // or a listener zeroed the damage), so it is worth a line too.
            boolean tilted = state.events > 0 && !state.cancelled;
            if (lost <= EPSILON && !tilted) {
                state.clearEvent();
                continue;
            }
            report(player, state, lost, tilted);
            state.clearEvent();
        }
        stopIfIdle();
    }

    /**
     * The "why can't I move" line. Everything here can hold a player without ever touching their health,
     * which is why a damage log alone kept coming up empty: a Slowness the fight refreshes every pulse, a
     * root (Slowness at a huge amplifier), freeze ticks from powder snow, burning from an attack that has
     * long since finished, or a block you are standing inside. Printed once a second while tracing.
     */
    private void reportState(Player player, State state) {
        StringBuilder line = new StringBuilder("t").append(state.tick).append("  state:");
        line.append("  hp=").append(String.format("%.1f", player.getHealth()));
        if (player.getAbsorptionAmount() > 0) {
            line.append("  absorb=").append(String.format("%.1f", player.getAbsorptionAmount()));
        }
        if (player.getFireTicks() > 0) {
            line.append("  BURNING(").append(player.getFireTicks()).append("t)");
        }
        if (player.getFreezeTicks() > 0) {
            line.append("  freeze=").append(player.getFreezeTicks());
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            line.append("  ").append(effect.getType().getKey().getKey())
                    .append(':').append(effect.getAmplifier())
                    .append('(').append(effect.getDuration()).append("t)");
        }
        line.append("  feet=").append(player.getLocation().getBlock().getType());
        line.append("  ground=").append(player.isOnGround());
        String text = line.toString();
        player.sendMessage(Component.text(text, NamedTextColor.AQUA));
        plugin.getLogger().info("[damage-trace] " + player.getName() + "  " + text);
    }

    private void report(Player player, State state, double lost, boolean tilted) {
        StringBuilder line = new StringBuilder();
        line.append('t').append(state.tick).append("  ")
                .append(String.format("%.2f", lost)).append(" HP  ")
                .append(tilted ? "TILT" : "silent");
        if (state.events > 0) {
            line.append("  cause=").append(state.cause);
            if (state.events > 1) {
                line.append(" (x").append(state.events).append(')');
            }
            if (state.damager != null) {
                line.append("  by=").append(state.damager);
            }
            if (state.cancelled) {
                line.append("  cancelled");
            }
        } else {
            line.append("  direct health write (TickDamage)");
        }
        String text = line.toString();
        player.sendMessage(Component.text(text, tilted ? NamedTextColor.RED : NamedTextColor.GRAY));
        plugin.getLogger().info("[damage-trace] " + player.getName() + "  " + text);
    }
}
