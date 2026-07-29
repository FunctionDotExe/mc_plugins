package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P1's baseline hazard: ambient {@link FireTrail}s that ignite from the arena rim and crawl toward the
 * middle on a readable, scripted path (§1.3 P1 — "Fire Trails... spreading along the floor along a
 * readable path"). Unlike a {@link TntClusters} fuse, an ambient trail has nothing waiting at the end
 * of it — it is simply ground you should not be standing on, and touching it is what teaches the
 * Burning rules before P3 asks players to defuse anything.
 * <p>
 * Armed only while a phase wants it live; disarmed trails finish burning out their current path and are
 * not replaced. Kept fight-scoped (not rebuilt per phase) so a trail mid-crawl when P1 ends is allowed to
 * finish rather than snapping out of existence mid-telegraph.
 */
final class FireTrails {

    private static final int MAX_TILES = 14;

    private final InfernoFight fight;
    private final List<FireTrail> active = new ArrayList<>();

    private boolean armed;
    private int targetCount = 1;
    private int respawnCountdownTicks;

    FireTrails(InfernoFight fight) {
        this.fight = fight;
    }

    /** Arms ambient trails at {@code count} concurrent, scaling with the roster's "1 + 1 per 2 players" rule. */
    void arm(int count) {
        armed = true;
        targetCount = Math.max(1, count);
    }

    /** Stops spawning new trails; whatever is already burning finishes on its own. */
    void disarm() {
        armed = false;
    }

    int liveCount() {
        return active.size();
    }

    void pulse(int intervalTicks) {
        for (Iterator<FireTrail> it = active.iterator(); it.hasNext(); ) {
            FireTrail trail = it.next();
            trail.pulse(intervalTicks);
            if (trail.resolved()) {
                trail.expire();
                it.remove();
            }
        }
        if (!armed) {
            return;
        }
        respawnCountdownTicks -= intervalTicks;
        if (active.size() < targetCount && respawnCountdownTicks <= 0) {
            spawnOne();
            respawnCountdownTicks = fight.config().num("trail-respawn-delay-ticks", 100);
        }
    }

    private void spawnOne() {
        World world = fight.world();
        Location center = fight.instance().arena().center();
        if (world == null) {
            return;
        }
        double radius = fight.instance().arena().radius();
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        Location rim = center.clone().add(Math.cos(angle) * radius * 0.94, 0, Math.sin(angle) * radius * 0.94);
        rim.setY(world.getHighestBlockYAt(rim.getBlockX(), rim.getBlockZ()));
        Location inward = center.clone().add(Math.cos(angle) * radius * 0.25, 0, Math.sin(angle) * radius * 0.25);
        inward.setY(world.getHighestBlockYAt(inward.getBlockX(), inward.getBlockZ()));

        List<org.bukkit.block.Block> path = FireTrail.pathBetween(world, rim, inward, MAX_TILES);
        if (path.isEmpty()) {
            return;
        }
        FireTrail trail = new FireTrail(fight, path,
                fight.config().num("trail-step-ticks", 10),
                fight.config().dbl("trail-damage", 3.0),
                fight.config().num("trail-fire-ticks", 60),
                fight.config().dbl("trail-burning-add", 14.0),
                null);
        active.add(trail);
    }

    /** Fight teardown: snuff every lit block this manager still owns. */
    void discardAll() {
        for (FireTrail trail : active) {
            trail.expire();
        }
        active.clear();
    }
}
