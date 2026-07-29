package dev.rbm72.weaponsplugin.boss.bosses.graft;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — Two Grafts.</b> Baseline: a dispenser graft firing real volleys and a piston graft shoving
 * people off their footing, each on its own visible line to a power source at the arena edge.
 * <p>
 * Exit requires <em>both</em> starting circuits cut at least once, on top of the health threshold
 * (§1.3) — the first cut teaches the whole fight, and a group that out-damaged this band without ever
 * touching a wire would arrive in P2 with no idea what the wires are for.
 */
final class TwoGraftsPhase extends GraftPhaseMechanic {

    TwoGraftsPhase(BossInstance instance, double exitFraction) {
        super(instance, "Two Grafts", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.grafts().arm(Grafts.Kind.DISPENSER);
        fight.grafts().arm(Grafts.Kind.PISTON);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.grafts().cutsOf(Grafts.Kind.DISPENSER) > 0
                && fight.grafts().cutsOf(Grafts.Kind.PISTON) > 0;
    }

    @Override
    protected Component readoutText() {
        int cut = (fight.grafts().cutsOf(Grafts.Kind.DISPENSER) > 0 ? 1 : 0)
                + (fight.grafts().cutsOf(Grafts.Kind.PISTON) > 0 ? 1 : 0);
        if (cut >= 2) {
            return Component.text("both circuits cut — keep them down", NamedTextColor.GREEN);
        }
        return Component.text(cut + "/2 circuits cut — follow the wire, break the wire",
                NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        int cut = (fight.grafts().cutsOf(Grafts.Kind.DISPENSER) > 0 ? 1 : 0)
                + (fight.grafts().cutsOf(Grafts.Kind.PISTON) > 0 ? 1 : 0);
        return cut / 2.0;
    }
}
