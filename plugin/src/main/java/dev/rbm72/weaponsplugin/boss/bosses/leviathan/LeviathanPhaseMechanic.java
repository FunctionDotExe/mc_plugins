package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

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
 * Shared body of the Leviathan's three submerged phases (P1-P3; P4 "Low Tide" is deliberately ungated —
 * see {@link LowTidePhase}, which is not one of these): the water keeps rising or churning, the conduit
 * network keeps ticking, the columns keep appearing, the whirlpool keeps pulling if it's live, and the
 * breath system keeps granting conduit power — and the phase on top of that adds one objective of its
 * own. Same split as {@code NecroPhaseMechanic}/{@code StormPhaseMechanic}: {@link
 * BossInstance#recordExposure()} the instant the objective is met (releases the phase-floor pin), {@link
 * #readyToAdvance()} requiring the objective <em>and</em> the health threshold together (never either
 * alone — see that pair's own javadoc for why "or" would be wrong), and {@link
 * BossInstance#recordProgress()} on any partial headway so the floor-lock timeout valve never fires on a
 * fight that is actually working the mechanic.
 * <p>
 * <b>He is never gated by an invulnerability wall.</b> No phase here touches the damage multiplier
 * beyond {@code TickingMechanic}'s own reset to 1.0 on start. What gates the group is the water itself —
 * drowning, the whirlpool's pull, a lost conduit — never a shield on the boss.
 */
abstract class LeviathanPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final LeviathanFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected LeviathanPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = LeviathanFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        LeviathanSupplies.dropFor(fight);
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
    }

    @Override
    protected final void tick() {
        fight.water().pulse(fight.config().num("water-rise-blocks-per-pulse", 500));
        fight.conduits().pulse(PULSE_TICKS);
        fight.columns().pulse(PULSE_TICKS);
        fight.whirlpool().pulse(PULSE_TICKS);
        fight.air().pulse(PULSE_TICKS);
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

    /** Relayed from {@code BossInstance} so a Conduit Smash charge can be interrupted by real damage. */
    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        fight.conduits().onBossDamaged(damageDealt);
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
                Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("The current eases — push him", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.2f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.DARK_AQUA)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.BLUE);
    }
}
