package dev.rbm72.weaponsplugin.boss.bosses.storm;

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
 * Shared body of all four of the Storm Tyrant's phases — same split as {@code KingPhaseMechanic}/
 * {@code FrostPhaseMechanic}: the rods keep sparking, the pylons keep standing until struck down, and
 * the phase on top adds one structural demand of its own. None of his phases end on health alone
 * (batch-1 §3.3 gives each an explicit survival/discharge condition beyond the threshold).
 * <p>
 * <b>He is never invulnerable, in any phase.</b> P3's answer to "gated while you deal with an
 * objective" is a {@link #filterDamage} rule (only discharged players land hits while a pylon stands),
 * not a global multiplier — the memory note on the anti-pattern this rework closes everywhere else.
 */
abstract class StormPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final StormFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected StormPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = StormFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        StormSupplies.dropFor(fight);
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.rods().pulse();
        fight.pylons().pulse();
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
        return damage;
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
                Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("His hold here is broken — push him", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.YELLOW)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.YELLOW);
    }
}
