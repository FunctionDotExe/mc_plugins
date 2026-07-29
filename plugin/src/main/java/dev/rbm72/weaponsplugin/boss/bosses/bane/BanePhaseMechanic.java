package dev.rbm72.weaponsplugin.boss.bosses.bane;

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
 * Shared body of all four Threefold Bane phases: the three clocks keep running underneath every one of
 * them, and the phase on top adds a demand. Every phase exits on a rhythm objective as well as its
 * health band — survive one, slow one, break two (§2.6).
 * <p>
 * <b>It is never invulnerable.</b> Convergence is the fight's wall, and it is a wall made of timing and
 * geometry rather than of damage permission, so {@code filterDamage} stays the identity throughout.
 * <p>
 * Note the pulse interval: 2 ticks rather than the roster's usual 5. A tempo boss cannot be quantised to
 * a quarter-second — a Convergence detector running at 5-tick resolution would miss alignments the
 * players can plainly hear, and §2.8 forbids exactly that kind of divergence between machinery and truth.
 */
abstract class BanePhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 2;

    protected final BaneFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected BanePhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = BaneFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        BaneSupplies.dropFor(fight);
        fight.clocks().build();
        fight.pillars().raise();
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.clocks().pulse(PULSE_TICKS);
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

    /** Engineering the clocks and breaking alignments are what progress looks like on a tempo boss. */
    protected int progressSignal() {
        return fight.clocks().totalRepeaters() * 5
                + fight.clocks().desyncsBroken() * 10
                + fight.clocks().convergences();
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
                Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("You have its measure here — push it", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 1.4f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.AQUA)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                fight.clocks().convergencePending() ? BossBar.Color.RED
                        : objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.BLUE);
    }
}
