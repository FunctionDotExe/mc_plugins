package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * <b>P4 — Stormcall.</b> He lands, permanently charged — Charge climbs roughly twice as fast — and every
 * rod now burns out the instant somebody actually finishes a discharge on it, rather than standing ready
 * for the next person. Batch-1 §3.3: "a real coordination endgame that is about turn order, not damage".
 * <p>
 * Objective is satisfied the instant the phase starts, exactly like every roster boss's final phase —
 * there is no next band to pin a seam against.
 */
final class StormcallPhase extends StormPhaseMechanic {

    private static final int CONTACT_TICKS_TO_BURN_OUT = 30;

    private final Map<Block, Integer> contactTicks = new HashMap<>();

    StormcallPhase(BossInstance instance) {
        super(instance, "Stormcall", 0.0);
    }

    @Override
    protected void onArm() {
        fight.rods().raiseAll();
        fight.charge().setRateScale(2.0);
        instance.showTitle(
                Component.text("⚡ STORMCALL ⚡", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("Every rod burns out after one use — sequence your turns", NamedTextColor.GRAY));
    }

    @Override
    protected void onPulse(int intervalTicks) {
        for (Block rod : fight.rods().liveBlocks()) {
            boolean touched = !Arena.combatants(rod.getLocation().add(0.5, 0.5, 0.5), 1.8).isEmpty();
            if (!touched) {
                contactTicks.remove(rod);
                continue;
            }
            int ticks = contactTicks.merge(rod, intervalTicks, Integer::sum);
            if (ticks >= CONTACT_TICKS_TO_BURN_OUT) {
                fight.rods().burnOut(rod);
                contactTicks.remove(rod);
            }
        }
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
        return Component.text(fight.rods().liveCount() + "/" + fight.rods().total()
                + " rods left — sequence who discharges", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        int total = Math.max(1, fight.rods().total());
        return (double) fight.rods().liveCount() / total;
    }
}
