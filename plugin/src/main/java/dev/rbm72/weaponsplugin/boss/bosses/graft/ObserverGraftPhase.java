package dev.rbm72.weaponsplugin.boss.bosses.graft;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — Observer Graft.</b> It grafts on a real observer, facing a real arc, that fires when it sees
 * movement in front of it. The "don't move there" rule is enforced by the block's own facing rather than
 * by a script (§1.3), and the two earlier modules are still live throughout, so standing still to avoid
 * it is not free either.
 * <p>
 * Exit requires the observer circuit cut <em>while somebody is standing in its arc</em>. Cutting it from
 * behind while the group hides is the easy version and deliberately does not count — the phase is asking
 * the group to split around the thing, not to wait it out.
 */
final class ObserverGraftPhase extends GraftPhaseMechanic {

    ObserverGraftPhase(BossInstance instance, double exitFraction) {
        super(instance, "Observer Graft", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.grafts().arm(Grafts.Kind.DISPENSER);
        fight.grafts().arm(Grafts.Kind.PISTON);
        fight.grafts().arm(Grafts.Kind.OBSERVER);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.grafts().observerCutWhileWatching();
    }

    @Override
    protected Component readoutText() {
        if (fight.grafts().observerCutWhileWatching()) {
            return Component.text("blinded — it never saw that one coming", NamedTextColor.GREEN);
        }
        if (fight.grafts().severed(Grafts.Kind.OBSERVER)) {
            return Component.text("observer down, but nothing was in its arc — again, with bait",
                    NamedTextColor.YELLOW);
        }
        return Component.text("cut the observer line while it is watching someone", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return fight.grafts().observerCutWhileWatching() ? 1.0 : 0.0;
    }
}
