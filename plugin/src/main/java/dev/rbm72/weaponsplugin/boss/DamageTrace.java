package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.util.Grounded;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
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
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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

    /**
     * The "what is moving me" probe, and the counterpart to the damage lines above.
     * <p>
     * "It halts my momentum / I'm stuck for a second" is not a damage question at all, and no amount of
     * health logging answers it: a shove is a velocity packet, and it arrives by one of three routes that
     * a player cannot tell apart by feel — vanilla knockback from a hit, an attack writing
     * {@code setVelocity} directly, or an explosion. This prints whichever one landed and, crucially,
     * whether something cancelled it, so "my anti-knockback accessory isn't working" becomes a fact
     * rather than an impression. MONITOR with {@code ignoreCancelled = false} for exactly that reason.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVelocity(PlayerVelocityEvent event) {
        State state = traced.get(event.getPlayer().getUniqueId());
        if (state == null) {
            return;
        }
        Vector velocity = event.getVelocity();
        String line = "t" + state.tick + "  SHOVE  velocity=" + format(velocity)
                + "  mag=" + String.format("%.2f", velocity.length())
                + "  from=" + blame()
                + (event.isCancelled() ? "  cancelled" : "  APPLIED");
        emit(event.getPlayer(), line, event.isCancelled() ? NamedTextColor.GRAY : NamedTextColor.GOLD);
    }

    /**
     * Which of this plugin's classes caused the velocity change, or {@code vanilla} if none did.
     * <p>
     * Knowing a shove landed is only half an answer — "what is moving me" needs a name, and a shove can
     * come from a boss attack, a phase mechanic, an equipped stone or the server itself, which feel
     * identical in play and are indistinguishable in a log. The event fires synchronously inside whatever
     * called {@code setVelocity}, so that caller is still on the stack: the first plugin frame below this
     * listener is the culprit, and if there is no plugin frame at all the push came from vanilla (an
     * explosion, a wind charge, a piston) and no amount of plugin-side work will stop it.
     */
    private static String blame() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            if (!className.startsWith("dev.rbm72.weaponsplugin") || className.equals(DamageTrace.class.getName())) {
                continue;
            }
            int lastDot = className.lastIndexOf('.');
            return className.substring(lastDot + 1) + "." + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "vanilla";
    }

    /**
     * Vanilla's own knockback, logged separately from the velocity packet it would produce — a cancelled
     * knockback that is nonetheless followed by an {@code APPLIED} shove line means the push came from
     * somewhere other than the hit, which is the distinction worth an extra line.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        State state = traced.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        String line = "t" + state.tick + "  KNOCKBACK  cause=" + event.getCause()
                + "  " + format(event.getKnockback())
                + (event.isCancelled() ? "  cancelled" : "  APPLIED");
        emit(player, line, event.isCancelled() ? NamedTextColor.GRAY : NamedTextColor.GOLD);
    }

    private static String format(Vector vector) {
        return String.format("(%.2f, %.2f, %.2f)", vector.getX(), vector.getY(), vector.getZ());
    }

    private void emit(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
        plugin.getLogger().info("[damage-trace] " + player.getName() + "  " + text);
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
        line.append("  ground=").append(Grounded.onGround(player));
        // The two numbers behind "why did I stop moving": what the player's velocity actually is right
        // now, and whether the knockback immunity an accessory claims to grant is really on the player.
        line.append("  vel=").append(String.format("%.2f", player.getVelocity().length()));
        var knockbackResistance = player.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null) {
            line.append("  kbres=").append(String.format("%.2f", knockbackResistance.getValue()));
        }
        emit(player, line.toString(), NamedTextColor.AQUA);
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
        emit(player, line.toString(), tilted ? NamedTextColor.RED : NamedTextColor.GRAY);
    }
}
