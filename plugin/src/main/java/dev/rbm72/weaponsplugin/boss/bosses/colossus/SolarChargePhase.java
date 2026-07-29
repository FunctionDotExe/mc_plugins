package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * <b>P3 — Solar Charge.</b> Real beacons ({@link Beacons}) planted around the arena; a completed charge
 * fully repairs the Colossus's broken joints. Batch-2 §2.3 is the single most load-bearing sentence in
 * this boss's spec: <b>"the Colossus stays fully damageable throughout this phase... the beacons are
 * not a gate and must never be implemented as one — they threaten progress loss, not permission to
 * fight."</b> So unlike every other phase this one does not inherit {@code ColossusPhaseMechanic}'s
 * default joint-armour {@code filterDamage} — it overrides it to pass every hit through untouched,
 * regardless of where it landed. Nothing in {@link Beacons} ever calls {@code setDamageMultiplier} or
 * {@code setForcedInvulnerable} either.
 * <p>
 * Exit requires three charge cycles interrupted on top of the health threshold.
 */
final class SolarChargePhase extends ColossusPhaseMechanic {

    SolarChargePhase(BossInstance instance, double exitFraction) {
        super(instance, "Solar Charge", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.beacons().beginCycle();
    }

    @Override
    protected void onDisarm() {
        fight.beacons().discardAll();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.beacons().pulse(intervalTicks);
    }

    /** §2.3: never a damage gate. Every hit lands wherever it lands. */
    @Override
    public double filterDamage(Player attacker, double damage) {
        return damage;
    }

    @Override
    protected boolean objectiveMet() {
        return fight.beacons().interruptsSoFar() >= fight.config().num("beacon-interrupts-required", 3);
    }

    @Override
    protected int progressSignal() {
        return fight.beacons().interruptsSoFar();
    }

    @Override
    protected Component readoutText() {
        int required = fight.config().num("beacon-interrupts-required", 3);
        if (objectiveMet()) {
            return Component.text("its charge cycle is broken for good", NamedTextColor.GREEN);
        }
        return Component.text(fight.beacons().interruptsSoFar() + "/" + required + " interrupted — "
                + fight.beacons().blockedCount() + "/" + fight.beacons().total() + " beams blocked",
                NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        int required = fight.config().num("beacon-interrupts-required", 3);
        return Math.min(1.0, (double) fight.beacons().interruptsSoFar() / required);
    }
}
