package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P3 — Coronation.</b> The Bane starts pulling the group's repeaters back out on a timer, forcing its
 * own clocks back toward alignment, and Convergences become frequent. An active tug-of-war over the
 * machinery: it re-tunes, you re-tune, and being caught at a loop when three heads strike together is
 * fatal (batch-3 §2.3).
 * <p>
 * Exit requires two Convergences broken by desync — alignments that were announced and then came apart
 * before they landed, because somebody changed a period in time. Surviving one behind cover does not
 * count here; the phase is asking the group to <em>prevent</em> them.
 */
final class CoronationPhase extends BanePhaseMechanic {

    private static final int REQUIRED = 2;

    private int baseline;

    CoronationPhase(BossInstance instance, double exitFraction) {
        super(instance, "Coronation", exitFraction);
    }

    @Override
    protected void onArm() {
        // Desyncs the group managed in earlier phases were free — this phase asks for two of its own.
        baseline = fight.clocks().desyncsBroken();
        fight.clocks().startRepairing();
    }

    private int broken() {
        return Math.max(0, fight.clocks().desyncsBroken() - baseline);
    }

    @Override
    protected boolean objectiveMet() {
        return broken() >= REQUIRED;
    }

    @Override
    protected Component readoutText() {
        if (broken() >= REQUIRED) {
            return Component.text("you are running its clocks now", NamedTextColor.GREEN);
        }
        if (fight.clocks().convergencePending()) {
            return Component.text("ALIGNING — break it or take cover ("
                    + broken() + "/" + REQUIRED + ")", NamedTextColor.RED);
        }
        return Component.text(broken() + "/" + REQUIRED
                + " alignments broken — it is re-tuning behind you", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) broken() / REQUIRED);
    }
}
