package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>P3 — The Serpent.</b> It surfaces fully: a long segmented body ({@link WyrmSegments}) coiling
 * through the arena, moving terrain more than an enemy. Most segments are armored (hits barely
 * register); a subset migrates exposed every few seconds, so the group is constantly repositioning
 * around a moving wall of boss rather than parking on one favourite segment. The real boss entity rides
 * invisibly at the lead segment's position — its health is still what every hit ultimately lands on
 * (see {@code WyrmSegments#isForwarding}), it just isn't a separate, independently-hittable body anymore.
 * Exit requires three segments actually broken, on top of the health threshold (batch-4 §1.3).
 */
final class SerpentPhase extends WyrmPhaseMechanic {

    private static final int REQUIRED_BROKEN = 3;

    private double coilAngle;
    private int migrateTicksLeft;
    private final List<Integer> exposedIndices = new ArrayList<>();

    SerpentPhase(BossInstance instance, double exitFraction) {
        super(instance, exitFraction);
    }

    @Override
    protected void onArm() {
        int segmentCount = fight.config().num("segment-count", 8);
        fight.segments().spawnChain(segmentCount, fight.config().dbl("segment-max-hp", 50.0));
        coilAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        instance.entity().setInvisible(true);
        // The real entity's position is fully authored by the head segment's teleport every pulse (see
        // #onPulse) — same reasoning as EvasiveRig/Burrow: left alone, BossInstance#tick's forced
        // moveTo would fight that between pulses instead of just riding along with it.
        instance.entity().setAI(false);
        migrateExposed();
        migrateTicksLeft = fight.config().num("segment-migrate-interval-ticks", 100);
    }

    @Override
    protected void onDisarm() {
        fight.segments().discardAll();
        if (instance.entity().isValid()) {
            instance.entity().setInvisible(false);
            instance.entity().setAI(true);
        }
    }

    @Override
    protected void onPulse(int intervalTicks) {
        double degreesPerSecond = fight.config().dbl("coil-angular-speed-degrees", 9.0);
        coilAngle += Math.toRadians(degreesPerSecond) * (intervalTicks / 20.0);

        double baseRadius = instance.arena().radius() * fight.config().dbl("coil-radius-fraction", 0.5);
        double amplitude = instance.arena().radius() * fight.config().dbl("coil-amplitude-fraction", 0.15);
        double angularSpacing = fight.config().dbl("coil-segment-spacing-radians", 0.42);
        fight.segments().reposition(coilAngle, baseRadius, amplitude, angularSpacing);

        Location head = fight.segments().headLocation();
        if (head != null) {
            instance.entity().teleport(head);
        }

        migrateTicksLeft -= intervalTicks;
        if (migrateTicksLeft <= 0) {
            migrateExposed();
            migrateTicksLeft = fight.config().num("segment-migrate-interval-ticks", 100);
        }
    }

    private void migrateExposed() {
        for (int index : exposedIndices) {
            fight.segments().setExposed(index, false);
        }
        exposedIndices.clear();

        int segmentCount = fight.segments().size();
        int wanted = 1 + fight.playerCount() / 2;
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < segmentCount; i++) {
            if (!fight.segments().isBroken(i)) {
                candidates.add(i);
            }
        }
        java.util.Collections.shuffle(candidates, ThreadLocalRandom.current());
        for (int i = 0; i < Math.min(wanted, candidates.size()); i++) {
            int index = candidates.get(i);
            fight.segments().setExposed(index, true);
            exposedIndices.add(index);
        }
    }

    @Override
    public double filterDamage(Player attacker, double damage) {
        if (fight.segments().isForwarding()) {
            return damage;
        }
        // A hit that somehow lands directly on the real (invisible, mostly out of the way) body,
        // rather than a segment marker — barely registers, same "not the answer" as the Colossus.
        return damage * fight.config().dbl("body-hit-fraction", 0.04);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.segments().brokenCount() >= REQUIRED_BROKEN;
    }

    @Override
    protected int progressSignal() {
        return fight.segments().brokenCount() * 1000;
    }
}
