package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.TickDamage;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * One scripted crawl of real fire along a precomputed floor path — the implementation note's own
 * warning made concrete: "vanilla fire spread is too random to telegraph — needs a controlled crawl,
 * i.e. YOU drive the fire block placement along a precomputed path rather than letting Bukkit's own
 * fire-spread physics run wild." This is that drive: one block ignites per {@link #stepTicks}, in path
 * order, so a trail's direction and destination are readable the instant it starts.
 * <p>
 * Reused for two different jobs in the mechanics table: an ambient <b>Fire Trail</b> (no destination
 * that matters — it just burns whatever it reaches) and a <b>Fuse Line</b> (its destination is a TNT
 * cluster, wired through {@link #onArrive}). Both share every rule the design gives fire trails:
 * <ul>
 *   <li><b>Doused.</b> Every pulse re-checks the most recently lit block. A player who poured water on
 *       it (or it simply burned out) means the block is no longer {@link Material#FIRE}, and the crawl
 *       stops there for good — "douse a segment to break it" (§1.4 Fire Trail row).</li>
 *   <li><b>Blocked.</b> Before igniting the next tile, it must still be a plantable floor spot. A
 *       player who placed <em>any</em> block on the remaining path denies that tile outright, so the
 *       crawl halts without needing a dedicated listener — "break the line with any placed block"
 *       (§1.4 Fuse Line row) falls out of the step check for free.</li>
 * </ul>
 * Neither failure is treated as worse than success for the boss — the trail simply goes quiet, which is
 * exactly what "douse it" or "wall it off" should feel like (Pitfall 3: failing a mechanic must not be
 * better than doing it, and stopping a fuse is never worse than letting it run).
 */
final class FireTrail {

    private final InfernoFight fight;
    private final List<Block> path;
    private final int stepTicks;
    private final double burnDamage;
    private final int fireTicksOnContact;
    private final double burningAdd;
    private final Runnable onArrive;

    private int headIndex;
    private int ticksSinceStep;
    private boolean extinguished;
    private boolean blocked;
    private boolean arrived;

    /**
     * @param path              floor columns in travel order, one block apart; {@link FireTrails#pathBetween}
     *                          builds these
     * @param onArrive          nullable — fired exactly once when the crawl reaches the end of its path.
     *                          A fuse line's cluster ignition; null for an ambient trail, which simply
     *                          keeps its lit blocks until {@link #expire()} clears them.
     */
    FireTrail(InfernoFight fight, List<Block> path, int stepTicks, double burnDamage, int fireTicksOnContact,
              double burningAdd, Runnable onArrive) {
        this.fight = fight;
        this.path = path;
        this.stepTicks = Math.max(1, stepTicks);
        this.burnDamage = burnDamage;
        this.fireTicksOnContact = fireTicksOnContact;
        this.burningAdd = burningAdd;
        this.onArrive = onArrive;
    }

    boolean extinguished() {
        return extinguished;
    }

    boolean blocked() {
        return blocked;
    }

    boolean arrived() {
        return arrived;
    }

    /** Cut short — dead either way, whichever solved it. */
    boolean resolved() {
        return extinguished || blocked || arrived;
    }

    /** How far the crawl has travelled, for a readout progress bar. */
    double fraction() {
        return path.isEmpty() ? 1.0 : Math.min(1.0, headIndex / (double) path.size());
    }

    /** Ticks driving one step; called every mechanic pulse with the ticks elapsed since the last call. */
    void pulse(int elapsedTicks) {
        if (resolved()) {
            return;
        }
        ticksSinceStep += elapsedTicks;
        if (headIndex > 0 && ticksSinceStep < stepTicks) {
            return;
        }
        ticksSinceStep = 0;
        if (headIndex > 0) {
            Block last = path.get(headIndex - 1);
            if (last.getType() != Material.FIRE) {
                extinguished = true;
                Fx.burst(last.getLocation().add(0.5, 0.3, 0.5), Particle.SMOKE, 14, 0.3);
                return;
            }
        }
        if (headIndex >= path.size()) {
            arrived = true;
            if (onArrive != null) {
                onArrive.run();
            }
            return;
        }
        Block next = path.get(headIndex);
        if (!plantable(next)) {
            blocked = true;
            Fx.burst(next.getLocation().add(0.5, 0.6, 0.5), Particle.LARGE_SMOKE, 18, 0.35);
            Fx.sound(next.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
            return;
        }
        ignite(next);
        headIndex++;
    }

    private boolean plantable(Block block) {
        return (block.getType().isAir() || block.getType() == Material.FIRE)
                && block.getRelative(0, -1, 0).getType().isSolid();
    }

    private void ignite(Block block) {
        Grief.setMechanicBlock(fight.griefContext(), block, Material.FIRE);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        Fx.coloredBurst(at, InfernoFight.EMBER, 1.0f, 6, 0.25);
        Fx.sound(at, Sound.BLOCK_FIRE_AMBIENT, 0.6f, 1.1f);
        for (Player player : Arena.combatants(at, 1.1)) {
            TickDamage.apply(fight.instance(), player, burnDamage);
            player.setFireTicks(Math.max(player.getFireTicks(), fireTicksOnContact));
            fight.burning().add(player, burningAdd);
        }
    }

    /** Snuffs every lit block this trail placed — an ambient trail's natural end, and fight teardown. */
    void expire() {
        for (int i = 0; i < headIndex && i < path.size(); i++) {
            Block block = path.get(i);
            if (block.getType() == Material.FIRE) {
                Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
            }
        }
    }

    /** Straight-line floor path from {@code from} toward {@code to}, capped at {@code maxTiles}. */
    static List<Block> pathBetween(World world, Location from, Location to, int maxTiles) {
        List<Block> path = new ArrayList<>();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, Math.min(maxTiles, (int) Math.round(distance)));
        double stepX = dx / steps;
        double stepZ = dz / steps;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 1; i <= steps; i++) {
            int x = (int) Math.floor(from.getX() + stepX * i);
            int z = (int) Math.floor(from.getZ() + stepZ * i);
            if (x == lastX && z == lastZ) {
                continue;
            }
            lastX = x;
            lastZ = z;
            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y, z);
            path.add(ground.getRelative(0, 1, 0));
        }
        return path;
    }
}
