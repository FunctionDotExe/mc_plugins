package dev.rbm72.weaponsplugin.boss.meter;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One live meter: a 0..{@code cap} number per player, owned by one fight, filling from the conditions
 * its {@link MeterSpec} names and falling only when a player performs that boss's physical cure.
 * <p>
 * The three properties that make this the roster's structural answer to face-tanking and infinite
 * healing (§0.2 rule 4, and the batch-4 audit that consolidated four bosses onto this one system) are
 * enforced here rather than left to each boss to honour:
 * <ul>
 *   <li><b>It ignores armour.</b> It is not damage and never passes through a damage event, so nothing
 *       in the mitigation chain — protection, resistance, a shield, a damage-reduction accessory — sees
 *       it at all. There is no code path that could apply mitigation to it even by accident.</li>
 *   <li><b>Healing cannot touch it.</b> The only ways a value goes down are a {@link MeterSpec.Cure}
 *       matching this pulse, an explicit {@link #cure} call from the boss's own physical cure act, and
 *       the reset that follows the threshold firing. There is no passive decay and no "reduce by"
 *       primitive that isn't named after a cure.</li>
 *   <li><b>Filling it always costs the player something.</b> {@link MeterThreshold} fires exactly once
 *       per cap, behind a cooldown, and the value is reset <em>before</em> the payload runs.</li>
 * </ul>
 * Deliberately <b>not</b> wired to {@link BossInstance#recordExposure()}. A meter runs for the whole
 * fight, so crediting the phase floor from it would mark every phase's mandatory mechanic as engaged
 * within a second of that phase starting and hand high-DPS groups the phase-skipping chain the floor
 * exists to close. Whether the group actually did this phase's job stays the phase mechanic's call.
 */
public final class PlayerMeter {

    private static final int BAR_SEGMENTS = 12;

    private final BossInstance instance;
    private final MeterSpec spec;
    private final MeterAfflictions afflictions;

    private final Map<UUID, Double> values = new HashMap<>();
    /** Last pulse's position per player, the basis for "are you actually moving" (Void Echo's cure). */
    private final Map<UUID, Location> lastPositions = new HashMap<>();
    /** Earliest wall clock at which this player's threshold may fire again. */
    private final Map<UUID, Long> thresholdReadyAtMs = new HashMap<>();
    /**
     * Who was being cured as of the last pulse, for the readout to colour itself from. Cached rather
     * than recomputed at render time because the cure conditions are the expensive ones (the campfire
     * and lightning-rod checks are block scans) and because a movement-based cure cannot be evaluated
     * at all outside a pulse — there is no step length to measure movement against.
     */
    private final Set<UUID> curing = new HashSet<>();

    private boolean detached;
    private double rateScale = 1.0;

    /**
     * Whether a cure running at the same instant as a contribution nets against it instead of
     * suppressing it outright — {@code bosses.<boss>.meter.<id>.cure-nets-gain}, off by default.
     * <p>
     * This is a tuning decision, not a bug, and the two answers play very differently. Suppressing (the
     * default) means the physical cure act is always worth doing: get to the campfire and the clock
     * stops, wherever you are standing. Netting makes a contested cure a tug-of-war the boss can win —
     * standing in the fire while pressed against a boss whose aura fills faster than the fire drains
     * means you are still climbing, and the real answer becomes disengaging first. Off by default
     * because turning it on retunes every fight the meter appears in at once.
     * <p>
     * Read once at construction: config does not change mid-fight, and a lookup here would run per
     * player per pulse for the whole fight.
     */
    private final boolean cureNetsGain;

    PlayerMeter(BossInstance instance, MeterSpec spec, MeterAfflictions afflictions) {
        this.instance = instance;
        this.spec = spec;
        this.afflictions = afflictions;
        this.cureNetsGain = instance.boss().meterBoolean(spec.id(), "cure-nets-gain", false);
    }

    public BossInstance instance() {
        return instance;
    }

    public MeterSpec spec() {
        return spec;
    }

    public String id() {
        return spec.id();
    }

    /** The shared hold/bleed state, for a threshold payload that needs to pin its victim. */
    public MeterAfflictions afflictions() {
        return afflictions;
    }

    public boolean detached() {
        return detached;
    }

    public double value(Player player) {
        return player == null ? 0.0 : values.getOrDefault(player.getUniqueId(), 0.0);
    }

    public double fraction(Player player) {
        return Math.max(0.0, Math.min(1.0, value(player) / spec.cap()));
    }

    /** Whole stacks for a stacked skin (Void Echo), 0 for a smoothly-filling one. */
    public int stacks(Player player) {
        return spec.stacked() ? (int) Math.floor(value(player) / spec.stackSize()) : 0;
    }

    public boolean atMax(Player player) {
        return value(player) >= spec.cap();
    }

    /**
     * Scales every contribution for the rest of the fight — the "in the last phase charge climbs
     * roughly twice as fast" line that three of the four designs end on.
     * <p>
     * A lever on the live meter rather than a condition on the spec because a {@link MeterCondition}
     * cannot see which phase is running: {@link BossInstance} keeps its current phase private, and
     * routing phase identity into every condition would make conditions depend on fight structure. A
     * phase's {@code onEnter} calling this instead keeps the escalation where the escalation is
     * authored. Cures are deliberately untouched — a hotter meter must not also become easier to shed.
     */
    public void setRateScale(double scale) {
        this.rateScale = Math.max(0.0, scale);
    }

    public double rateScale() {
        return rateScale;
    }

    /**
     * Pushes a player's meter up by {@code amount} — an Ice Lance impact, an Echo strike landing, the
     * splash off somebody else's rupture.
     * <p>
     * Clamps at the cap and deliberately does <em>not</em> fire the threshold itself: the threshold
     * fires from the pulse, at most a few ticks later, so there is exactly one place in the system
     * where a detonation can start. An attack that detonated a player inline would be doing it from
     * inside its own damage/particle loop, mid-iteration over a player list it is still using.
     */
    public void add(Player player, double amount) {
        if (detached || player == null || amount <= 0 || !Arena.isCombatant(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        // Not Map#merge: its remapping function is skipped entirely for an absent key, so a single
        // spike larger than the cap would land unclamped on a player who had no value yet.
        values.put(id, Math.min(spec.cap(), values.getOrDefault(id, 0.0) + amount));
    }

    /** {@link #add} in the units a stacked skin actually talks in. */
    public void addStacks(Player player, int stacks) {
        if (spec.stacked()) {
            add(player, stacks * spec.stackSize());
        }
    }

    /**
     * The boss's designated physical cure act, applied as a one-shot — the moment a player grabs the
     * lightning rod, the burst off a killed Bloated Carrier. Named after what it is so that every
     * reduction in this class is greppable and no caller can mistake it for generic arithmetic.
     */
    public void cure(Player player, double amount) {
        if (detached || player == null || amount <= 0) {
            return;
        }
        values.computeIfPresent(player.getUniqueId(), (id, current) -> Math.max(0.0, current - amount));
    }

    /** Wipes one player's meter outright — the full-strength version of the cure act. */
    public void clearFor(Player player) {
        if (player != null) {
            values.remove(player.getUniqueId());
        }
    }

    /**
     * Everyone in the fight at or above {@code fraction} — the "who is currently a carrier" question
     * both the contagion rule and several designs' damage rules ask (the Storm Tyrant's pylons only
     * let discharged players hurt him; the Plague Warden's Host only takes hits from the barely
     * infected).
     */
    public List<Player> above(double fraction) {
        double threshold = spec.cap() * fraction;
        List<Player> found = new ArrayList<>();
        for (Player player : combatants()) {
            if (value(player) >= threshold) {
                found.add(player);
            }
        }
        return found;
    }

    /**
     * Spikes everyone else within {@code radius} — the shape of a rupture spreading infection and of a
     * lightning strike chaining onward. Skips {@code origin}, who has already paid for it.
     */
    public void spikeNearby(Player origin, double radius, double amount) {
        for (Player nearby : Arena.combatants(origin.getLocation(), radius)) {
            if (!nearby.equals(origin)) {
                add(nearby, amount);
            }
        }
    }

    /** Drops this meter, leaving no per-player state behind. Safe to call twice. */
    public void detach() {
        if (detached) {
            return;
        }
        detached = true;
        values.clear();
        lastPositions.clear();
        thresholdReadyAtMs.clear();
        curing.clear();
    }

    // ---------------------------------------------------------------- the pulse

    /**
     * One step of the whole meter, driven by {@link MeterRegistry}'s single task.
     * <p>
     * Contagion — and only contagion — reads {@code snapshot}, the values as they stood before anybody
     * was updated this pulse. Without that, two infected players standing together would have the
     * second one's gain computed against the first one's already-raised value inside the same tick, so
     * the spread rate would silently depend on iteration order and compound within a tick. Everything
     * else reads live values, for the mirror-image reason spelled out below.
     */
    void pulse(long stepTicks, List<Player> present) {
        if (detached) {
            return;
        }
        double stepSeconds = stepTicks / 20.0;
        Map<UUID, Double> snapshot = new HashMap<>(values);
        Set<UUID> here = new HashSet<>();

        for (Player player : present) {
            UUID id = player.getUniqueId();
            here.add(id);

            double moved = movedSinceLastPulse(player);
            lastPositions.put(id, player.getLocation().clone());

            // A player already held by this meter's own threshold is being punished right now and
            // physically cannot walk to the cure. Continuing to fill them would mean the freeze
            // re-freezes the instant it releases — a death spiral with no counterplay, which is exactly
            // the "unavoidable by design" damage §0.2 rule 3 rules out.
            if (afflictions.isHeld(player)) {
                curing.remove(id);
                continue;
            }

            // The player's own accumulation reads the LIVE value, not the snapshot: a payload that
            // already fired this pulse (a rupture spiking its neighbours) may have raised players who
            // are still ahead of us in this loop, and reading the stale snapshot would write that spike
            // straight back out again.
            double current = values.getOrDefault(id, 0.0);
            MeterContext ctx = new MeterContext(instance, player, current, spec.cap(), stepSeconds, moved);

            double drain = bestCureRate(ctx);
            // Under the default, a cure zeroes the gain rather than being netted against it, so the
            // contribution conditions are not even evaluated — they are the expensive ones (the campfire
            // and lightning-rod checks are block scans) and their answer cannot matter.
            double gain = drain > 0 && !cureNetsGain ? 0.0 : gainRate(ctx, snapshot, player);
            double delta = gain - drain;

            // "You are curing" is reported off the net direction, not off the mere presence of a cure.
            // Under netting a player can be standing in the fire and still climbing, and colouring that
            // green would tell them they are safe at the exact moment they are not.
            boolean cureWinning = drain > 0 && delta < 0;
            if (cureWinning) {
                curing.add(id);
            } else {
                curing.remove(id);
            }
            double next = Math.max(0.0, Math.min(spec.cap(), current + delta * stepSeconds));

            if (next >= spec.cap() && fireThreshold(player)) {
                // Reset before the payload: it may damage, kill, teleport or schedule, and a value left
                // at the cap underneath it would re-detonate on the very next pulse.
                values.put(id, spec.cap() * spec.resetFraction());
                runThreshold(player);
                continue;
            }
            values.put(id, next);
            ambience(player, next / spec.cap(), cureWinning);
        }

        // Anyone who left the arena or logged out loses their meter rather than banking it for a
        // return. Keeping it would let a player at 95% Chill relog to dodge the freeze and walk back in.
        values.keySet().retainAll(here);
        lastPositions.keySet().retainAll(here);
        thresholdReadyAtMs.keySet().retainAll(here);
        curing.retainAll(here);
    }

    private double movedSinceLastPulse(Player player) {
        Location previous = lastPositions.get(player.getUniqueId());
        // First sight of a player counts as not having moved: the alternative is treating their whole
        // walk into the arena as movement and handing them a free tick of Void Echo decay.
        return previous == null ? 0.0 : MeterContext.flatDistance(previous, player.getLocation());
    }

    /**
     * The fastest cure currently applying, or 0. Fastest rather than summed so that a skin with two
     * cure routes (stand in the fire, or catch the Carrier burst) does not accidentally become twice as
     * forgiving to the player who happens to be doing both at once.
     */
    private double bestCureRate(MeterContext ctx) {
        double best = 0.0;
        for (MeterSpec.Cure cure : spec.cures()) {
            if (cure.perSecond() > best && cure.when().test(ctx)) {
                best = cure.perSecond();
            }
        }
        return best;
    }

    private double gainRate(MeterContext ctx, Map<UUID, Double> snapshot, Player player) {
        double base = 0.0;
        for (MeterSpec.Contribution contribution : spec.contributions()) {
            if (contribution.when().test(ctx)) {
                base += contribution.perSecond();
            }
        }
        for (MeterSpec.Multiplier multiplier : spec.multipliers()) {
            if (multiplier.when().test(ctx)) {
                base *= multiplier.factor();
            }
        }

        MeterSpec.Contagion contagion = spec.contagion();
        if (contagion == null) {
            return base * rateScale;
        }
        int carriers = countCarriers(snapshot, player, contagion);
        if (carriers <= 0) {
            return base * rateScale;
        }
        return (base * Math.pow(contagion.factorPerCarrier(), carriers)
                + contagion.flatPerSecondPerCarrier() * carriers) * rateScale;
    }

    /**
     * How many other combatants near {@code player} are carrying enough to infect them. Counted off the
     * pre-pulse snapshot and capped, so group size raises the pressure without a five-player huddle
     * turning into an instant wipe — the designs want more players to mean more coordination cost, not
     * more raw lethality.
     */
    private int countCarriers(Map<UUID, Double> snapshot, Player player, MeterSpec.Contagion contagion) {
        double carrierValue = spec.cap() * contagion.carrierFraction();
        int carriers = 0;
        for (Player other : Arena.combatants(player.getLocation(), contagion.radius())) {
            if (other.equals(player)) {
                continue;
            }
            if (snapshot.getOrDefault(other.getUniqueId(), 0.0) >= carrierValue) {
                carriers++;
                if (carriers >= contagion.maxCarriersCounted()) {
                    break;
                }
            }
        }
        return carriers;
    }

    /** True when this player's threshold is off cooldown; stamps the next window as a side effect. */
    private boolean fireThreshold(Player player) {
        if (spec.threshold() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long readyAt = thresholdReadyAtMs.get(player.getUniqueId());
        if (readyAt != null && now < readyAt) {
            return false;
        }
        thresholdReadyAtMs.put(player.getUniqueId(),
                now + (long) (spec.thresholdCooldownSeconds() * 1000L));
        return true;
    }

    private void runThreshold(Player player) {
        Location at = player.getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.0, 0), spec.accent(), 2.4f, 50, 0.9);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        try {
            spec.threshold().fire(this, player);
        } catch (Exception e) {
            // A broken payload must cost one detonation, not the fight. The value has already been
            // reset above, so the player is not left pinned at the cap re-firing it every pulse.
            instance.plugin().getLogger().log(java.util.logging.Level.SEVERE,
                    "Meter '" + spec.id() + "' threshold threw for " + player.getName()
                            + " — skipping this detonation.", e);
        }
    }

    /**
     * The meter making itself heard as it gets dangerous. Purely reinforcement of state the readout bar
     * is already showing (§0.1 — a particle may never <em>be</em> the mechanic), and silent below the
     * warn line so a meter ticking along at 20% is not a constant light show.
     */
    private void ambience(Player player, double fraction, boolean curing) {
        if (curing || fraction < spec.warnFraction()) {
            return;
        }
        Fx.coloredBurst(player.getLocation().add(0, 1.2, 0), spec.accent(), 1.0f, 6, 0.3);
        Fx.sound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f + 0.8f * (float) fraction);
    }

    private List<Player> combatants() {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    // ---------------------------------------------------------------- readout

    /**
     * This player's line on the meter bar, or null if they have nothing to show — the meter is empty,
     * they are not a combatant, or the fight has moved on from them.
     * <p>
     * Held players get told what is happening to them instead of a fill they cannot act on: someone
     * frozen solid needs to read "FROZEN — allies break the ice", not "Chill 100%".
     */
    MechanicBar.Readout readout(Player player) {
        if (detached || !Arena.isCombatant(player)) {
            return null;
        }
        String held = afflictions.heldLabel(player);
        if (held != null) {
            return MechanicBar.Readout.of(
                    Component.text(spec.label() + "  ", NamedTextColor.WHITE)
                            .append(Component.text(held, NamedTextColor.RED)),
                    1.0, BossBar.Color.RED);
        }

        double fraction = fraction(player);
        boolean beingCured = curing.contains(player.getUniqueId());
        NamedTextColor tone = tone(fraction, beingCured);

        Component text = Component.text(spec.label() + "  ", NamedTextColor.WHITE)
                .append(spec.stacked() ? stackGlyphs(player, tone) : fillGlyphs(fraction, tone))
                .append(Component.text("   " + (beingCured ? spec.cureHint() : spec.dangerHint()),
                        beingCured ? NamedTextColor.GREEN : NamedTextColor.GRAY));

        BossBar.Color barColor = beingCured ? BossBar.Color.GREEN
                : fraction > 0.75 ? BossBar.Color.RED
                : fraction > 0.45 ? BossBar.Color.YELLOW : BossBar.Color.WHITE;
        return MechanicBar.Readout.of(text, fraction, barColor);
    }

    /**
     * The same state as {@link #readout}, squeezed into something that can share a single line with
     * weapon cooldowns and movement charges — for when {@link MeterRegistry} has moved this player's
     * meter onto the action bar rather than adding a third boss bar to their stack.
     * <p>
     * No fill glyphs and no hint: the twelve-segment bar and a sentence of advice are what a channel
     * with its own real estate can afford, and putting them in a merged line would push everything else
     * off the screen. What survives is what the player cannot act without — which meter, and how bad it
     * is right now. Null on exactly the same conditions as {@link #readout}, so the two channels never
     * disagree about whether this player has a meter at all.
     */
    Component compactReadout(Player player) {
        if (detached || !Arena.isCombatant(player)) {
            return null;
        }
        String held = afflictions.heldLabel(player);
        if (held != null) {
            return Component.text(spec.label() + " ", NamedTextColor.WHITE)
                    .append(Component.text(held, NamedTextColor.RED));
        }
        double fraction = fraction(player);
        boolean beingCured = curing.contains(player.getUniqueId());
        String value = spec.stacked()
                ? stacks(player) + "/" + (int) Math.round(spec.cap() / spec.stackSize())
                : Math.round(fraction * 100) + "%";
        return Component.text(spec.label() + " ", NamedTextColor.WHITE)
                .append(Component.text(value, tone(fraction, beingCured)));
    }

    /**
     * True once this player's meter has crossed its warn line, or while a threshold already has them
     * held — the band in which the readout has stopped being information and become a warning.
     * <p>
     * {@link MeterRegistry} uses it to decide that a readout must not merely be present but
     * uncoverable: the merged action-bar line is briefly taken over by an attack's cast bar every
     * telegraph, and a few seconds of invisibility is survivable for a meter at 20% and not for one
     * about to detonate.
     */
    boolean demandsAttention(Player player) {
        if (detached || !Arena.isCombatant(player)) {
            return false;
        }
        if (afflictions.isHeld(player)) {
            return true;
        }
        double fraction = fraction(player);
        // The fraction test is guarded on being non-empty as well, so a skin authored with a warn line
        // of zero asks for a permanent bar rather than for one on every player standing at nothing.
        return fraction > 0.0 && fraction >= spec.warnFraction();
    }

    /** Shared by both channels so a meter never reads as one severity on the bar and another on the line. */
    private NamedTextColor tone(double fraction, boolean beingCured) {
        return beingCured ? NamedTextColor.GREEN
                : fraction > 0.75 ? NamedTextColor.RED
                : fraction > 0.45 ? NamedTextColor.GOLD : NamedTextColor.YELLOW;
    }

    private Component stackGlyphs(Player player, NamedTextColor tone) {
        int total = (int) Math.round(spec.cap() / spec.stackSize());
        int filled = Math.min(total, stacks(player));
        return Component.text("◆".repeat(filled), tone)
                .append(Component.text("◇".repeat(Math.max(0, total - filled)), NamedTextColor.DARK_GRAY))
                .append(Component.text("  " + filled + "/" + total, tone));
    }

    private Component fillGlyphs(double fraction, NamedTextColor tone) {
        int filled = (int) Math.round(BAR_SEGMENTS * fraction);
        return Component.text("▮".repeat(filled), tone)
                .append(Component.text("▯".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
    }
}
