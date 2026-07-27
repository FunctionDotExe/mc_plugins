package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P4 — Army of the Dead.</b> He commits everything and steps out of the back line himself.
 * <p>
 * This is the payoff phase and it is deliberately not a new puzzle. The sun is up for good, so his summons
 * melt about as fast as they arrive, and he has stopped retreating — the one thing the group could not do
 * for three phases, walk up and hit him, is now simply available. What decides the phase is what the group
 * left behind: the corpse floor from P3 is still physically there, and a group that never cleaned it is
 * holding a chokepoint made of its own dead.
 * <p>
 * The objective is therefore satisfied the instant the phase starts. That is not a missing exit condition —
 * it is the last phase, there is nothing to advance to, and the framework's health floor has to be released
 * or the boss could not be finished at all.
 */
public final class ArmyPhase extends NecroPhaseMechanic {

    public ArmyPhase(BossInstance instance) {
        super(instance, "Army of the Dead", 0.0);
    }

    @Override
    protected void onArm() {
        fight.letTheSunIn();
        if (fight.needsArtificialSun()) {
            fight.horde().scorch();
        }
        // He leaves the back line. Nothing about his damage taken changes here, because nothing about it
        // ever changed — the previous three phases kept him away, not immune.
        fight.horde().stepIntoTheFight();
    }

    @Override
    protected boolean objectiveMet() {
        return true;
    }

    @Override
    protected boolean announcesCompletion() {
        return false;
    }

    @Override
    protected Component readoutText() {
        return Component.text("he is in reach — the sun has his army", NamedTextColor.GOLD);
    }

    @Override
    protected double readoutProgress() {
        return 1.0 - healthFraction();
    }
}
