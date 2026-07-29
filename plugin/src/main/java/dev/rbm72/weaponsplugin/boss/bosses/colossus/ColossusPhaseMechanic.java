package dev.rbm72.weaponsplugin.boss.bosses.colossus;

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
import org.bukkit.entity.Player;

/**
 * Shared body of all four of the Colossus's phases — same split as {@code NecroPhaseMechanic}/
 * {@code StormPhaseMechanic}: the joints keep tracking the body every pulse regardless of which phase
 * is running, and the phase on top adds one physical objective of its own.
 * <p>
 * <b>The joint-armour rule lives here, once, as arithmetic rather than a wall.</b> Batch-2 §2.4's
 * "Joint armour: hits anywhere but a joint barely register" is the single rule every phase inherits by
 * default: a hit that arrived by way of {@link Joints}'s forwarding (see that class's header for why a
 * real per-joint entity is what makes "where the hit landed" answerable at all) passes through at full
 * value, and anything else — a swing that connected with the Colossus's own hitbox directly — is cut to
 * {@code body-hit-fraction}. Nothing here calls {@code setDamageMultiplier} or
 * {@code setForcedInvulnerable}; per the CRITICAL design note this rework is built around, the Colossus
 * must never be a wall the group waits out, only a target whose weak points matter. {@code
 * SolarChargePhase} is the one deliberate exception — batch-2 §2.3 is explicit that P3 "stays fully
 * damageable throughout" and the beacons "must never be implemented as" a gate, so it overrides
 * {@link #filterDamage} to pass every hit through untouched.
 */
abstract class ColossusPhaseMechanic extends TickingMechanic {

    /** Pulse resolution — fast enough that a joint breaking or a beacon flipping is never missed for long. */
    private static final int PULSE_TICKS = 4;

    protected final ColossusFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected ColossusPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = ColossusFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
    }

    @Override
    protected final void tick() {
        fight.joints().reposition();
        onPulse(PULSE_TICKS);

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

    @Override
    public double filterDamage(Player attacker, double damage) {
        if (fight.joints().isForwarding()) {
            return damage;
        }
        return damage * fight.config().dbl("body-hit-fraction", 0.04);
    }

    // ------------------------------------------------------------ subclass hooks

    protected void onArm() {
    }

    protected void onDisarm() {
    }

    protected void onPulse(int intervalTicks) {
    }

    /** False for the final phase, whose objective is satisfied the instant it starts — see {@code CollapsePhase}. */
    protected boolean announcesCompletion() {
        return true;
    }

    protected abstract boolean objectiveMet();

    protected int progressSignal() {
        return 0;
    }

    protected abstract Component readoutText();

    protected abstract double readoutProgress();

    // ---------------------------------------------------------------- helpers

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
                Component.text(label, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Its hold here is broken — push it", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.3f);
        Fx.sound(centre, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.6f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.YELLOW)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.YELLOW);
    }
}
