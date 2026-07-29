package dev.rbm72.weaponsplugin.boss.bosses.choir;

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
 * Shared body of all four Hollow Choir phases. Two things run underneath every one of them: the noise
 * model ages its memory, and the boss's target is re-pointed at whoever is nearest the last sound it
 * heard. That second line is the entire boss — it is why hitting the Choir makes you its next target and
 * why a struck note block across the arena pulls it off you (batch-3 §4.2).
 * <p>
 * <b>It is never invulnerable.</b> P3's ward is broken by playing a phrase, not by a damage gate: while
 * the ward holds the Choir simply keeps hunting, and the group can keep hitting it the whole time.
 */
abstract class ChoirPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final ChoirFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected ChoirPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = ChoirFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        ChoirSupplies.dropFor(fight);
        fight.noise().arm();
        fight.attacks().arm();
        fight.instruments().build();
        // The blind-targeting rule, installed once per phase and released in onStop by the shared
        // TickingMechanic contract.
        instance.setTargetOverride(() -> fight.noise().hunted());
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.noise().pulse(PULSE_TICKS);
        fight.instruments().pulse();
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

    // ------------------------------------------------------------ subclass hooks

    protected void onArm() {
    }

    protected void onDisarm() {
    }

    protected void onPulse(int intervalTicks) {
    }

    protected boolean announcesCompletion() {
        return true;
    }

    protected abstract boolean objectiveMet();

    /** Misdirections and phrases are what progress looks like on a boss that is fought with sound. */
    protected int progressSignal() {
        return fight.attacks().misdirected() * 10 + fight.phrase().completed() * 20;
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
                Component.text(label, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("It has lost your voice — push it", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 1.6f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.LIGHT_PURPLE)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.PURPLE);
    }
}
