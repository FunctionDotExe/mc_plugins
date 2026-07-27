package dev.rbm72.weaponsplugin.boss.bosses.king;

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
 * Shared body of all four of the Fallen King's phases: the duel runs, the court keeps arriving, the bell
 * keeps hanging on its dais, and the phase on top of that adds one objective of its own.
 * <p>
 * <b>Why no phase ever ends on health alone.</b> Both framework levers are used, because either on its
 * own is wrong:
 * <ul>
 *   <li>{@link BossInstance#recordExposure()} fires the instant the phase's objective is met, which is
 *       what releases {@code clampToPhaseFloor}'s pin on the health seam. Until then no amount of damage
 *       carries the boss into the next band — burst cannot skip a mechanic that has not been played.</li>
 *   <li>{@link #readyToAdvance()} requires the objective <em>and</em> the health threshold, not the
 *       objective alone. Returning true on the objective by itself would turn "HP and resolution" into
 *       "HP or resolution", handing the group the rest of the band as a reward for being prompt.</li>
 * </ul>
 * <b>He is never invulnerable.</b> No phase here sets a damage multiplier. What varies is the
 * <em>rule</em>: in P1 and P2 only his named Challenger's blows land at full weight, in P3 only hits from
 * behind his facing arc do, and in P4 nothing is filtered at all. Damage stops being permission he grants
 * and becomes a statement about how you are fighting.
 */
abstract class KingPhaseMechanic extends TickingMechanic {

    /** Pulse resolution. Fast enough for a duel: a riposte window is twenty ticks wide. */
    private static final int PULSE_TICKS = 5;

    protected final KingFight fight;

    private final String label;
    /** Health fraction the band below this phase starts at; the final phase passes 0. */
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    protected KingPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = KingFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        // §0.3: the arena provides every item this boss's counterplay leans on, replenished per phase.
        KingSupplies.dropFor(fight);
        // Hung from the very first phase rather than from P3, so a group arrives at the phase where it
        // is mandatory already knowing what it does.
        fight.bell().raise();
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        // Whatever this phase pointed him at is this phase's business and nobody else's.
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.duel().pulse(PULSE_TICKS);
        fight.court().pulse(PULSE_TICKS, knightTarget());
        fight.shards().pulse(PULSE_TICKS);
        fight.judgment().pulse(PULSE_TICKS);
        fight.bell().pulse();
        onPulse(PULSE_TICKS);

        // Partial headway keeps the framework's floor-lock valve from handing the phase over. Every one
        // of this boss's objectives is a sequence of real physical acts under pressure — cutting chains,
        // running shards, ringing a bell — and the valve's default patience cannot tell a long objective
        // from an unreachable one without being told when something moved.
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

    /**
     * The duel's rule by default: outsiders' blows are cut to a tenth and a Challenger's riposte spikes.
     * P3 replaces it with the facing arc, and P4 drops filtering entirely.
     */
    @Override
    public double filterDamage(Player attacker, double damage) {
        return fight.duel().filterDamage(attacker, damage);
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        fight.duel().onBossDamaged(attacker, damageDealt);
        fight.shards().onBossDamaged(attacker, damageDealt);
    }

    // ------------------------------------------------------------ subclass hooks

    protected void onArm() {
    }

    protected void onDisarm() {
    }

    protected void onPulse(int intervalTicks) {
    }

    /**
     * Whether meeting the objective gets its own title. False for the final phase, whose objective is
     * satisfied the moment it starts — a "you did it" banner landing on top of the enrage cinematic reads
     * as a bug rather than a beat.
     */
    protected boolean announcesCompletion() {
        return true;
    }

    protected abstract boolean objectiveMet();

    /**
     * A monotonic count of <em>player</em> headway on this phase's objective. Must never move because
     * the boss did his own job (a chain lapsing on its timer, a knight wandering off) — a signal that
     * did would keep the floor-lock valve topped up during a fight nobody is solving, which is the bug
     * it exists to close.
     */
    protected int progressSignal() {
        return 0;
    }

    protected abstract Component readoutText();

    protected abstract double readoutProgress();

    /** How many knights should be walking the floor during this phase. */
    protected int knightTarget() {
        int players = fight.playerCount();
        int wanted = fight.config().num("knight-base-count", 2) + Math.max(0, players - 1);
        return Math.min(fight.config().num("knight-max-live", 6), wanted);
    }

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
                Component.text("His hold here is broken — push him", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.BLOCK_BELL_RESONATE, 1.0f, 1.2f);
        Fx.sound(centre, Sound.ENTITY_WITHER_SKELETON_HURT, 1.0f, 1.4f);
    }

    private void showBar() {
        Player challenger = fight.duel().challenger();
        String duelLine = fight.duel().abandoned()
                ? "no challenger — hit him from behind"
                : challenger == null ? "no challenger" : "challenger: " + challenger.getName();
        Component text = Component.text(label + "  ", NamedTextColor.GOLD)
                .append(readoutText())
                .append(Component.text("   " + duelLine, NamedTextColor.GRAY))
                .append(Component.text("   court " + fight.court().aliveCount(), NamedTextColor.DARK_GRAY));
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.RED);
    }
}
