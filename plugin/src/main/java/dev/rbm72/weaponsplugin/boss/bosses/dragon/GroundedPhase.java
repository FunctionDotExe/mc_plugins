package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * <b>P4 — Grounded.</b> Batch-2 §4.3: wings shredded, it comes down for good — the finale is a melee
 * brawl (Tail Sweep, bites, fire fields) with the roles fully inverted, archers suddenly the ones
 * scrambling for safe angles instead of the ones with all the work.
 * <p>
 * The dragon is pinned into {@link AerialRig.AerialState#GROUNDED} for the rest of the fight
 * ({@link #permanentlyGrounded}), the fireball spawner stops for good (there is nothing left to
 * deflect once it can be reached directly), and periodic ground fire under it stands in for "fire
 * fields" — everything else (Tail Sweep, Firestorm Breath, Grab and Drop) is already handled by the
 * existing attack pool wired in from {@code DragonElder}.
 * <p>
 * Objective is satisfied the instant the phase starts — last phase, nothing left to gate — exactly the
 * pattern every other roster boss's final phase uses.
 */
public final class GroundedPhase extends DragonPhaseMechanic {

    public GroundedPhase(BossInstance instance) {
        super(instance, "Grounded", 0.0);
    }

    @Override
    protected void onArm() {
        permanentlyGrounded = true;
        fight.aerial().enterGrounded(groundSpotBelow(instance.entity().getLocation()));
        fight.fireballs().stopSpawning();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        // A small residual fire field under it every few seconds — the ground-level equivalent of the
        // burning lanes P3 leaves behind, and one more reason archers can't just camp a single spot.
        if (elapsedTicks % fight.config().num("grounded-fire-field-interval-ticks", 100) < intervalTicks) {
            Location at = instance.entity().getLocation();
            fight.fireLanes().igniteCone(at, new Vector(1, 0, 0), fight.config().dbl("grounded-fire-field-radius", 3.5),
                    180.0, fight.config().num("grounded-fire-field-duration-ticks", 100));
            Fx.coloredBurst(at.clone().add(0, 0.3, 0), DragonFight.DRAGON_RED, 1.6f, 20, 1.5);
        }
    }

    @Override
    protected boolean objectiveMet() {
        return true;
    }

    @Override
    protected boolean announcesCompletion() {
        return false;
    }

    @Override
    protected Component readoutText() {
        return Component.text("it's grounded for good — finish it", NamedTextColor.GREEN);
    }

    @Override
    protected double readoutProgress() {
        return 1.0 - healthFraction();
    }
}
