package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P3 — Powder Keg</b> (§1.3, 48–20%). He stacks real TNT and lights long fuse lines — several
 * {@link TntClusters} live at once now instead of Forge's tutorial single — while {@code RisingLava}
 * (armed since P2) keeps climbing underneath the whole scramble, so the group is genuinely splitting
 * attention three ways as the design's strategy note asks for.
 * <p>
 * Exit requires three clusters neutralised, <em>not</em> detonated (§1.3) — {@link TntClusters#setTarget}
 * resets the phase-local tally fresh on entry, so a fuse solved back in Forge does not count twice.
 * <p>
 * Solo (§1.5): three clusters instead of five, one fuse-line pressure at a time in spirit even though
 * several may be alight — a slower, tighter version of the same triage rather than a different puzzle.
 */
final class PowderKegPhase extends InfernoPhaseMechanic {

    PowderKegPhase(BossInstance instance, double exitFraction) {
        super(instance, "Powder Keg", exitFraction);
    }

    @Override
    protected void onArm() {
        int players = fight.playerCount();
        boolean solo = fight.solo();
        int liveClusters = solo ? Math.max(1, fight.config().num("tnt-cluster-count-solo", 3))
                : Math.min(fight.config().num("tnt-cluster-count-max", 5), 3 + (players - 1));
        fight.clusters().setTarget(liveClusters);
        fight.trails().arm(1);
        fight.magma().arm(Math.max(1, 1 + players / 3));
        // fight.lava() stays armed from P2 — the rise does not pause for this phase's scramble.
    }

    @Override
    protected void onDisarm() {
        fight.trails().disarm();
        fight.clusters().disarm();
    }

    @Override
    protected boolean objectiveMet() {
        return fight.clusters().neutralizedThisPhase() >= fight.config().num("tnt-clusters-required", 3);
    }

    @Override
    protected int progressSignal() {
        return fight.clusters().neutralizedThisPhase() + fight.lava().playerMadeCount();
    }

    @Override
    protected Component readoutText() {
        int need = fight.config().num("tnt-clusters-required", 3);
        int have = Math.min(need, fight.clusters().neutralizedThisPhase());
        return Component.text("clusters neutralised " + have + "/" + need,
                objectiveMet() ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        int need = Math.max(1, fight.config().num("tnt-clusters-required", 3));
        return (double) fight.clusters().neutralizedThisPhase() / need;
    }
}
