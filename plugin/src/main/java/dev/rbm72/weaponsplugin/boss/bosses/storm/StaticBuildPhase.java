package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <b>P1 — Static Build.</b> Baseline: the Static Charge meter is live, four lightning rods stand, and
 * players are learning the rhythm of pressuring him and breaking off to discharge.
 * <p>
 * Exit requires every combatant to have discharged at least once, on top of the health threshold —
 * batch-1 §3.3: a group that never touches a rod should not get to skip the phase that teaches them.
 */
final class StaticBuildPhase extends StormPhaseMechanic {

    private final Set<UUID> everCharged = new HashSet<>();
    private final Set<UUID> everDischarged = new HashSet<>();

    StaticBuildPhase(BossInstance instance, double exitFraction) {
        super(instance, "Static Build", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.rods().raiseAll();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        for (var player : fight.combatants()) {
            double fraction = fight.charge().fraction(player);
            if (fraction > 0.15) {
                everCharged.add(player.getUniqueId());
            } else if (everCharged.contains(player.getUniqueId())) {
                everDischarged.add(player.getUniqueId());
            }
        }
    }

    @Override
    protected boolean objectiveMet() {
        return !everDischarged.isEmpty() && everDischarged.size() >= fight.playerCount();
    }

    @Override
    protected int progressSignal() {
        return everDischarged.size();
    }

    @Override
    protected Component readoutText() {
        return Component.text(everDischarged.size() + "/" + fight.playerCount()
                + " have discharged — touch a rod", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) everDischarged.size() / Math.max(1, fight.playerCount()));
    }
}
