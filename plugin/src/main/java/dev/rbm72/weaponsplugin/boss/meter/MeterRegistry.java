package dev.rbm72.weaponsplugin.boss.meter;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One fight's meters: attaches them, drives all of them from a single task, owns their readout, owns
 * the one healing hook the framework has, and guarantees that when the fight ends every player it
 * touched is back to normal.
 * <p>
 * A boss or a phase attaches a skin with {@link #attach(MeterSpec)} and keeps the returned
 * {@link PlayerMeter}; anything else in the fight can find it again by id with {@link #meter(String)},
 * so an attack does not need a reference threaded through it to spike a player's Chill.
 * <p>
 * Four structural decisions:
 * <ul>
 *   <li><b>One task for every meter, not one each.</b> The pulse is where contagion is computed, and
 *       contagion has to read every player's value at a single consistent instant. Two meters on
 *       independent timers would also mean two independent block-scan passes over the same players.</li>
 *   <li><b>Its own {@link MechanicBar} instance, separate from {@link BossInstance#mechanicBar()}.</b>
 *       That bar is claimed exclusively by whichever of the phase mechanic or a running event outranks
 *       the other, and {@link BossInstance} clears it outright on every phase change. A meter runs for
 *       the whole fight and must be visible at all times (it is the thing quietly killing you), so
 *       sharing that bar would mean the meter vanishing for the entire duration of every gate and
 *       every event — precisely the contention {@code MechanicBar}'s own header describes, one layer
 *       up. This is not a new UI channel: it is a second instance of the existing one, which is
 *       exactly what its per-player, claimable design already supports.</li>
 *   <li><b>But that instance is not unconditionally used.</b> Owning a second bar is correct and still
 *       produces three tiled boss bars — health, mechanic, meter — and at three the bar names start
 *       running into the bar above them, which costs the readability of all three rather than only the
 *       new one. So the meter picks a channel per player per pulse: its own bar while there is room in
 *       the stack, and the merged action-bar line (via {@link ActionBarHub}, as a registered
 *       {@link ActionBarHub.Source}, not a flash) once there is not. See {@link Channel} for why the
 *       alternatives were not taken.</li>
 *   <li><b>Teardown is unconditional and idempotent.</b> {@link #detachAll()} runs from
 *       {@link BossInstance#end} before anything else, so a player who was frozen solid at the instant
 *       the boss died is released first and the rest of the teardown cannot skip it by throwing. It is
 *       also the only thing standing between a fight and a leaked event listener or a leaked action-bar
 *       source, both of which would outlive their fight silently.</li>
 * </ul>
 */
public final class MeterRegistry {

    private static final Component SEPARATOR = Component.text("  ·  ", NamedTextColor.DARK_GRAY);

    /**
     * Where a player's meter readout is drawn, from {@code bosses.<boss>.meter-channel}.
     * <p>
     * The problem being solved is stacking, not content: a fight already shows a health bar and a
     * mechanic bar, and the meter's own bar makes three. Three of the alternatives were weighed and
     * rejected —
     * <ul>
     *   <li>merging back into {@link BossInstance#mechanicBar()} reintroduces exactly the contention
     *       that bar's header documents, and loses the meter for the whole of every gate and event;</li>
     *   <li>compacting the readout shortens a bar without removing one, and the collision is between a
     *       bar's name and the bar above it — a shorter name still collides;</li>
     *   <li>living on the action bar unconditionally puts a fight-long display into a channel where a
     *       parry notice or a cast bar takes the whole line, so the one display that must never vanish
     *       would be the one most often hidden.</li>
     * </ul>
     * {@link #AUTO} takes the bar whenever the stack has room, sheds it only when the stack would
     * otherwise be unreadable, and takes it back unconditionally once the meter is past its warn line —
     * so the good channel is used in the common case and the meter is never coverable at the moment it
     * matters. {@link #BAR} and {@link #ACTION_BAR} exist because "how many bars is too many" is a
     * judgement about a server's other plugins, which this code cannot see.
     */
    public enum Channel {
        /** Always its own boss bar, whatever else the player is already looking at. */
        BAR,
        /** Always the merged action-bar line, leaving the bar stack alone entirely. */
        ACTION_BAR,
        /** A boss bar while the stack has room for one, the action bar once it does not. */
        AUTO;

        static Channel parse(String raw) {
            if (raw == null) {
                return AUTO;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "bar", "bossbar", "boss-bar" -> BAR;
                case "action", "actionbar", "action-bar" -> ACTION_BAR;
                default -> AUTO;
            };
        }
    }

    private final BossInstance instance;
    private final long intervalTicks;
    private final boolean enabled;
    private final Channel channel;
    private final int barStackLimit;
    private final MechanicBar bar = new MechanicBar();
    private final MeterAfflictions afflictions;
    private final List<PlayerMeter> meters = new ArrayList<>();

    /**
     * This pulse's action-bar line per player, for the players currently on that channel. Built once
     * per pulse and only read by {@link #actionBarSegments}, which {@link ActionBarHub} calls for every
     * online player several times a second — that path has to be a map lookup and nothing more.
     */
    private final Map<UUID, Component> actionBarLines = new HashMap<>();
    /** Stored rather than created per call so {@link ActionBarHub#unregister} can find it again. */
    private final ActionBarHub.Source actionBarSource = this::actionBarSegments;
    private boolean actionBarSourceRegistered;

    /** One entry per attached meter that opted into healing conversion. Empty for almost every fight. */
    private final List<HealingRule> healingRules = new ArrayList<>();
    private Listener healingListener;

    private BukkitTask task;
    private boolean shutDown;

    public MeterRegistry(BossInstance instance) {
        this.instance = instance;
        this.intervalTicks = Math.max(1, instance.boss().meterTickInterval());
        this.enabled = instance.boss().metersEnabled();
        this.channel = Channel.parse(instance.boss().meterChannel());
        this.barStackLimit = Math.max(1, instance.boss().meterBarStackLimit());
        this.afflictions = new MeterAfflictions(instance);
    }

    /** The shared hold/bleed state for this fight, so a rescue act can free a player without a meter reference. */
    public MeterAfflictions afflictions() {
        return afflictions;
    }

    /**
     * Arms {@code spec} for this fight and starts the pulse if it was not already running.
     * <p>
     * When meters are disabled for this boss in config, or the fight is already tearing down, the
     * returned meter is inert rather than null: every {@code add}/{@code cure} call on it is a no-op,
     * so a boss never has to null-check the thing its whole design is built on.
     */
    public PlayerMeter attach(MeterSpec spec) {
        PlayerMeter meter = new PlayerMeter(instance, spec, afflictions);
        if (!enabled || shutDown) {
            meter.detach();
            return meter;
        }
        meters.add(meter);
        armHealing(meter);
        startPulse();
        return meter;
    }

    /** Drops one meter, clearing its per-player state. The pulse stops once the last one is gone. */
    public void detach(PlayerMeter meter) {
        if (meter == null) {
            return;
        }
        meters.remove(meter);
        meter.detach();
        // The healing hook belongs to the meter that asked for it, not to the fight: a phase-scoped
        // meter detaching must stop taxing healing immediately, not at fight end.
        healingRules.removeIf(rule -> rule.meter() == meter);
        if (healingRules.isEmpty()) {
            stopHealingHook();
        }
        if (meters.isEmpty()) {
            stopPulse();
            releaseActionBar();
            bar.clear();
        }
    }

    /** The attached meter with this id, or null. Ids come from {@link MeterSpec#id()}. */
    public PlayerMeter meter(String id) {
        for (PlayerMeter meter : meters) {
            if (meter.id().equals(id)) {
                return meter;
            }
        }
        return null;
    }

    /**
     * Every meter this fight has attached.
     * <p>
     * Exists for {@link dev.rbm72.weaponsplugin.items.kit.Counterplay}, which answers the boss verb
     * "you are carrying a stack that armour and healing cannot touch" without knowing which of the four
     * skins is on the player — a boss drop reads as "this shakes off whatever the boss is stacking on
     * you", not as "this specifically clears Chill". A copy, so a caller iterating it while a fight tears
     * down cannot trip over {@link #detachAll} clearing the live list.
     */
    public List<PlayerMeter> meters() {
        return List.copyOf(meters);
    }

    public boolean isEmpty() {
        return meters.isEmpty();
    }

    /**
     * Fight teardown: every hold released, every meter emptied, the readout gone, the healing hook off
     * the handler list. Safe to call twice and safe to call from a fight that is already half-broken —
     * this is the method that has to leave no player rooted, blinded or bleeding once the boss no longer
     * exists, and no listener still running once the fight does not.
     */
    public void detachAll() {
        shutDown = true;
        stopPulse();
        // Before anything below can throw: nothing may be left able to convert a heal or to keep being
        // polled for an action-bar line belonging to a fight that is over. Isolated so that a failure
        // here cannot skip the hold release further down, which is the part a player feels.
        try {
            stopHealingHook();
            releaseActionBar();
        } catch (Exception e) {
            log("Meter readout/healing teardown threw — continuing so held players are still released.", e);
        }
        for (PlayerMeter meter : meters) {
            meter.detach();
        }
        meters.clear();
        // Afflictions last: releasing a hold clears potion effects, and doing that before the meters
        // stop would let the next pulse re-assert them on a player we just freed.
        afflictions.releaseAll();
        bar.clear();
    }

    // ---------------------------------------------------------------- pulse

    private void startPulse() {
        if (task != null) {
            return;
        }
        task = instance.plugin().getServer().getScheduler()
                .runTaskTimer(instance.plugin(), this::pulse, intervalTicks, intervalTicks);
        // Tracked so it dies with the fight even if nothing ever calls detachAll — the registry must
        // not be the one system that can outlive a boss.
        instance.trackTask(task);
        claimActionBar();
    }

    private void stopPulse() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
    }

    /**
     * One step of everything. Wrapped end to end the way {@link BossInstance} wraps its calls into
     * phase mechanics and events: a meter that throws costs one pulse and a log line, never the fight.
     */
    private void pulse() {
        if (shutDown) {
            return;
        }
        try {
            afflictions.pulse(intervalTicks);
        } catch (Exception e) {
            log("Meter afflictions threw on pulse — skipping this step.", e);
        }
        if (!instance.entity().isValid()) {
            // Mid-despawn or mid-death. Holds still expired above; filling meters against a boss that
            // is no longer there would just be noise.
            return;
        }
        List<Player> present = Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
        for (PlayerMeter meter : List.copyOf(meters)) {
            try {
                meter.pulse(intervalTicks, present);
            } catch (Exception e) {
                log("Meter '" + meter.id() + "' threw on pulse — skipping this step.", e);
            }
        }
        try {
            render();
        } catch (Exception e) {
            log("Meter readout threw while rendering — the meters themselves are unaffected.", e);
        }
    }

    // ---------------------------------------------------------------- readout

    /**
     * Draws every viewer's meter on whichever channel {@link #useBarFor} picked for them this pulse.
     * <p>
     * Both channels are written from the same pass so a player can never briefly hold both: returning
     * null from the bar's readout function is what hides an existing bar, so a player moving to the
     * action bar loses their bar in the same call that gives them a line.
     */
    private void render() {
        if (meters.isEmpty()) {
            actionBarLines.clear();
            return;
        }
        Map<UUID, Component> lines = new HashMap<>();
        bar.update(instance.barViewers(), player -> {
            MechanicBar.Readout readout = readoutFor(player);
            if (readout == null || useBarFor(player)) {
                return readout;
            }
            Component compact = compactFor(player);
            if (compact != null) {
                lines.put(player.getUniqueId(), compact);
            }
            return null;
        });
        // Replaced wholesale rather than merged: anyone who left the arena, died, or moved back onto the
        // bar this pulse has to lose their line, and nothing else in this class would ever remove it.
        actionBarLines.clear();
        actionBarLines.putAll(lines);
    }

    /**
     * Whether this player's meter gets a boss bar of its own this pulse.
     * <p>
     * Counts only the bars this fight is already showing them ({@link BossInstance#visibleBarCount}) —
     * never its own — so the decision cannot oscillate off its own output. It can still change when the
     * mechanic bar appears or disappears, and that is the intent: the meter yields the stack to whatever
     * the current mechanic is demanding and takes it back the moment the mechanic is done.
     * <p>
     * A meter past its warn line, or a player already held by one, overrides the limit and takes a bar
     * whatever else is on screen. The merged action-bar line is a shared channel and an attack's cast
     * bar covers it outright for the length of every telegraph; a few seconds of that is survivable for
     * a meter at 20% and is not survivable for one about to detonate, which is the case the whole
     * always-visible requirement is really about.
     */
    private boolean useBarFor(Player player) {
        return switch (channel) {
            case BAR -> true;
            case ACTION_BAR -> false;
            case AUTO -> urgentFor(player) || instance.visibleBarCount(player) < barStackLimit;
        };
    }

    private boolean urgentFor(Player player) {
        for (PlayerMeter meter : meters) {
            if (meter.demandsAttention(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This player's line, merged across every attached meter. The fullest one owns the bar's progress
     * and colour — it is the one about to do something to them — and the rest trail after it as text.
     * <p>
     * In practice the roster attaches exactly one meter per boss (the batch-4 audit holds the line at
     * four skins across sixteen bosses, one each), so the merge is a correctness guarantee for an
     * unusual case rather than the normal display.
     */
    private MechanicBar.Readout readoutFor(Player player) {
        MechanicBar.Readout leading = null;
        Component trailing = null;
        for (PlayerMeter meter : meters) {
            MechanicBar.Readout readout = meter.readout(player);
            if (readout == null) {
                continue;
            }
            if (leading == null || readout.progress() > leading.progress()) {
                if (leading != null) {
                    trailing = appendSegment(trailing, leading.text());
                }
                leading = readout;
            } else {
                trailing = appendSegment(trailing, readout.text());
            }
        }
        if (leading == null) {
            return null;
        }
        return trailing == null
                ? leading
                : new MechanicBar.Readout(leading.text().append(SEPARATOR).append(trailing),
                        leading.progress(), leading.color());
    }

    /** The action-bar form of the same thing, or null when no attached meter has anything to say. */
    private Component compactFor(Player player) {
        Component line = null;
        for (PlayerMeter meter : meters) {
            Component segment = meter.compactReadout(player);
            if (segment == null) {
                continue;
            }
            line = line == null ? segment : line.append(SEPARATOR).append(segment);
        }
        return line;
    }

    private static Component appendSegment(Component existing, Component segment) {
        return existing == null ? segment : existing.append(SEPARATOR).append(segment);
    }

    // ---------------------------------------------------------------- action bar channel

    /**
     * Registers this fight's contribution to the merged action-bar line, once, the first time a meter is
     * actually attached.
     * <p>
     * A {@link ActionBarHub.Source} rather than a {@link ActionBarHub#flash}: a flash takes the whole
     * line for its duration, and a fight-long display re-flashing itself every pulse would blank weapon
     * cooldowns and movement charges for the entire fight. Merged, the meter sits alongside them.
     */
    private void claimActionBar() {
        if (actionBarSourceRegistered || channel == Channel.BAR) {
            return;
        }
        actionBarSourceRegistered = true;
        instance.plugin().actionBarHub().register(actionBarSource);
    }

    private void releaseActionBar() {
        actionBarLines.clear();
        if (!actionBarSourceRegistered) {
            return;
        }
        actionBarSourceRegistered = false;
        instance.plugin().actionBarHub().unregister(actionBarSource);
    }

    /**
     * Polled by {@link ActionBarHub} for every online player several times a second, which is why it
     * only reads a map: the line itself was built during {@link #render()}. Anyone this fight is not
     * currently drawing a meter for — another arena's players, spectators, anyone on the boss-bar
     * channel — is simply not in the map and contributes nothing to their line.
     */
    private List<Component> actionBarSegments(Player player) {
        Component line = actionBarLines.get(player.getUniqueId());
        return line == null ? List.of() : List.of(line);
    }

    // ---------------------------------------------------------------- healing conversion

    /**
     * One meter's opt-in "healing above a cap becomes meter instead of health" rule, with its numbers
     * already resolved from config, plus the per-player allowance it spends.
     * <p>
     * The allowance is a refilling bucket rather than a hard per-event cap: a hard cap would tax every
     * healing item that restores more than one tick's worth, which turns the cap into a flat conversion
     * rate and makes the "low cap" in the design meaningless. A bucket lets a player who has not healed
     * recently take one real heal and taxes the sustained stream that follows, which is the behaviour
     * the rule is actually aimed at.
     */
    private static final class HealingRule {

        private final PlayerMeter meter;
        private final double allowedPerSecond;
        private final double capacity;
        private final double meterPerHealth;
        /**
         * Health already spent out of the allowance, per player. Bounded by the players who healed
         * inside this arena during this fight, and dies with the rule.
         */
        private final Map<UUID, Bucket> spent = new HashMap<>();

        private HealingRule(PlayerMeter meter, double allowedPerSecond, double burstSeconds, double meterPerHealth) {
            this.meter = meter;
            this.allowedPerSecond = Math.max(0.0, allowedPerSecond);
            this.capacity = Math.max(0.0, allowedPerSecond) * Math.max(0.05, burstSeconds);
            this.meterPerHealth = Math.max(0.0, meterPerHealth);
        }

        private PlayerMeter meter() {
            return meter;
        }

        /** How much of {@code healed} this player is allowed to actually keep; the rest converts. */
        private double allow(Player player, double healed, long nowMs) {
            Bucket bucket = spent.computeIfAbsent(player.getUniqueId(), id -> new Bucket());
            if (bucket.lastMs > 0) {
                bucket.used = Math.max(0.0, bucket.used - allowedPerSecond * (nowMs - bucket.lastMs) / 1000.0);
            }
            bucket.lastMs = nowMs;
            double allowed = Math.min(healed, Math.max(0.0, capacity - bucket.used));
            bucket.used += allowed;
            return allowed;
        }
    }

    private static final class Bucket {
        private double used;
        private long lastMs;
    }

    /**
     * Arms {@code meter}'s healing rule if it declared one, registering the fight's listener on first
     * use. A spec with no {@link MeterSpec.HealingConversion} produces no rule and no listener at all,
     * which is what makes this genuinely opt-in rather than merely inert for the other bosses.
     */
    private void armHealing(PlayerMeter meter) {
        MeterSpec.HealingConversion conversion = meter.spec().healingConversion();
        if (conversion == null) {
            return;
        }
        Boss boss = instance.boss();
        String id = meter.id();
        healingRules.add(new HealingRule(meter,
                boss.meterDouble(id, "heal-cap-per-second", conversion.healthPerSecond()),
                boss.meterDouble(id, "heal-cap-burst-seconds", conversion.burstSeconds()),
                boss.meterDouble(id, "heal-converted-per-health", conversion.meterPerHealth())));
        if (healingListener == null) {
            healingListener = new HealingHook();
            instance.plugin().getServer().getPluginManager().registerEvents(healingListener, instance.plugin());
        }
    }

    private void stopHealingHook() {
        healingRules.clear();
        if (healingListener != null) {
            // The one piece of this framework that Bukkit keeps a reference to. Left registered, it
            // would go on taxing healing for a boss that no longer exists, and every subsequent fight
            // would add another.
            HandlerList.unregisterAll(healingListener);
            healingListener = null;
        }
    }

    /**
     * The Plague Warden's "Necrotic Rot" rule as framework: healing past a low per-player cap stops
     * restoring health and fills the meter instead. Registered only for a fight whose meters asked for
     * it, and unregistered in {@link #detachAll()}.
     */
    private final class HealingHook implements Listener {

        /**
         * HIGHEST rather than MONITOR: this changes the heal, so it cannot sit at the priority reserved
         * for observers — but it wants to be as close to the last word as an event that still modifies
         * can get, so that a mitigation or healing plugin earlier in the chain has already settled the
         * number being taxed.
         */
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onRegainHealth(EntityRegainHealthEvent event) {
            try {
                convert(event);
            } catch (Exception e) {
                // Same contract as everything else here: a broken meter rule costs one heal's worth of
                // tax and a log line, never the heal and never the fight.
                log("Meter healing conversion threw — letting the heal through untouched.", e);
            }
        }

        private void convert(EntityRegainHealthEvent event) {
            if (shutDown || healingRules.isEmpty() || !(event.getEntity() instanceof Player player)) {
                return;
            }
            // Spectators and creative players are never a fight's business, and neither is anyone
            // standing outside this arena — including the players of a second fight running at the same
            // time, whose healing is that fight's problem and not this one's.
            if (!Arena.isCombatant(player) || !insideThisFight(player)) {
                return;
            }
            for (HealingRule rule : healingRules) {
                // Re-read the amount every time instead of caching the original. Another live fight's
                // hook, or an earlier rule in this list, may already have cut it down, and netting each
                // rule against the untouched original would convert the same point of health twice.
                double healed = event.getAmount();
                if (healed <= 0) {
                    return;
                }
                double converted = healed - rule.allow(player, healed, System.currentTimeMillis());
                if (converted <= 0) {
                    continue;
                }
                event.setAmount(healed - converted);
                rule.meter().add(player, converted * rule.meterPerHealth);
            }
        }
    }

    /**
     * Whether this player is standing in this fight right now, measured off the boss's live position the
     * same way the pulse is — a fight that has chased its group across the arena still recognises them.
     * A boss mid-despawn recognises nobody, which is the right answer: a fight that is ending has no
     * business still eating anyone's healing.
     */
    private boolean insideThisFight(Player player) {
        LivingEntity boss = instance.entity();
        if (boss == null || !boss.isValid()) {
            return false;
        }
        Location bossAt = boss.getLocation();
        Location playerAt = player.getLocation();
        if (playerAt.getWorld() == null || !playerAt.getWorld().equals(bossAt.getWorld())) {
            return false;
        }
        double radius = instance.arena().radius();
        return playerAt.distanceSquared(bossAt) <= radius * radius;
    }

    private void log(String message, Exception e) {
        instance.plugin().getLogger().log(Level.SEVERE, message, e);
    }
}
