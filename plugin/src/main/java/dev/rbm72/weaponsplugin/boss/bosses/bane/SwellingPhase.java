package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — Swelling.</b> It grows, its reach grows, and the quiet gaps between beats physically shrink
 * because more of the arena is occupied by boss. Clock sabotage stops being optional here (batch-3
 * §2.3): somebody has to leave the fight, walk to a loop and lengthen it while the others hold tempo.
 * <p>
 * Exit requires one clock running slower than it started — a repeater added to a loop and still in it.
 * See {@code Clocks}' header for why adding rather than pulling is the slowing move.
 */
final class SwellingPhase extends BanePhaseMechanic {

    SwellingPhase(BossInstance instance, double exitFraction) {
        super(instance, "Swelling", exitFraction);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.clocks().anySlowed();
    }

    @Override
    protected Component readoutText() {
        if (fight.clocks().anySlowed()) {
            return Component.text(String.format("slowest head at %.1fs — keep it there",
                    fight.clocks().slowestSeconds()), NamedTextColor.GREEN);
        }
        if (fight.clocks().convergencePending()) {
            return Component.text("CONVERGENCE INCOMING — cover", NamedTextColor.RED);
        }
        return Component.text("add a repeater to a loop to slow that head", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return fight.clocks().anySlowed() ? 1.0 : 0.0;
    }
}
