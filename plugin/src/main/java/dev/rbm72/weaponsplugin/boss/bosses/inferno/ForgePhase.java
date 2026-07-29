package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — The Forge</b> (§1.3, 100–72%). Baseline: fire trails crawl the floor on a readable schedule,
 * magma hazards and burning logs start immediately, and a single fuse already burns toward one lit TNT
 * block — the tutorial-scale version of P3's Powder Keg, teaching the fuse's two solves (douse it, or
 * break the line with a placed block) before the group ever has to juggle several at once.
 * <p>
 * Exit also requires that one fuse be cut before it detonates (§1.3) — {@code TntClusters}' neutralise
 * counter is reset fresh by {@link #onArm} via {@code setTarget}, so a fuse that goes off here simply
 * respawns and the group gets another shot rather than the objective becoming unwinnable.
 * <p>
 * Solo (§1.5): one fuse at a time regardless of size, so this phase is identical solo and grouped except
 * for how many ambient fire trails are crawling at once.
 */
final class ForgePhase extends InfernoPhaseMechanic {

    ForgePhase(BossInstance instance, double exitFraction) {
        super(instance, "The Forge", exitFraction);
    }

    @Override
    protected void onArm() {
        int players = fight.playerCount();
        fight.trails().arm(1 + players / 2);
        fight.clusters().setTarget(1);
        fight.magma().arm(1);
        fight.logs().arm();
        fight.cinderNova().arm();
    }

    @Override
    protected void onDisarm() {
        fight.trails().disarm();
        fight.clusters().disarm();
        fight.magma().disarm();
        // Burning Logs and Cinder Nova are P1-onward — left armed through every later phase.
    }

    @Override
    protected boolean objectiveMet() {
        return fight.clusters().neutralizedThisPhase() >= 1;
    }

    @Override
    protected int progressSignal() {
        return fight.clusters().neutralizedThisPhase() + fight.lava().playerMadeCount();
    }

    @Override
    protected Component readoutText() {
        boolean done = objectiveMet();
        return Component.text(done ? "first fuse cut" : "cut the fuse — douse it or wall it off",
                done ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, fight.clusters().neutralizedThisPhase());
    }
}
