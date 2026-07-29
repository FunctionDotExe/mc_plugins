package dev.rbm72.weaponsplugin.boss.bosses.graft;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;

/**
 * The only public surface of the Grafted Horror's mechanics: four factory methods, one per phase. Every
 * collaborating object underneath — the grafts, their circuits, the supplies — is package-private, same
 * discipline as {@code StormPhases}.
 */
public final class GraftPhases {

    private GraftPhases() {
    }

    /** P1 — Two Grafts. Exits once both starting circuits have been cut at least once. */
    public static PhaseMechanic twoGrafts(BossInstance instance, double exitFraction) {
        return new TwoGraftsPhase(instance, exitFraction);
    }

    /** P2 — Observer Graft. Exits on the observer line cut while it is actively watching someone. */
    public static PhaseMechanic observerGraft(BossInstance instance, double exitFraction) {
        return new ObserverGraftPhase(instance, exitFraction);
    }

    /** P3 — Self-Repair. Exits on three circuits cut inside one repair window. */
    public static PhaseMechanic selfRepair(BossInstance instance, double exitFraction) {
        return new SelfRepairPhase(instance, exitFraction);
    }

    /** P4 — Seams Open. Surviving modules detach into destructible floor turrets. */
    public static PhaseMechanic seamsOpen(BossInstance instance) {
        return new SeamsOpenPhase(instance);
    }
}
