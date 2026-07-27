package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — Contagion.</b> Baseline: the Infection meter and pyres are live, and Bloated Carriers walk in
 * — kill them away from the group, and step into the burst only if you want the cleanse trade.
 * <p>
 * Exit requires nobody standing above half Infection at the threshold, on top of the health seam —
 * batch-1 §4.3: a group that lets the meter run away should not get to bulldoze past the phase built to
 * teach spacing and pyre discipline.
 */
final class ContagionPhase extends PlaguePhaseMechanic {

    ContagionPhase(BossInstance instance, double exitFraction) {
        super(instance, "Contagion", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.carriers().spawn();
    }

    @Override
    protected boolean objectiveMet() {
        return fight.infection().above(0.5).isEmpty();
    }

    @Override
    protected int progressSignal() {
        return Math.max(0, fight.playerCount() - fight.infection().above(0.5).size());
    }

    @Override
    protected Component readoutText() {
        int over = fight.infection().above(0.5).size();
        return over == 0
                ? Component.text("everyone is under control", NamedTextColor.GREEN)
                : Component.text(over + " above half Infection — cleanse up", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) progressSignal() / Math.max(1, fight.playerCount()));
    }
}
