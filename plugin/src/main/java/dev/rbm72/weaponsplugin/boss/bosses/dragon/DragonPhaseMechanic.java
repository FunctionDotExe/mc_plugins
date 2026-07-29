package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.mechanics.TickingMechanic;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

/**
 * Shared body of all four of the Dragon Elder's phases — same split as {@code NecroPhaseMechanic}/
 * {@code StormPhaseMechanic}: the fight-scoped systems (pillars, wing membranes, the fireball spawner)
 * get pulsed every cycle regardless of which phase is running, and the phase on top adds its own
 * objective.
 * <p>
 * <b>He is never invulnerable, in any phase.</b> Nothing here — or in any of the four concrete phases —
 * ever calls {@code setForcedInvulnerable} or a sub-1.0 {@code setDamageMultiplier}. What actually
 * gates the group across this fight is distance (he is out of melee reach for most of it) and the
 * membrane/HP split, never a permission switch, which is exactly the "boss stays live" rule this
 * rework is built around.
 */
public abstract class DragonPhaseMechanic extends TickingMechanic {

    /** Pulse resolution — fine enough that a strafing run's damage-along-path check doesn't miss its window. */
    private static final int PULSE_TICKS = 4;

    protected final DragonFight fight;

    private final String label;
    private final double exitFraction;

    private boolean objectiveRecorded;
    private int lastProgressSignal;

    /**
     * Ticks left in the current grounded window before {@link #tick()} automatically resumes circling.
     * Shared across P1's tutorial landing, P2's post-heal window, and every phase's membrane-shredded
     * bonus window, so none of them has to reimplement "grounded for N ticks, then back up".
     */
    private int groundedWindowTicksLeft;
    /** Set by {@code GroundedPhase} — once true, a grounded window never auto-resumes circling. */
    protected boolean permanentlyGrounded;

    protected DragonPhaseMechanic(BossInstance instance, String label, double exitFraction) {
        super(instance, PULSE_TICKS);
        this.fight = DragonFight.of(instance);
        this.label = label;
        this.exitFraction = exitFraction;
    }

    @Override
    protected final void onStart() {
        ArenaSupplies.dropFor(fight);
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
    }

    @Override
    protected final void tick() {
        fight.pillars().pulse();
        fight.membranes().pulse(PULSE_TICKS);
        tickGroundedWindow(PULSE_TICKS);
        // "Shoot them — this is what eventually grounds it." A bonus grounded window on top of whatever
        // this phase's own objective earns, so sustained arrow pressure always has somewhere to go even
        // mid-phase. Never fires while already grounded, and never during P4 (permanentlyGrounded), where
        // the boss has nothing left to be shaken out of.
        if (!permanentlyGrounded && !isGrounded() && fight.membranes().consumeShreddedSignal()) {
            enterGroundedWindow(fight.config().num("membrane-forced-window-ticks", 100));
        }
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

    /** Arm this phase's own objective — start/retune the fireball cadence, kick off a run/breath cycle. */
    protected void onArm() {
    }

    /** Release this phase's own state. The fight-scoped pillars/membranes/fireballs are not this method's business. */
    protected void onDisarm() {
    }

    /** One pulse of this phase's own objective, after the fight-scoped systems have been ticked. */
    protected void onPulse(int intervalTicks) {
    }

    /** False for the final phase, whose objective is satisfied the instant it starts. */
    protected boolean announcesCompletion() {
        return true;
    }

    protected abstract boolean objectiveMet();

    /** A count of player headway on this phase's objective that only ever rises. Zero is valid for a phase with nothing to gate. */
    protected int progressSignal() {
        return 0;
    }

    protected abstract Component readoutText();

    protected abstract double readoutProgress();

    // ---------------------------------------------------------------- grounded windows

    /**
     * Forces a grounded window right where the boss currently is — batch-2 §4.4's "Grounded window ...
     * all-in melee damage" — regardless of what triggered it (a successful perch landing, a denied
     * perch draining its stamina, or the wing membranes emptying out). Fixed length, same reward for
     * everyone, per the spec's explicit "the reward is the same for everyone."
     */
    protected final void enterGroundedWindow(int durationTicks) {
        fight.aerial().enterGrounded(groundSpotBelow(instance.entity().getLocation()));
        groundedWindowTicksLeft = durationTicks;
        instance.showTitle(
                Component.text("GROUNDED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It's down — all in", NamedTextColor.GRAY));
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 0.5f);
        Fx.burst(instance.entity().getLocation(), Particle.CLOUD, 30, 0.8);
    }

    protected final boolean isGrounded() {
        return fight.aerial().state() == AerialRig.AerialState.GROUNDED;
    }

    private void tickGroundedWindow(int intervalTicks) {
        if (permanentlyGrounded || fight.aerial().state() != AerialRig.AerialState.GROUNDED || groundedWindowTicksLeft <= 0) {
            return;
        }
        groundedWindowTicksLeft -= intervalTicks;
        if (groundedWindowTicksLeft <= 0) {
            fight.aerial().enterCircling();
        }
    }

    protected static Location groundSpotBelow(Location above) {
        World world = above.getWorld();
        if (world == null) {
            return above;
        }
        Location spot = above.clone();
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1.0);
        return spot;
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
                Component.text("It can't stay up there forever — push it", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.1f);
    }

    private void showBar() {
        Component text = Component.text(label + "  ", NamedTextColor.RED)
                .append(readoutText())
                .append(Component.text("   wings " + (int) Math.round(fight.membranes().fraction() * 100) + "%",
                        NamedTextColor.GRAY));
        instance.mechanicBar().updateShared(instance.barViewers(), text, readoutProgress(),
                objectiveMet() ? BossBar.Color.GREEN : BossBar.Color.RED);
    }
}
