package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — Three Clocks.</b> Baseline: three loops, three periods, three note blocks, and the group
 * learning that each head has a sound before it has an attack. Clock tampering is introduced here and
 * costs nothing to try (batch-3 §2.3).
 * <p>
 * Exit requires one Convergence survived, on top of the health threshold — a group that burst through
 * this band without ever hearing all three strike together would arrive in P2 with no idea what the
 * warning means.
 */
final class ThreeClocksPhase extends BanePhaseMechanic {

    ThreeClocksPhase(BossInstance instance, double exitFraction) {
        super(instance, "Three Clocks", exitFraction);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.clocks().convergences() >= 1;
    }

    @Override
    protected Component readoutText() {
        if (fight.clocks().convergences() >= 1) {
            return Component.text("you've heard what alignment sounds like", NamedTextColor.GREEN);
        }
        if (fight.clocks().convergencePending()) {
            return Component.text("CONVERGENCE INCOMING — cover", NamedTextColor.RED);
        }
        return Component.text(String.format("beats at %.1fs / %.1fs — learn them",
                fight.clocks().fastestSeconds(), fight.clocks().slowestSeconds()), NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return fight.clocks().convergences() >= 1 ? 1.0 : 0.0;
    }
}
