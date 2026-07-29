package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

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
 * Shared body of all four of the Void Sovereign's phases — same split as every other rework in this
 * batch: the Echo trail keeps striking, rifts and pistons keep existing once opened/built, and the
 * phase on top adds one structural demand of its own. None of his phases end on health alone.
 * <p>
 * <b>Nothing here is ever invulnerable.</b> P3's identification puzzle ({@code BetweenPhase}) works
 * entirely because damage only ever lands on the real {@code instance.entity()} through the normal
 * pipeline — there is no gate, no filter and no forced-invulnerability flag anywhere in this package.
 */
abstract class SovereignPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final SovereignFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected SovereignPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = SovereignFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        SovereignSupplies.dropFor(fight);
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.echoTrail().pulse();
        fight.pistons().pulse(PULSE_TICKS);
        fight.enderPearls().pulse(PULSE_TICKS);
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
                Component.text(label, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("His hold here is broken — push him", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 1.2f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.DARK_PURPLE)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.PURPLE);
    }
}
