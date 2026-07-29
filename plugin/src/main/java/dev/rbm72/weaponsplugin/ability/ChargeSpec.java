package dev.rbm72.weaponsplugin.ability;

import org.bukkit.Color;

/**
 * What a weapon has to do to earn its ultimate, for a weapon that would rather be earned than waited out.
 * <p>
 * Every ultimate in the roster is currently gated on a 40-75 second timer, which makes the biggest button
 * on a weapon the one the player has least to do with: it fires when the clock says so, not when the fight
 * has gone a particular way. A charge meter inverts that — the ultimate arrives because of what the player
 * did, so a fight that went well produces it early and a fight spent running away does not produce it at
 * all.
 * <p>
 * Shaped deliberately as the mirror of {@link dev.rbm72.weaponsplugin.boss.meter.MeterSpec}, and not built
 * on it. That class is the roster's punishment clock: it has no passive decay <em>on purpose</em>, because
 * "only the boss's cure act brings it down" is its load-bearing property. This one is a reward clock and
 * needs the opposite defaults — it fills from player action and drains when the player disengages. Sharing
 * one class between the two would have meant a boolean deciding which of two opposed philosophies applied,
 * and a passive-decay knob visible from the boss side is exactly the setting that would quietly undo the
 * anti-facetank design.
 * <p>
 * Immutable. One instance per weapon, built in its constructor and handed to
 * {@link UltimateChargeManager}.
 *
 * @param label            what the readout calls it — "Wrath", "Momentum", "Tide"
 * @param accent           readout colour, normally the weapon's rarity colour
 * @param cap              charge units for a full meter. 100 throughout, so every rate below reads as a percentage
 * @param perMeleeHit      units for landing a melee hit with this weapon — rewards staying in range
 * @param perDamageDealt   units per point of damage this weapon's abilities deal, so a big hit on a boss is
 *                         worth more than a big hit count on chickens
 * @param perDamageTaken   units per point of damage taken while holding it — the knob that lets a defensive
 *                         weapon charge by enduring rather than by dealing
 * @param perKill          units for a kill, on top of the hit that caused it
 * @param perAbilityCast   units for casting ability1/2/3, so the ultimate is the top of a combo rather than
 *                         a separate resource
 * @param decayPerSecond   units lost per second while out of combat — what stops a charge meter from being a
 *                         cooldown you can pre-bank between fights
 * @param decayGraceSeconds how long after the last contribution the decay waits, so ordinary gaps between
 *                         swings do not drain the bar
 * @param cooldownFloorSeconds a minimum gap between ultimates even at full charge. Without it, a weapon whose
 *                         ultimate itself generates charge can chain two back to back
 */
public record ChargeSpec(String label, Color accent, double cap,
                         double perMeleeHit, double perDamageDealt, double perDamageTaken,
                         double perKill, double perAbilityCast,
                         double decayPerSecond, double decayGraceSeconds,
                         double cooldownFloorSeconds) {

    public static Builder builder(String label) {
        return new Builder(label);
    }

    /** Fraction of a full meter, clamped to [0,1] — what the readout draws and what the gate compares. */
    public double fractionOf(double charge) {
        return Math.max(0.0, Math.min(1.0, charge / Math.max(1.0, cap)));
    }

    public static final class Builder {

        private final String label;
        private Color accent = Color.WHITE;
        private double cap = 100.0;
        private double perMeleeHit;
        private double perDamageDealt;
        private double perDamageTaken;
        private double perKill;
        private double perAbilityCast;
        private double decayPerSecond = 1.5;
        private double decayGraceSeconds = 6.0;
        private double cooldownFloorSeconds = 8.0;

        private Builder(String label) {
            this.label = label;
        }

        public Builder accent(Color accent) {
            this.accent = accent;
            return this;
        }

        public Builder cap(double cap) {
            this.cap = Math.max(1.0, cap);
            return this;
        }

        public Builder perMeleeHit(double units) {
            this.perMeleeHit = units;
            return this;
        }

        public Builder perDamageDealt(double unitsPerPoint) {
            this.perDamageDealt = unitsPerPoint;
            return this;
        }

        public Builder perDamageTaken(double unitsPerPoint) {
            this.perDamageTaken = unitsPerPoint;
            return this;
        }

        public Builder perKill(double units) {
            this.perKill = units;
            return this;
        }

        public Builder perAbilityCast(double units) {
            this.perAbilityCast = units;
            return this;
        }

        /** Out-of-combat drain, and how long it holds off. Zero rate makes the meter bankable — rarely what you want. */
        public Builder decay(double perSecond, double graceSeconds) {
            this.decayPerSecond = Math.max(0.0, perSecond);
            this.decayGraceSeconds = Math.max(0.0, graceSeconds);
            return this;
        }

        public Builder cooldownFloor(double seconds) {
            this.cooldownFloorSeconds = Math.max(0.0, seconds);
            return this;
        }

        public ChargeSpec build() {
            return new ChargeSpec(label, accent, cap, perMeleeHit, perDamageDealt, perDamageTaken,
                    perKill, perAbilityCast, decayPerSecond, decayGraceSeconds, cooldownFloorSeconds);
        }
    }
}
