package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * <b>P3 — Host.</b> He burrows into a real sculk growth at arena centre. He is not invulnerable —
 * {@link #filterDamage} lets only currently-cleansed (low-Infection) players land real hits while the
 * Host stands, which forces a live role rotation: clean players push in, infected players peel off to
 * the pyres and hold noise discipline, then swap. Breaking the Host open removes the restriction, but
 * its sensors punish sprinting and jumping with a shriek — Darkness plus an add wave — the whole time.
 * <p>
 * Exit requires the Host broken open, on top of the health seam — HP alone will not pass this phase
 * (batch-1 §4.3).
 */
final class HostPhase extends PlaguePhaseMechanic {

    HostPhase(BossInstance instance, double exitFraction) {
        super(instance, "Host", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.host().build();
    }

    /** Only the cleansed land a real hit while the Host still gates him — a rule, not a wall. */
    @Override
    public double filterDamage(Player attacker, double damage) {
        if (!fight.host().gatesDamage()) {
            return damage;
        }
        return fight.isCleansed(attacker) ? damage : damage * fight.config().dbl("host-uncleansed-fraction", 0.05);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.host().brokenOpen();
    }

    /**
     * A new record low on the Host's HP resets the floor-lock timeout's clock — without this, a group
     * genuinely chipping the Host down (through noise discipline, taking their turns) for longer than
     * this boss's floor-lock timeout without quite breaking it open looks identical to a group that
     * never engaged at all, and the valve would hand the phase over mid-effort.
     */
    @Override
    protected int progressSignal() {
        return (int) Math.round((1.0 - fight.host().fraction()) * 1000);
    }

    @Override
    protected Component readoutText() {
        if (fight.host().brokenOpen()) {
            return Component.text("the Host is open — he can be hurt", NamedTextColor.GREEN);
        }
        int pct = (int) Math.round((1.0 - fight.host().fraction()) * 100);
        return Component.text("Host " + pct + "% open — stay quiet, stay clean", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return 1.0 - fight.host().fraction();
    }
}
