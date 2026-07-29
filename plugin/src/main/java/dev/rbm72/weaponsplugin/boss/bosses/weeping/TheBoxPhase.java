package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * <b>P4 — The Box.</b> The walls stop where the group left them. The Colossus is tiny and extremely fast,
 * ricocheting around a chamber full of fallen dripstone and standing water — a pure close-quarters duel
 * in terrain the fight itself created (batch-3 §5.3).
 * <p>
 * How small the box is, is the consequence: a group that jammed well fights in a room, a group that never
 * touched the walls fights in a cupboard. It is never smaller than {@code PistonWalls#minChamber()},
 * which is §5.8's winnability floor and the reason this phase can never be unwinnable.
 * <p>
 * Objective is satisfied the instant the phase starts, like every roster boss's final phase.
 */
final class TheBoxPhase extends WeepingPhaseMechanic {

    TheBoxPhase(BossInstance instance) {
        super(instance, "The Box", 0.0);
    }

    @Override
    protected void onArm() {
        fight.walls().halt();
        instance.showTitle(
                Component.text("☁ THE BOX ☁", NamedTextColor.DARK_BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("The walls stop here — finish it in the room you kept", NamedTextColor.GRAY));
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.dripstone().pulse(intervalTicks);
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
        int radius = fight.walls().chamberRadius();
        return Component.text(radius + " blocks of room, " + fight.dripstone().obstructions()
                + " spikes in it", radius > fight.walls().minChamber() + 3
                        ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        double start = fight.instance().arena().radius()
                * fight.config().dbl("wall-start-fraction", 0.85);
        return Math.max(0.0, Math.min(1.0, fight.walls().chamberRadius() / Math.max(1.0, start)));
    }
}
