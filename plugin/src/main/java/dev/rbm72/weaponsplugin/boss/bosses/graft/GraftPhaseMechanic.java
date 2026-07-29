package dev.rbm72.weaponsplugin.boss.bosses.graft;

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
 * Shared body of all four Grafted Horror phases — same split as {@code StormPhaseMechanic}: the grafts
 * keep running underneath, and the phase on top adds one structural demand. Every phase exits on a
 * circuit objective as well as its health band (§1.6, "burst-skipping: every phase exits on circuit
 * objectives, never HP").
 * <p>
 * <b>It is never invulnerable.</b> Its body damage is simply modest (§1.6) and the modules are what kill
 * you, so there is no phase here that needs a damage wall at all — {@link #filterDamage} is left as the
 * identity for the whole fight, which is the honest expression of "you don't out-damage the Horror, you
 * disable it".
 */
abstract class GraftPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final GraftFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected GraftPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = GraftFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        GraftSupplies.dropFor(fight);
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.grafts().pulse(PULSE_TICKS);
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

    /**
     * Cuts are the fight's unit of progress, so the floor-timeout clock resets on every one of them —
     * a group patiently working the wires never reads as a stalled fight.
     */
    protected int progressSignal() {
        return fight.grafts().totalCuts();
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
                Component.text(label, NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Its wiring is beaten here — push it", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.4f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.DARK_GREEN)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.RED);
    }
}
