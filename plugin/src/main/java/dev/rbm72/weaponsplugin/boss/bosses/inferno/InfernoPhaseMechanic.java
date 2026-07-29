package dev.rbm72.weaponsplugin.boss.bosses.inferno;

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
 * Shared body of all four of the Inferno Warlord's phases — same split as {@code NecroPhaseMechanic}/
 * {@code StormPhaseMechanic}: the fight-scoped systems (cauldrons, lava, trails, clusters, magma hazards,
 * burning logs, Cinder Nova) keep pulsing every tick regardless of which phase is running, and the phase
 * on top adds one objective of its own by arming/disarming the subset it owns.
 * <p>
 * <b>Burning's own damage is applied here</b>, not by the meter itself (see {@code InfernoFight}'s
 * header for why it carries no threshold): every pulse, every combatant currently on fire pays a bite
 * proportional to how full their meter is — "steady damage that ignores most mitigation and can't be
 * outhealed cheaply" (§1.4), continuous rather than a single detonation.
 * <p>
 * He is hittable for every second of every phase. Nothing here touches {@code setDamageMultiplier} or
 * {@code setForcedInvulnerable} — what gates the group is a phase objective (a fuse cut, ground held,
 * clusters neutralised), never a permission to swing.
 */
abstract class InfernoPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final InfernoFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected InfernoPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = InfernoFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        InfernoSupplies.dropFor(fight);
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
    }

    @Override
    protected final void tick() {
        fight.cauldrons().pulse(PULSE_TICKS);
        fight.trails().pulse(PULSE_TICKS);
        fight.clusters().pulse(PULSE_TICKS);
        fight.magma().pulse(PULSE_TICKS);
        fight.logs().pulse(PULSE_TICKS);
        fight.cinderNova().pulse(PULSE_TICKS);
        applyBurningDamage(PULSE_TICKS);
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

    /** Every combatant currently on fire pays a bite scaled to how full the meter is — no single nuke. */
    private void applyBurningDamage(int intervalTicks) {
        double maxPerSecond = fight.config().dbl("burning-damage-per-second-at-max", 6.0);
        double stepSeconds = intervalTicks / 20.0;
        for (Player player : combatants()) {
            double fraction = fight.burning().fraction(player);
            if (fraction <= 0) {
                continue;
            }
            tickHurt(player, fraction * maxPerSecond * stepSeconds);
        }
    }

    // ------------------------------------------------------------ subclass hooks

    /** Arm this phase's own objective (and any subsystem's target counts it owns). */
    protected void onArm() {
    }

    /** Disarm this phase's own systems. Fight-scoped state (cauldrons, lava tiles) is not this method's business. */
    protected void onDisarm() {
    }

    /** One pulse of this phase's own objective, after the shared systems have ticked. */
    protected void onPulse(int intervalTicks) {
    }

    /** False for the final phase — see {@code NecroPhaseMechanic} for the reasoning this mirrors. */
    protected boolean announcesCompletion() {
        return true;
    }

    protected abstract boolean objectiveMet();

    /** Monotonic player-headway counter — see {@code NecroPhaseMechanic}. Zero is valid for an ungated phase. */
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
                Component.text("The forge answers — push him", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.7f);
        Fx.sound(centre, Sound.ENTITY_BLAZE_HURT, 1.0f, 0.8f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.GOLD)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.RED);
    }
}
