package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * <b>P3 — The Broken Oath.</b> He abandons the duel, swings at everyone, and the ceiling starts failing.
 * In exchange he exposes his spine: only hits landed from <b>behind his facing arc</b> count for full.
 * <p>
 * This is the phase where the boss's damage rule stops being about <em>who</em> and becomes about
 * <em>where</em>. It replaces the duel filter outright rather than stacking with it — the mark is gone,
 * so there is nobody the old rule could name, and leaving both in place would mean a group with no
 * Challenger dealing a tenth of a tenth.
 * <p>
 * The bell is the only answer. It staggers him and turns him to face whoever rang it, which is what puts
 * his back toward everyone else — so the group has to split into someone holding his front, someone
 * making the trip to the dais, and everyone else waiting behind him for the window. The phase will not
 * end until the bell has been used at least twice, so a group that never works this out never leaves.
 * <p>
 * Meanwhile Judgment starts, and it does not stop again. From here the floor is a resource being spent.
 */
final class BrokenOathPhase extends KingPhaseMechanic {

    BrokenOathPhase(BossInstance instance, double exitFraction) {
        super(instance, "The Broken Oath", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.duel().abandon();
        fight.judgment().setRunning(true);
    }

    /**
     * Facing-relative damage. Front hits are cut to almost nothing rather than to zero — a hit that
     * registers for a sliver still reads as connecting, so the player learns "wrong side" instead of
     * "my weapon stopped working", which is the difference between a legible rule and a bug report.
     */
    @Override
    public double filterDamage(Player attacker, double damage) {
        double arc = fight.config().dbl("oath-front-arc-degrees", 150.0);
        if (ThroneBell.isBehind(instance.entity(), attacker, arc)) {
            return damage * fight.config().dbl("oath-rear-multiplier", 1.35);
        }
        return damage * fight.config().dbl("oath-front-fraction", 0.05);
    }

    /**
     * Draws the arc on the floor every pulse, so "behind him" is a place players can see rather than a
     * number they have to infer from their damage output. Reinforcing a physical rule that already
     * exists is one of the five jobs §0.1 permits a particle.
     */
    @Override
    protected void onPulse(int intervalTicks) {
        Location at = instance.entity().getLocation();
        double arc = fight.config().dbl("oath-front-arc-degrees", 150.0);
        double facing = Math.toRadians(at.getYaw() + 90.0);
        double half = Math.toRadians(arc / 2.0);
        for (int i = 0; i <= 10; i++) {
            double angle = facing - half + (2 * half * i / 10.0);
            Location edge = at.clone().add(Math.cos(angle) * 3.2, 0.1, Math.sin(angle) * 3.2);
            Fx.coloredBurst(edge, KingFight.KING_SHADOW, 1.0f, 2, 0.05);
        }
        Location behind = at.clone().add(Math.cos(facing + Math.PI) * 2.6, 0.1, Math.sin(facing + Math.PI) * 2.6);
        Fx.burst(behind, Particle.HAPPY_VILLAGER, 3, 0.4);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.bell().rings() >= requiredRings();
    }

    private int requiredRings() {
        return Math.max(1, fight.config().num("oath-rings-required", 2));
    }

    @Override
    protected int progressSignal() {
        return fight.bell().rings() * 100 + fight.court().chainsBroken();
    }

    @Override
    protected Component readoutText() {
        if (!fight.bell().isUp()) {
            // The bell failed to place (ledger budget, unbuildable dais). Say so rather than asking the
            // group to find a prop that is not there — the floor-lock valve will release the phase.
            return Component.text("the bell never hung — strike him from behind", NamedTextColor.GOLD);
        }
        int rung = Math.min(fight.bell().rings(), requiredRings());
        return Component.text("bell " + rung + "/" + requiredRings()
                + " — his front takes nothing", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) fight.bell().rings() / requiredRings());
    }
}
