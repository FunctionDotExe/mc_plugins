package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.mechanics.TickingMechanic;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;

/**
 * Shared body of all eight Worldender phases. Two things run underneath every one of them, exactly the
 * split {@code ChoirPhaseMechanic} uses for the same reason: the vibration model ages its memory every
 * pulse, and the boss's target is re-pointed at whoever it currently hears — installed once per phase and
 * released in {@link #onStop()} by the shared {@code TickingMechanic} contract, but the underlying
 * {@link Vibration} instance itself is fight-wide and is never re-armed after phase one (batch-5 §2: "one
 * constant, eight variables" — the memory of who it last heard must not reset at a health-band seam).
 * <p>
 * <b>It is never invulnerable, in any phase.</b> Every phase here is an ungated damage race with an
 * environmental hazard layered on top, not a "break the objective" gate — batch-5 §0 rules 1–2 are about
 * subtraction and pacing, not about adding a sixth immunity archetype to a roster that already closed that
 * anti-pattern everywhere else (see the boss-design-philosophy memory note).
 */
abstract class WorldenderPhaseMechanic extends TickingMechanic {

    private static final int PULSE_TICKS = 5;

    protected final WorldenderFight fight;

    private final String label;

    protected WorldenderPhaseMechanic(BossInstance instance, String label) {
        super(instance, PULSE_TICKS);
        this.fight = WorldenderFight.of(instance);
        this.label = label;
    }

    @Override
    protected final void onStart() {
        fight.vibration().arm();
        instance.setTargetOverride(() -> fight.vibration().hunted());
        // Every Worldender phase is an ungated damage race with an environmental hazard on top, never a
        // "break the objective" gate (batch-5 §0) — recording the exposure immediately keeps the phase
        // floor wide open for the whole band instead of pinning health on a mechanic nothing here asks
        // players to clear.
        instance.recordExposure();
        onArm();
    }

    @Override
    protected final void onStop() {
        onDisarm();
        instance.setTargetOverride(null);
    }

    @Override
    protected final void tick() {
        fight.vibration().pulse(PULSE_TICKS);
        suppressVanillaAggro();
        onPulse(PULSE_TICKS);
        showBar();
    }

    /**
     * It is a real Warden, and a real Warden's melee bite and sonic boom are driven by its own
     * built-in anger — which the same hits and nearby sounds {@link Vibration} listens to also feed,
     * completely independent of anything this framework schedules. Left alone, that anger climbs to
     * "angry" during ordinary combat and the vanilla AI starts throwing its own attacks on its own
     * cooldown on top of every scripted {@link dev.rbm72.weaponsplugin.boss.BossAttack} here — which
     * reads in play as a second, un-telegraphed damage source stacking on the real one (the "stuns
     * almost every second" camera-knockback). Capping it every pulse keeps the vanilla AI too calm to
     * ever fire its own attack, so all of this fight's damage stays the kit batch-5 §3 describes.
     * <p>
     * <b>Anger alone was not enough, and this is why.</b> Clearing anger does not clear the
     * <em>target</em>, and a Warden's melee and sonic-boom goals only ask for a target — so as soon as
     * anger spiked for a single tick (which it does the instant anybody lands a hit on it, i.e.
     * constantly), the goals latched a target and then kept biting on their own cooldown with anger
     * back at zero. The visible result is a camera roll about once a second for the whole fight, from a
     * source no telegraph ever announced. The target is cleared here too: the engine drives this boss
     * with {@code getPathfinder().moveTo}, which does not read {@code getTarget()}, so nothing this
     * framework does needs it set.
     */
    private void suppressVanillaAggro() {
        if (!(instance.entity() instanceof Warden warden)) {
            return;
        }
        for (Player player : fight.combatants()) {
            warden.clearAnger(player);
        }
        warden.setTarget(null);
    }

    // ------------------------------------------------------------ subclass hooks

    protected void onArm() {
    }

    protected void onDisarm() {
    }

    protected void onPulse(int intervalTicks) {
    }

    /** What the mechanic bar says about this phase's own hazard, beyond the shared vibration readout. */
    protected abstract Component readoutText();

    // ---------------------------------------------------------------- helpers

    private void showBar() {
        var hunted = fight.vibration().hunted();
        Component vibrationLine = hunted == null
                ? Component.text("all quiet", NamedTextColor.GRAY)
                : Component.text("hunting " + hunted.getName(), NamedTextColor.RED);
        Component text = Component.text(label + "  ", NamedTextColor.DARK_PURPLE)
                .append(readoutText())
                .append(Component.text("   ", NamedTextColor.GRAY))
                .append(vibrationLine);
        instance.mechanicBar().updateShared(instance.barViewers(), text, healthFraction(), BossBar.Color.PURPLE);
    }

    protected final double healthFraction() {
        var max = instance.entity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 0.0;
        if (maxHealth <= 0.0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, instance.entity().getHealth() / maxHealth));
    }
}
