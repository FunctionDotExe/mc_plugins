package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P3 — Maelstrom.</b> The whirlpool goes permanent: position is now something the group maintains
 * rather than chooses, and drifting into the centre is punished continuously rather than by one
 * scripted event. Magma and soul-sand columns keep multiplying, giving the group more of both the
 * hazard and the escape tool as the pull gets harder to ignore.
 * <p>
 * Exit requires the health threshold <em>and</em> three whirlpool "breaks" — riding a soul-sand column
 * out of the pull radius, three separate times (batch-2 §3.3). Not scaled down solo: a single player
 * can absolutely ride the same escape three times over, and the pull itself never gets easier just
 * because there are fewer bodies in the water — "no fixed point survives" applies at every group size
 * equally.
 */
public final class MaelstromPhase extends LeviathanPhaseMechanic {

    private int smashCooldown;

    public MaelstromPhase(BossInstance instance, double exitFraction) {
        super(instance, "Maelstrom", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.water().raiseTo(1.0);
        fight.whirlpool().makePermanent();
        int players = fight.playerCount();
        fight.columns().ensureColumns(
                fight.config().num("columns-soulsand-p3", 2 + players / 2),
                fight.config().num("columns-magma-p3", 2 + players / 3));
        fight.guardians().topUp();
        smashCooldown = fight.config().num("conduit-smash-interval-ticks-p3", 200);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        smashCooldown -= intervalTicks;
        if (smashCooldown <= 0) {
            smashCooldown = fight.config().num("conduit-smash-interval-ticks-p3", 200);
            fight.conduits().trySmash();
        }
    }

    @Override
    protected boolean objectiveMet() {
        return fight.whirlpool().breakCount() >= fight.whirlpool().breaksRequired();
    }

    @Override
    protected int progressSignal() {
        return fight.whirlpool().breakCount();
    }

    @Override
    protected Component readoutText() {
        int required = fight.whirlpool().breaksRequired();
        return Component.text("whirlpool broken " + Math.min(fight.whirlpool().breakCount(), required) + "/" + required,
                objectiveMet() ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) fight.whirlpool().breakCount() / fight.whirlpool().breaksRequired());
    }
}
