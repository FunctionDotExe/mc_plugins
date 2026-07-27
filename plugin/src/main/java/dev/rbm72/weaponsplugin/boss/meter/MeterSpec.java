package dev.rbm72.weaponsplugin.boss.meter;

import org.bukkit.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole of what makes one meter skin different from another.
 * <p>
 * Chill, Static Charge, Infection and Void Echo are four boss identities and one rule: a per-player
 * number that ignores armour, that healing cannot touch, that climbs while you are somewhere the boss
 * is punishing, that falls only when you go and do a specific physical act, and that does something
 * ugly to you when it fills. The batch-4 audit settled that the roster holds at exactly those four
 * and adds no more, which is precisely why they are configuration of one class instead of four
 * subclasses — a fifth subclass would be the thing nobody noticed we were adding.
 * <p>
 * A spec is immutable and stateless: build it once, hand it to
 * {@link MeterRegistry#attach(MeterSpec)}, and the returned {@link PlayerMeter} owns all the running
 * per-player numbers. The same spec can legitimately be attached by two different fights at once.
 * <p>
 * <b>There is deliberately no passive decay knob.</b> "Cannot be healed off, only the boss's cure act
 * reduces it" is the load-bearing property of the whole system — it is the roster's structural answer
 * to face-tanking and infinite healing (§0.2 rule 4). A general-purpose "drains by itself over time"
 * setting would let a boss quietly opt out of that with one config line, so the only way a value ever
 * goes down is a {@link Cure} that names a real thing the player must go and do, or the reset that
 * follows the threshold firing. The one healing-adjacent knob here, {@link HealingConversion}, only
 * ever pushes a meter <em>up</em> — it is the other half of the same argument, not an exception to it.
 */
public final class MeterSpec {

    /** A condition that adds to the meter while it holds, at a flat rate. Several may apply at once. */
    public record Contribution(MeterCondition when, double perSecond) {
    }

    /**
     * A condition that scales the summed contributions while it holds. Multipliers compose, so
     * "×2 in water" and "×2 next to a charged ally" both applying is ×4 — which is exactly the Storm
     * Tyrant's stated worst case and the reason a stacked group standing in his flood evaporates.
     */
    public record Multiplier(MeterCondition when, double factor) {
    }

    /**
     * The physical act that takes the meter back down: standing at a lit campfire, touching a lightning
     * rod, standing in fire, moving continuously. While any cure holds, contributions are ignored
     * entirely rather than netted against the drain, so the act is always worth doing wherever the
     * player is standing. A server that wants a contested cure to be a tug-of-war instead sets
     * {@code bosses.<boss>.meter.<id>.cure-nets-gain} — see {@link PlayerMeter} for both sides of it.
     */
    public record Cure(MeterCondition when, double perSecond) {
    }

    /**
     * Player-to-player spread: your meter climbs faster near someone whose own meter is high.
     * <p>
     * First-class rather than a condition each boss re-derives, because two of the four skins need it
     * (Static Charge's "×2 near a charged ally", Infection's "×2 near infected allies") and doing it
     * with an ordinary {@link MeterCondition} is not actually possible — a condition can only see the
     * player it is asked about, and contagion needs to read <em>other</em> players' values out of the
     * meter that is currently mid-update. Building it into the meter also fixes the read ordering
     * once: every player's contagion is computed against the values as they stood at the start of the
     * pulse, so A and B infecting each other cannot feed back within a single tick and double-count.
     *
     * @param radius              how far the spread reaches, horizontally
     * @param carrierFraction     how full another player's meter must be before they count as a carrier
     * @param factorPerCarrier    multiplier applied per carrier in range (2.0 with one carrier = the ×2 both designs ask for)
     * @param flatPerSecondPerCarrier additive gain per carrier, for a skin that should spread even to someone
     *                            standing somewhere with no contributions of their own
     * @param maxCarriersCounted  ceiling on carriers counted, so a five-player huddle is punishing rather
     *                            than instantly lethal — the designs want group size to be the difficulty
     *                            knob, not a wipe button
     */
    public record Contagion(double radius, double carrierFraction, double factorPerCarrier,
                            double flatPerSecondPerCarrier, int maxCarriersCounted) {
    }

    /**
     * The Plague Warden's "Necrotic Rot" rule: healing above a low per-player cap stops restoring
     * health and becomes meter instead.
     * <p>
     * This is the one healing hook the framework has, and it is the other half of the argument the
     * meter itself makes. "Healing cannot take it down" already closes the obvious loophole; this
     * closes the answer to it, where a group simply out-heals the damage the meter is causing and
     * ignores the mechanic. Above the cap a healer is not failing to help — they are actively filling
     * the clock (batch-1 §4.6: "it does it by making healing a cost rather than by nerfing it").
     * <p>
     * <b>Opt-in, and null on a spec that never asks for it.</b> No listener is even registered for a
     * fight whose meters do not declare one, so the other bosses are not merely unaffected but
     * untouched. It is deliberately not given to all four skins: it is the Warden's identity, and a
     * roster-wide healing tax would turn "bring a healer" into a trap everywhere instead of a decision
     * about one boss.
     *
     * @param healthPerSecond sustained healing a player may take without paying for it
     * @param burstSeconds    how many seconds of that allowance may bank up, so one large heal landing
     *                        after a quiet stretch is not taxed as if it were spam — without this, any
     *                        healing item that restores more than one second's worth is always taxed and
     *                        the cap stops being a cap and becomes a flat conversion rate
     * @param meterPerHealth  meter units gained per point of health converted instead of healed
     */
    public record HealingConversion(double healthPerSecond, double burstSeconds, double meterPerHealth) {
    }

    private final String id;
    private final String label;
    private final Color accent;
    private final double cap;
    private final double stackSize;
    private final List<Contribution> contributions;
    private final List<Multiplier> multipliers;
    private final List<Cure> cures;
    private final Contagion contagion;
    private final HealingConversion healingConversion;
    private final MeterThreshold threshold;
    private final double resetFraction;
    private final double thresholdCooldownSeconds;
    private final double warnFraction;
    private final String cureHint;
    private final String dangerHint;

    private MeterSpec(Builder builder) {
        this.id = builder.id;
        this.label = builder.label;
        this.accent = builder.accent;
        this.cap = builder.cap;
        // Resolved here rather than in stacks(): otherwise a builder chain that sets the cap after the
        // stack count would silently size its stacks against the default cap.
        this.stackSize = builder.stackCount > 0 ? builder.cap / builder.stackCount : 0.0;
        this.contributions = List.copyOf(builder.contributions);
        this.multipliers = List.copyOf(builder.multipliers);
        this.cures = List.copyOf(builder.cures);
        this.contagion = builder.contagion;
        this.healingConversion = builder.healingConversion;
        this.threshold = builder.threshold;
        this.resetFraction = builder.resetFraction;
        this.thresholdCooldownSeconds = builder.thresholdCooldownSeconds;
        this.warnFraction = builder.warnFraction;
        this.cureHint = builder.cureHint;
        this.dangerHint = builder.dangerHint;
    }

    /**
     * @param id    stable key for {@link MeterRegistry#meter(String)}, so an attack can find and spike
     *              the meter its boss attached without holding a reference to it
     * @param label what the readout bar calls it — "Chill", "Static", "Infection", "Void Echo"
     */
    public static Builder builder(String id, String label) {
        return new Builder(id, label);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Color accent() {
        return accent;
    }

    public double cap() {
        return cap;
    }

    /** Meter units per visible stack, or 0 for a smoothly-filling meter. */
    public double stackSize() {
        return stackSize;
    }

    /** True when this skin reads as discrete stacks (Void Echo) rather than a continuous fill. */
    public boolean stacked() {
        return stackSize > 0;
    }

    public List<Contribution> contributions() {
        return contributions;
    }

    public List<Multiplier> multipliers() {
        return multipliers;
    }

    public List<Cure> cures() {
        return cures;
    }

    /** Null when this skin does not spread player to player. */
    public Contagion contagion() {
        return contagion;
    }

    /** Null — the default — when this skin lets healing be healing. */
    public HealingConversion healingConversion() {
        return healingConversion;
    }

    /** Null when filling the meter does nothing but sit there — valid, but only for a debug skin. */
    public MeterThreshold threshold() {
        return threshold;
    }

    /** Where the meter lands after the threshold fires. 0.0 = wiped, 0.5 = the Void Echo-style partial reset. */
    public double resetFraction() {
        return resetFraction;
    }

    public double thresholdCooldownSeconds() {
        return thresholdCooldownSeconds;
    }

    /** Above this fill the meter starts making itself heard — ambient particles and a rising tone. */
    public double warnFraction() {
        return warnFraction;
    }

    public String cureHint() {
        return cureHint;
    }

    public String dangerHint() {
        return dangerHint;
    }

    public static final class Builder {

        private final String id;
        private final String label;
        private Color accent = Color.WHITE;
        private double cap = 100.0;
        private int stackCount;
        private final List<Contribution> contributions = new ArrayList<>();
        private final List<Multiplier> multipliers = new ArrayList<>();
        private final List<Cure> cures = new ArrayList<>();
        private Contagion contagion;
        private HealingConversion healingConversion;
        private MeterThreshold threshold;
        private double resetFraction;
        private double thresholdCooldownSeconds = 3.0;
        private double warnFraction = 0.6;
        private String cureHint = "";
        private String dangerHint = "";

        private Builder(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public Builder accent(Color accent) {
            this.accent = accent;
            return this;
        }

        /**
         * Full-meter value. 100 throughout the designs, and left configurable only so a server can
         * retune resolution — every rate below is expressed in the same units, so changing this
         * without changing the rates changes how long the whole clock takes.
         */
        public Builder cap(double cap) {
            this.cap = Math.max(1.0, cap);
            return this;
        }

        /**
         * Makes this skin read as {@code count} discrete stacks instead of a smooth fill — Void Echo is
         * "at 5 you're briefly banished", not "at 100%". The underlying number is still continuous;
         * this only changes the readout and enables {@link PlayerMeter#addStacks}.
         */
        public Builder stacks(int count) {
            this.stackCount = Math.max(0, count);
            return this;
        }

        /** A condition that fills the meter while it holds. Call once per contributing condition. */
        public Builder gain(MeterCondition when, double perSecond) {
            contributions.add(new Contribution(when, perSecond));
            return this;
        }

        /** A condition that scales whatever the contributions came to. Composes with other multipliers. */
        public Builder multiplier(MeterCondition when, double factor) {
            multipliers.add(new Multiplier(when, factor));
            return this;
        }

        /** The physical act that empties it. Multiple cures are allowed; the fastest matching one wins. */
        public Builder cure(MeterCondition when, double perSecond) {
            cures.add(new Cure(when, perSecond));
            return this;
        }

        public Builder contagion(double radius, double carrierFraction, double factorPerCarrier,
                                  double flatPerSecondPerCarrier, int maxCarriersCounted) {
            this.contagion = new Contagion(radius, carrierFraction, factorPerCarrier,
                    flatPerSecondPerCarrier, Math.max(1, maxCarriersCounted));
            return this;
        }

        /**
         * Arms {@link HealingConversion} for this skin — healing past the cap fills the meter instead
         * of the health bar. Leave it unset and the fight registers no healing hook at all.
         * <p>
         * The three numbers are authored defaults, not the final values: {@link MeterRegistry} re-reads
         * each of them from {@code bosses.<boss>.meter.<id>.heal-*} before arming the rule, so the
         * sharpest anti-healing knob in the roster can be retuned on a live server without a boss author
         * threading config through by hand.
         */
        public Builder convertHealing(double healthPerSecond, double burstSeconds, double meterPerHealth) {
            this.healingConversion = new HealingConversion(
                    Math.max(0.0, healthPerSecond),
                    // Floored rather than allowed to be zero: a zero-length window makes the bucket
                    // capacity zero, which silently converts every point of healing regardless of cap.
                    Math.max(0.05, burstSeconds),
                    Math.max(0.0, meterPerHealth));
            return this;
        }

        /** What happens at full, and where the meter sits afterwards. */
        public Builder threshold(MeterThreshold threshold, double resetFraction) {
            this.threshold = threshold;
            this.resetFraction = Math.max(0.0, Math.min(0.95, resetFraction));
            return this;
        }

        /**
         * Minimum gap between two threshold firings on the same player. Without one, a player who caps
         * while standing somewhere with a heavy contribution re-caps on the very next pulse and gets
         * chain-detonated by a mechanic they had no window to answer — which is the "unavoidable
         * damage" §0.2 rule 3 forbids.
         */
        public Builder thresholdCooldown(double seconds) {
            this.thresholdCooldownSeconds = Math.max(0.0, seconds);
            return this;
        }

        public Builder warnAt(double fraction) {
            this.warnFraction = Math.max(0.0, Math.min(1.0, fraction));
            return this;
        }

        /**
         * The two lines the readout bar shows: what to do about it, and what is currently doing it to
         * you. Kept as plain text on the spec so a boss author writes its voice in one place.
         */
        public Builder hints(String cureHint, String dangerHint) {
            this.cureHint = cureHint;
            this.dangerHint = dangerHint;
            return this;
        }

        public MeterSpec build() {
            return new MeterSpec(this);
        }
    }
}
