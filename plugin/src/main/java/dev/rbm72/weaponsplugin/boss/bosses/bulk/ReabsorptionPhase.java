package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P3 — Reabsorption.</b> The restraint band tightens from both sides. Adds now <em>must</em> die,
 * because the Bulk is reaching out and pulling them home to heal — but they still must not die near a
 * catalyst. The phase is the fight's whole thesis under a timer (batch-3 §3.3).
 * <p>
 * Exit requires three reabsorption attempts denied: kill the targeted Bulkling inside the pull, three
 * times. One target at a time, so this is the same ask at every group size.
 */
final class ReabsorptionPhase extends BulkPhaseMechanic {

    ReabsorptionPhase(BossInstance instance, double exitFraction) {
        super(instance, "Reabsorption", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.catalysts().place();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.bulklings().pulseShedding(intervalTicks);
        fight.bulklings().pulseReabsorption(intervalTicks);
    }

    private int required() {
        return fight.config().num("reabsorption-denials", 3);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.bulklings().reabsorbDenied() >= required();
    }

    @Override
    protected Component readoutText() {
        int denied = fight.bulklings().reabsorbDenied();
        if (denied >= required()) {
            return Component.text("it gets nothing back — " + denied + " pulls denied", NamedTextColor.GREEN);
        }
        if (fight.bulklings().reabsorbing()) {
            return Component.text("PULLING — kill the marked one (" + denied + "/" + required() + ")",
                    NamedTextColor.YELLOW);
        }
        return Component.text(denied + "/" + required() + " pulls denied — "
                + fight.bulklings().reabsorbCompleted() + " got home", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) fight.bulklings().reabsorbDenied() / required());
    }
}
