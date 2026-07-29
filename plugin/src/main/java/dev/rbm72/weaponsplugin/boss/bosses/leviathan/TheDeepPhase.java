package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P2 — The Deep.</b> Full submersion. Guardians arrive, bubble columns start appearing as both hazard
 * and highway, and he starts actively defending his own conduit network with Conduit Smash. Roughly
 * midway through, a one-shot Whirlpool spins up at the centre — the phase's own exit condition.
 * <p>
 * Exit requires the health threshold <em>and</em> the one-shot whirlpool having run its course (batch-2
 * §3.3) — reaching the end of that event, not any specific outcome for any one player, is what "survive
 * one Whirlpool" means here; a wipe ends the fight through the normal boss-death/player-death paths
 * regardless of what this phase mechanic thinks.
 */
public final class TheDeepPhase extends LeviathanPhaseMechanic {

    private int smashCooldown;
    private int whirlpoolDelay;
    private boolean whirlpoolStarted;

    public TheDeepPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Deep", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.water().raiseTo(1.0);
        int players = fight.playerCount();
        fight.columns().ensureColumns(
                fight.config().num("columns-soulsand-p2", 1 + players / 2),
                fight.config().num("columns-magma-p2", 1 + players / 3));
        fight.guardians().topUp();
        smashCooldown = fight.config().num("conduit-smash-interval-ticks", 260);
        whirlpoolDelay = fight.config().num("whirlpool-oneshot-delay-ticks", 220);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        if (!whirlpoolStarted) {
            whirlpoolDelay -= intervalTicks;
            if (whirlpoolDelay <= 0) {
                whirlpoolStarted = true;
                fight.whirlpool().startOneShot();
            }
        }

        smashCooldown -= intervalTicks;
        if (smashCooldown <= 0) {
            smashCooldown = fight.config().num("conduit-smash-interval-ticks", 260);
            fight.conduits().trySmash();
        }
    }

    @Override
    protected boolean objectiveMet() {
        return fight.whirlpool().p2Completed();
    }

    @Override
    protected int progressSignal() {
        return whirlpoolStarted ? 1 : 0;
    }

    @Override
    protected Component readoutText() {
        if (objectiveMet()) {
            return Component.text("the pull has eased", NamedTextColor.GREEN);
        }
        return Component.text(whirlpoolStarted ? "hold through the whirlpool" : "the current is building",
                NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return objectiveMet() ? 1.0 : whirlpoolStarted ? 0.5 : 0.1;
    }
}
