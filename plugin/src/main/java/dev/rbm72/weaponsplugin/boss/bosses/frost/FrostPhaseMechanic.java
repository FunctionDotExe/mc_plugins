package dev.rbm72.weaponsplugin.boss.bosses.frost;

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
 * Shared body of all four of the Frost Queen's phases: the ice field keeps growing, the Heart keeps
 * ticking once it exists, the campfires stay lit or don't, and the phase on top adds one objective of
 * its own — same split as {@code KingPhaseMechanic}, and for the same reason: none of her phases end on
 * health alone (batch-1 §2.3 gives each one an explicit survival/destruction condition beyond the
 * threshold).
 * <p>
 * <b>She is hittable for almost the whole fight.</b> Three of her four phases run no damage filter and
 * no forced invulnerability at all — batch-1 is explicit that P1, P2 and P4 never stop her being
 * hittable. P3 is the roster's one authorized exception (see {@link FrozenHeart}): the boss herself is
 * sealed while the Heart stands, and {@link HeartOfWinterPhase} is the only subclass that ever touches
 * {@link BossInstance#setForcedInvulnerable}.
 */
abstract class FrostPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final FrostFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected FrostPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = FrostFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        // Placed once, from whichever phase happens to start first — always P1 in practice, but
        // idempotent so nothing breaks if that ever changes.
        fight.campfires().place();
        FrostSupplies.dropFor(fight);
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.iceField().pulse(PULSE_TICKS);
        fight.avalanche().pulse(PULSE_TICKS);
        fight.heart().pulse(PULSE_TICKS);
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
                Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("Her hold here is broken — push her", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
        Fx.sound(centre, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.4f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.AQUA)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.BLUE);
    }

    /** Everyone currently fighting her — shared shorthand the phase subclasses lean on repeatedly. */
    protected java.util.List<Player> combatants() {
        return fight.combatants();
    }
}
