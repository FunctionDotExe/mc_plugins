package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The staged fluid-fill engine: water climbs the arena in layers over real time (P1's "the shoreline
 * moving", P2's full submersion) and drains it in one dramatic event in P4 ("Beaching").
 * <p>
 * This is the system batch-2 §6.1 calls out as shared conceptually with the Inferno Warlord's rising
 * lava — same shape (a staged volumetric fill, bounded per-pulse so it never spikes the tick), built
 * self-contained here rather than depending on that boss's own package.
 * <p>
 * <b>Fill shape.</b> The arena floor is treated as flat at {@link #floorY()} (the arena's fixed centre
 * Y — every other prop in this fight anchors to the same reference, per {@code TickingMechanic}'s own
 * "anchor to the centre, not the boss" rule). Rising is layer-by-layer: each new Y layer is a disc of
 * radius {@code water-flood-radius-fraction * arena radius}, and only {@code AIR} blocks in that disc
 * are converted — a real wall, pillar, or overhang the arena was built with is never touched, which is
 * what lets it double as physical cover once the water is up. A handful of columns are permanently
 * excluded from the fill (see {@link #isPocketColumn}) — literal dry chimneys through the flood, which
 * <i>are</i> this fight's "air pockets under overhangs": real, static, and learnable, rather than a
 * proximity flag with nothing physical backing it.
 * <p>
 * <b>Ledger discipline.</b> Every write here is {@link Grief#setBlock} — batch-2's own instruction is
 * explicit that the rising tiers and the drain are destructive terrain, not the mechanic interaction
 * itself (that split is conduits/columns/whirlpool, which use {@link Grief#setMechanicBlock}). A
 * server with grief disabled therefore gets no real water at all from this boss; that is the same
 * trade-off {@code Flooding} (Storm Tyrant) already accepts for the same reason.
 */
final class WaterLevel {

    private final LeviathanFight fight;

    /** Y layers already fully queued for filling, up to and including this one. */
    private int nextLayerY;
    /** Target Y (exclusive-safe: fill layers up to and including this one). */
    private int targetY;
    private boolean draining;

    private final Deque<Block> pending = new ArrayDeque<>();
    /** Every block this fight has turned to water, roughly bottom-to-top since layers fill in order. */
    private final List<Block> wet = new ArrayList<>();

    /** (dx, dz) column offsets from the arena centre that are never filled — the fight's air pockets. */
    private int[][] pocketColumns = new int[0][];

    private int ambientCooldown;

    WaterLevel(LeviathanFight fight) {
        this.fight = fight;
    }

    // --------------------------------------------------------------------- geometry

    /** First fillable Y — one block above the arena's fixed floor. */
    int floorY() {
        return fight.instance().arena().center().getBlockY() + 1;
    }

    private int fullSubmergeHeight() {
        return Math.max(3, fight.config().num("water-full-submerge-height", 9));
    }

    private double floodRadius() {
        double fraction = fight.config().dbl("water-flood-radius-fraction", 0.9);
        return fight.instance().arena().radius() * Math.max(0.1, Math.min(1.0, fraction));
    }

    /** 0..1 fill of the target height, for readouts — not how much has physically landed yet. */
    double surfaceFraction() {
        int height = fullSubmergeHeight();
        return height <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) (targetY - floorY()) / height));
    }

    boolean isFullySubmerged() {
        return targetY >= floorY() + fullSubmergeHeight() && pending.isEmpty() && nextLayerY > targetY;
    }

    // --------------------------------------------------------------------- rising

    /**
     * Raises the target height to {@code fraction} of full submersion (0 = dry, 1 = fully submerged).
     * Never lowers the target — draining is a separate, one-way event. The rise itself plays out over
     * many {@link #pulse} calls, which <em>is</em> the "water level visibly and audibly changes in
     * stages, with warning" beat (batch-2 §3.4, Tidal Surge): the multi-second climb is the warning.
     */
    void raiseTo(double fraction) {
        if (draining) {
            return;
        }
        int newTarget = floorY() + (int) Math.round(fullSubmergeHeight() * Math.max(0.0, Math.min(1.0, fraction)));
        if (newTarget <= targetY) {
            return;
        }
        targetY = newTarget;
        if (nextLayerY < floorY()) {
            nextLayerY = floorY();
            buildPocketColumns();
        }
        Location centre = fight.instance().arena().center();
        Fx.coloredRing(centre, LeviathanFight.PALE, 1.5f, floodRadius(), 40, 0);
        Fx.sound(centre, Sound.ENTITY_GENERIC_SWIM, 1.2f, 0.5f);
        Fx.sound(centre, Sound.WEATHER_RAIN_ABOVE, 1.0f, 0.6f);
    }

    /** One pulse of physical fill work — pops up to {@code budget} blocks off the queue and wets them. */
    void pulse(int budget) {
        if (draining) {
            drainPulse(budget);
            return;
        }
        if (pending.isEmpty() && nextLayerY <= targetY) {
            queueLayer(nextLayerY);
            nextLayerY++;
        }
        int placed = 0;
        while (placed < budget && !pending.isEmpty()) {
            Block block = pending.poll();
            if (Grief.setBlock(fight.griefContext(), block, Material.WATER)) {
                wet.add(block);
            }
            placed++;
        }
        if (placed > 0 && ambientCooldown-- <= 0) {
            ambientCooldown = 8;
            Location centre = fight.instance().arena().center();
            Fx.burst(centre.clone().add(0, targetY - floorY() + 1, 0), Particle.BUBBLE_COLUMN_UP, 20, floodRadius() * 0.4);
        }
    }

    private void queueLayer(int y) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location centre = fight.instance().arena().center();
        double radius = floodRadius();
        int ir = (int) Math.ceil(radius);
        double r2 = radius * radius;
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                if (x * x + z * z > r2 || isPocketColumn(x, z)) {
                    continue;
                }
                Block block = world.getBlockAt(centre.getBlockX() + x, y, centre.getBlockZ() + z);
                if (block.getType().isAir()) {
                    pending.add(block);
                }
            }
        }
    }

    // --------------------------------------------------------------------- draining

    /**
     * P4's "Beaching": the arena drains in one dramatic event rather than another staged rise. Driven
     * by repeated {@link #pulse} calls from {@link LowTidePhase}'s own short-lived task (P4 has no
     * running {@link LeviathanPhaseMechanic} to piggyback on — see that class for why).
     */
    void beginDrain() {
        draining = true;
        pending.clear();
    }

    /**
     * Pops from the <em>end</em> of {@link #wet} — since layers were queued and filled in ascending Y
     * order, the tail is the most recently (highest) filled water, so draining back-to-front reads as
     * the surface visibly dropping rather than the floor patchily drying out first.
     */
    private void drainPulse(int budget) {
        int residual = residualCount();
        int removed = 0;
        while (removed < budget && wet.size() > residual) {
            Block block = wet.remove(wet.size() - 1);
            if (block.getType() == Material.WATER) {
                Grief.setBlock(fight.griefContext(), block, Material.AIR);
            }
            removed++;
        }
    }

    private int residualCount() {
        double fraction = fight.config().dbl("water-drain-residual-fraction", 0.08);
        return (int) Math.round(wet.size() * Math.max(0.0, Math.min(0.5, fraction)));
    }

    boolean drainComplete() {
        return draining && wet.size() <= residualCount();
    }

    // --------------------------------------------------------------------- air pockets

    /**
     * Picks a handful of fixed columns, scattered at a mid-radius ring, that {@link #queueLayer} will
     * never fill — real dry chimneys through the flood, from floor to open sky, marked with a lit prop
     * so they double as a landmark. Chosen once, at the first rise, so "learn where the air is"
     * (batch-2 §3.3) has a fixed answer rather than a moving target.
     */
    private void buildPocketColumns() {
        int count = Math.max(0, fight.config().num("water-pocket-count", 3));
        int radius = Math.max(0, fight.config().num("water-pocket-radius", 1));
        double fraction = fight.config().dbl("water-pocket-fraction", 0.62);
        double arenaRadius = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        List<int[]> offsets = new ArrayList<>();
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / Math.max(1, count);
            int cx = (int) Math.round(Math.cos(angle) * arenaRadius);
            int cz = (int) Math.round(Math.sin(angle) * arenaRadius);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    offsets.add(new int[] {cx + dx, cz + dz});
                }
            }
        }
        pocketColumns = offsets.toArray(new int[0][]);
        markPockets(count, radius, arenaRadius, startAngle);
    }

    private boolean isPocketColumn(int dx, int dz) {
        for (int[] col : pocketColumns) {
            if (col[0] == dx && col[1] == dz) {
                return true;
            }
        }
        return false;
    }

    /** A sea-lantern marker at the top of each pocket column — a landmark, not a functional block. */
    private void markPockets(int count, int radius, double ringRadius, double startAngle) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location centre = fight.instance().arena().center();
        int markY = floorY() + fullSubmergeHeight();
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / Math.max(1, count);
            int cx = centre.getBlockX() + (int) Math.round(Math.cos(angle) * ringRadius);
            int cz = centre.getBlockZ() + (int) Math.round(Math.sin(angle) * ringRadius);
            Block marker = world.getBlockAt(cx, markY, cz);
            if (marker.getType().isAir()) {
                Grief.setMechanicBlock(fight.griefContext(), marker, Material.SEA_LANTERN);
            }
        }
    }
}
