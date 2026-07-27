package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Judgment: the ceiling failing, one anvil at a time, and the anvils <em>staying</em>.
 * <p>
 * This is the mechanic that writes the fight's history into the floor (§0.1's "the world is the memory
 * of the fight"). Every volley leaves real anvil blocks embedded where they landed, and by P4 the throne
 * room is a field of them. That accretion is load-bearing in three directions at once:
 * <ul>
 *   <li>it is the <b>anti-camping tax</b> — a group that holds one spot has that spot converted to
 *       anvils under them, and camped long enough they have walled themselves in;</li>
 *   <li>it is the <b>cover</b> P4's Execution charge is broken on, so the terrain the fight created is
 *       the tool that saves you;</li>
 *   <li>it is a <b>readable record</b> — you can look at the floor and tell how the fight has gone.</li>
 * </ul>
 * Every anvil goes down through the arena ledger, so all of it is undone when the fight ends. That is
 * precisely what licenses a mechanic this destructive: because the room comes back, the room can be
 * ruined (§0.3).
 */
final class Judgment {

    /** The three anvil states, used in order so a repeatedly-hit tile visibly degrades. */
    private static final Material[] ANVIL_STAGES = {
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL
    };

    private final KingFight fight;

    /** Marks currently counting down: where they are, and how long until the anvil arrives. */
    private final List<Mark> marks = new ArrayList<>();
    private final List<Block> landed = new ArrayList<>();

    private int volleyCountdown;
    private boolean running;

    Judgment(KingFight fight) {
        this.fight = fight;
    }

    private static final class Mark {

        private final Location where;
        private final int totalTicks;
        private int ticksLeft;

        private Mark(Location where, int ticks) {
            this.where = where;
            this.totalTicks = ticks;
            this.ticksLeft = ticks;
        }
    }

    /** Anvils standing in the arena right now — the readout's measure of how ruined the room is. */
    int landedCount() {
        landed.removeIf(block -> !isAnvil(block.getType()));
        return landed.size();
    }

    void setRunning(boolean running) {
        this.running = running;
    }

    void pulse(int intervalTicks) {
        resolveMarks(intervalTicks);
        if (!running) {
            return;
        }
        volleyCountdown -= intervalTicks;
        if (volleyCountdown <= 0) {
            volleyCountdown = Math.max(40, fight.config().num("judgment-interval-ticks", 130));
            volley();
        }
    }

    /**
     * One volley: {@code 2 + players} anvils, spread across marked players rather than piled on one.
     * Count scaling only (§0.2 rule 7) — an anvil never hits harder for a bigger group, there are simply
     * more of them and more of the floor ends up gone.
     */
    private void volley() {
        List<Player> present = fight.combatants();
        if (present.isEmpty()) {
            return;
        }
        int count = fight.config().num("judgment-base-anvils", 2) + present.size();
        int telegraph = Math.max(20, fight.config().num("judgment-telegraph-ticks", 30));
        for (int i = 0; i < count; i++) {
            // Cycling the player list rather than picking randomly guarantees every player is marked
            // before anyone is marked twice — the design says "spread across marked players", and a
            // random pick routinely triple-marks one person and leaves another untouched.
            Player victim = present.get(i % present.size());
            Location spot = victim.getLocation().clone();
            spot.setY(spot.getBlockY());
            marks.add(new Mark(spot, telegraph));
        }
        Location overhead = fight.instance().arena().center().add(0, 8, 0);
        Fx.sound(overhead, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1.6f, 0.5f);
        Fx.burst(overhead, Particle.FALLING_DUST, 40, 6.0);
    }

    /**
     * Ticks every armed mark and drops the anvil on the ones that are out of time. The ring tightens as
     * it counts down (§0.2 rule 3 — a telegraph has to say "any moment now", not just "somewhere in this
     * window"), and the payload lands exactly on the tile the ring drew.
     */
    private void resolveMarks(int intervalTicks) {
        for (Iterator<Mark> it = marks.iterator(); it.hasNext(); ) {
            Mark mark = it.next();
            mark.ticksLeft -= intervalTicks;
            if (mark.ticksLeft > 0) {
                double progress = 1.0 - (double) mark.ticksLeft / Math.max(1, mark.totalTicks);
                Telegraph.dangerZone(mark.where, fight.config().dbl("judgment-radius", 1.6), progress);
                continue;
            }
            it.remove();
            drop(mark.where);
        }
    }

    private void drop(Location where) {
        World world = where.getWorld();
        if (world == null) {
            return;
        }
        Grief.dropAsBlock(fight.griefContext(), where, nextStage(),
                fight.config().dbl("judgment-drop-height", 14.0),
                fight.config().dbl("judgment-damage", 22.0),
                fight.config().dbl("judgment-radius", 1.6),
                landedAt -> {
                    Block block = landedAt.getBlock();
                    if (isAnvil(block.getType())) {
                        landed.add(block);
                    }
                });
    }

    /**
     * Cycles the anvil variants so the field reads as wreckage rather than as a tidy grid of identical
     * blocks. Purely cosmetic; all three behave the same as cover.
     */
    private Material nextStage() {
        return ANVIL_STAGES[Math.floorMod(landed.size(), ANVIL_STAGES.length)];
    }

    private static boolean isAnvil(Material type) {
        return type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL;
    }

    /**
     * Whether an anvil column stands between these two points — P4's solo answer to the Execution
     * charge, where breaking line of sight with the terrain replaces a second player's body.
     * <p>
     * Walks the straight line between them a third of a block at a time and asks the world, rather than
     * consulting {@link #landed}: the group is welcome to have stacked their own blocks in the way, and
     * a check that only recognised the boss's own anvils would refuse to see cover the players built
     * themselves.
     */
    boolean blockedBetween(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || to.getWorld() == null || !world.equals(to.getWorld())) {
            return false;
        }
        double distance = from.distance(to);
        if (distance < 0.5) {
            return false;
        }
        var step = to.toVector().subtract(from.toVector()).normalize().multiply(0.34);
        Location cursor = from.clone().add(0, 0.6, 0);
        for (double travelled = 0; travelled < distance; travelled += 0.34) {
            cursor.add(step);
            if (cursor.getBlock().getType().isSolid()) {
                return true;
            }
        }
        return false;
    }
}
