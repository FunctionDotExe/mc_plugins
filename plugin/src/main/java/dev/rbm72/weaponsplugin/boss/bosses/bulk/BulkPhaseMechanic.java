package dev.rbm72.weaponsplugin.boss.bosses.bulk;

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
 * Shared body of all four Amalgamated Bulk phases: the catalysts keep marking their radii, the floor
 * keeps growing and feeding, and the phase on top adds one demand. Every phase exits on a
 * kill-placement, coverage or denial objective as well as its health band (§3.6) — a group that simply
 * out-damaged the Bulk would never have met the one rule the boss exists to teach.
 * <p>
 * <b>It is never invulnerable.</b> This is the roster's one boss where excess damage is itself the
 * cheese, and it is punished structurally — by the floor the group's own kills grow — rather than by a
 * wall, so {@code filterDamage} stays the identity for the whole fight.
 */
abstract class BulkPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final BulkFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected BulkPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = BulkFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        BulkSupplies.dropFor(fight);
        fight.sculk().arm();
        fight.bulklings().arm();
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.catalysts().pulse();
        fight.sculk().pulse(PULSE_TICKS);
        fight.sculk().feedBoss(PULSE_TICKS);
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
     * Clean kills and denied reabsorptions are what progress looks like on this boss, so both reset the
     * floor-timeout clock. Sculk cleared counts too — a group spending a minute scraping the floor is
     * doing the fight, not stalling it.
     */
    protected int progressSignal() {
        return fight.bulklings().cleanKills() * 10
                + fight.bulklings().reabsorbDenied() * 10
                + Math.max(0, 200 - fight.sculk().blockCount());
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
                Component.text(label, NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It stops growing here — push it", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.0f, 1.4f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.GREEN)
                .append(readoutText());
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.RED);
    }
}
