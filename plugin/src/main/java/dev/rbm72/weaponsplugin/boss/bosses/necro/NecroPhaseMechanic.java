package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.mechanics.TickingMechanic;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

/**
 * Shared body of all four of the Overlord's phases: the army keeps arriving, the graves keep feeding it,
 * the corpses keep getting back up, and the phase on top of that adds one objective of its own.
 * <p>
 * <b>Why no phase ever ends on health alone.</b> The framework offers two levers and this uses both,
 * because either on its own is wrong here:
 * <ul>
 *   <li>{@link BossInstance#recordExposure()} is called the instant the phase's objective is met, which
 *       is what releases {@code clampToPhaseFloor}'s pin on the health seam. Until then the boss's health
 *       cannot cross into the next band no matter how much damage the group pushes — burst cannot skip a
 *       mechanic that has not been played.</li>
 *   <li>{@link #readyToAdvance()} deliberately requires the objective <em>and</em> the health threshold,
 *       rather than the objective alone. Returning true on the objective by itself would advance the
 *       phase early and turn "HP and resolution" into "HP or resolution" — the group would skip the rest
 *       of the band as a reward for doing the mechanic promptly, which is backwards.</li>
 * </ul>
 * Neither the boss's damage multiplier nor {@code filterDamage} is touched by any of this. He is
 * hittable for every second of the fight; what gates the group is where he is standing and what his army
 * is doing to the floor.
 */
public abstract class NecroPhaseMechanic extends TickingMechanic {

    /** Pulse resolution. Fast enough that a kill's death animation is never missed by the corpse poller. */
    private static final int PULSE_TICKS = 5;

    protected final NecroFight fight;

    private final String label;
    /** Health fraction the band below this phase starts at; the final phase passes 0. */
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected NecroPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = NecroFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        // §0.3: the arena provides every item this boss's counterplay leans on, replenished per phase.
        ArenaSupplies.dropFor(fight);
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
    }

    @Override
    protected final void tick() {
        fight.horde().pulse(PULSE_TICKS);
        fight.corpses().pulse(PULSE_TICKS, corpseRiseMultiplier());
        fight.graves().pulse(PULSE_TICKS, graveTarget());
        onPulse(PULSE_TICKS);

        // Partial headway keeps the framework's floor-lock valve from handing the phase over. Every one
        // of this boss's objectives is minute-scale block work under pressure, which is exactly what the
        // valve's original 45-second patience could not tell apart from an unreachable weak point: all
        // four phases used to time out while their objectives were still standing, and a group that
        // ignored the mechanics entirely got there faster than one playing them.
        int signal = progressSignal();
        if (signal > lastProgressSignal) {
            lastProgressSignal = signal;
            instance.recordProgress();
        }

        if (!objectiveRecorded && objectiveMet()) {
            objectiveRecorded = true;
            instance.recordExposure();
            if (announcesCompletion()) {
                announceObjective();
            }
        }
        showBar();
    }

    @Override
    public final boolean readyToAdvance() {
        return objectiveMet() && healthFraction() <= exitFraction;
    }

    // ------------------------------------------------------------ subclass hooks

    /** Arm this phase's own objective. */
    protected void onArm() {
    }

    /** Release this phase's own props. The fight-scoped horde/corpses/graves are not this method's business. */
    protected void onDisarm() {
    }

    /** One pulse of this phase's own objective, after the army has been ticked. */
    protected void onPulse(int intervalTicks) {
    }

    /**
     * Whether meeting the objective gets its own title. False for the final phase, whose objective is
     * satisfied the moment it starts — a "you did it" banner landing on top of the enrage cinematic reads
     * as a bug rather than a beat.
     */
    protected boolean announcesCompletion() {
        return true;
    }

    /** True once this phase's non-health exit condition has been satisfied. */
    protected abstract boolean objectiveMet();

    /**
     * A count of player headway on this phase's objective that only ever rises — graves broken, anchors
     * cut, piles mined out. Any increase tells the framework the group is engaging, which resets the
     * floor-lock's patience.
     * <p>
     * Must be monotonic and must count <em>player</em> action only. A signal that also moves when the
     * boss does his own job (piles leaving the floor because they stood up, anchors vanishing because he
     * re-knit the canopy) would keep the valve topped up during a fight nobody is actually solving,
     * which is the bug this exists to close. Zero is a valid answer for a phase with nothing to gate.
     */
    protected int progressSignal() {
        return 0;
    }

    /** What the mechanic bar should say about this phase's objective right now. */
    protected abstract Component readoutText();

    /** How far along that objective is, 0 to 1, for the bar's fill. */
    protected abstract double readoutProgress();

    /** Scalar on the corpse-pile rise clock — P3 is the phase that turns this up. */
    protected double corpseRiseMultiplier() {
        return 1.0;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Live grave markers this phase wants standing: one at a time solo, then one more per two extra
     * players, capped. Count scaling, exactly as the design specifies — a marker never spawns undead any
     * faster or hits any harder for a bigger group, there are simply more of them to break.
     */
    protected int graveTarget() {
        int players = fight.playerCount();
        if (players <= 1) {
            return 1;
        }
        return Math.min(fight.config().num("grave-max-live", 3), 1 + (players - 1) / 2);
    }

    protected double healthFraction() {
        AttributeInstance max = instance.entity().getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 0.0;
        if (maxHealth <= 0.0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, instance.entity().getHealth() / maxHealth));
    }

    private void announceObjective() {
        Location centre = instance.arena().center();
        instance.showTitle(
                Component.text(label, NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("His hold here is broken — push him", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_BELL_RESONATE, 1.0f, 1.2f);
        Fx.sound(centre, Sound.ENTITY_WITHER_HURT, 1.0f, 1.4f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.DARK_GREEN)
                .append(readoutText())
                .append(Component.text("   horde " + fight.horde().aliveCount(), NamedTextColor.GRAY));
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.RED);
    }
}
