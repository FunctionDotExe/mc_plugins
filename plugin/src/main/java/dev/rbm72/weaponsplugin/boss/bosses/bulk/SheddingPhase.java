package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — Shedding.</b> Baseline: catalyst nodes are placed, Bulklings shed continuously, and the first
 * add killed close in blooms sculk across the floor where everybody can watch it happen. That mistake is
 * the tutorial (batch-3 §3.3) and it is deliberately unmissable.
 * <p>
 * Exit requires five Bulklings killed <em>outside</em> catalyst range. Kills inside count for nothing
 * toward the exit and cost the group floor, which is the entire lesson stated as an exit condition.
 */
final class SheddingPhase extends BulkPhaseMechanic {

    SheddingPhase(BossInstance instance, double exitFraction) {
        super(instance, "Shedding", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.catalysts().place();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.bulklings().pulseShedding(intervalTicks);
    }

    private int required() {
        return fight.config().num("shedding-clean-kills", 5);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.bulklings().cleanKills() >= required();
    }

    @Override
    protected Component readoutText() {
        int clean = fight.bulklings().cleanKills();
        if (clean >= required()) {
            return Component.text("pulled and killed clean — it is not growing off you", NamedTextColor.GREEN);
        }
        if (fight.bulklings().fedKills() > 0) {
            return Component.text(clean + "/" + required() + " clean kills — "
                    + fight.bulklings().fedKills() + " fed it. Pull them out first.", NamedTextColor.RED);
        }
        return Component.text(clean + "/" + required() + " killed outside the nodes", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) fight.bulklings().cleanKills() / required());
    }
}
