package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — Flood the Foundry</b> (§1.3, 72–48%). Rising lava arms here and keeps running through P3 —
 * "flowing from the arena edges inward... in visible tiers" is a fight-long clock once it starts, not a
 * one-phase gimmick, so {@link #onDisarm} deliberately leaves {@code fight.lava()} armed for the next
 * phase to inherit rather than stopping it.
 * <p>
 * Exit also requires the group to be standing on ground it made (§1.3). Read literally that would demand
 * every combatant on constructed tiles at the exact instant the floor is checked, which is nearly
 * impossible to synchronise for five people moving through a fight — so this reads it as a
 * <b>majority</b> of current combatants, latched permanently true the first pulse it is observed, the
 * same "achieved once, stays achieved" shape every other non-health exit in the roster uses. It is still
 * a genuine, positive proof that the group built and used real footing, not a participation trophy.
 * <p>
 * Solo (§1.5): majority of one is one — the same rule falls out for a solo player without a special case.
 */
final class FloodTheFoundryPhase extends InfernoPhaseMechanic {

    private boolean groundHeld;

    FloodTheFoundryPhase(BossInstance instance, double exitFraction) {
        super(instance, "Flood the Foundry", exitFraction);
    }

    @Override
    protected void onArm() {
        int players = fight.playerCount();
        fight.trails().arm(1 + players / 2);
        fight.clusters().disarm();
        fight.magma().arm(Math.max(1, 1 + players / 3));
        fight.lava().arm();
    }

    @Override
    protected void onDisarm() {
        fight.trails().disarm();
        // fight.lava() stays armed — the rise continues into P3.
    }

    @Override
    protected void onPulse(int intervalTicks) {
        if (groundHeld) {
            return;
        }
        int combatants = fight.combatants().size();
        if (combatants == 0) {
            return;
        }
        long onConstructed = fight.combatants().stream().filter(fight.lava()::standingOnPlayerMade).count();
        if (onConstructed * 2 >= combatants) {
            groundHeld = true;
        }
    }

    @Override
    protected boolean objectiveMet() {
        return groundHeld;
    }

    @Override
    protected int progressSignal() {
        return fight.lava().playerMadeCount();
    }

    @Override
    protected Component readoutText() {
        return Component.text(groundHeld ? "holding constructed ground" : "build and hold your own footing",
                groundHeld ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return groundHeld ? 1.0 : Math.min(0.9, fight.lava().playerMadeCount() / 6.0);
    }
}
