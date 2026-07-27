package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.Arena;
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
import java.util.Iterator;
import java.util.List;

/**
 * The fight's own dead, physically on the floor.
 * <p>
 * Every undead the group kills leaves a real heap of bone blocks where it fell, and unless somebody
 * <b>mines it out</b> before its timer runs down the heap gets back up as a fresh add. Nothing here is
 * a particle or a marker: the pile is bone blocks in the world, so it obstructs movement, breaks
 * sightlines, ruins a chokepoint that was working a minute ago, and has to be removed with a pickaxe
 * like any other block. Deleting every particle call in this file changes nothing about how it plays.
 * <p>
 * That is also where the boss's multiplayer scaling lives. A five-player group kills far more than a
 * solo player, so a five-player group buries its own arena far faster — the difficulty is the group's
 * own efficiency, and the uncomfortable correct answer in P3 is sometimes to stop killing things.
 * <p>
 * Every block written here goes through {@link Grief#setMechanicBlock}, so the whole corpse floor is in
 * the arena ledger and the arena comes back clean when the fight ends — and because it is ledgered,
 * {@link dev.rbm72.weaponsplugin.boss.grief.LedgerDropListener} sees to it that mining a pile out pays
 * nothing. The bones are an obstacle to clear, not a resource to harvest; the alternative is a bone farm
 * that pays best to the group playing the mechanic hardest.
 */
final class CorpseField {

    /** Kept low deliberately: a pile is a real obstacle, and a two-block heap already blocks a corridor. */
    private static final int PILE_HEIGHT = 2;
    private static final Material PILE_BLOCK = Material.BONE_BLOCK;
    /** Ground the pile sits on, so a cleared plot still reads as disturbed earth after the bones are gone. */
    private static final Material PLOT_BLOCK = Material.SOUL_SOIL;
    /**
     * Gap between rattles on one pile. A pile the horde cap is holding down sits in its warning window
     * indefinitely, and forty of those rattling on every pulse is a wall of noise that buries the cues
     * the group actually needs to react to.
     */
    private static final int WARN_REPEAT_TICKS = 20;

    private final NecroFight fight;
    private final List<Pile> piles = new ArrayList<>();

    private int buriedTotal;
    private int clearedTotal;
    private boolean capWarned;

    CorpseField(NecroFight fight) {
        this.fight = fight;
    }

    /** One heap of bones: the blocks it is made of, and how long until what's left of it stands up again. */
    private static final class Pile {

        private final List<Block> bones;
        private int riseTicksLeft;
        private int warnCooldownLeft;

        private Pile(List<Block> bones, int riseTicksLeft) {
            this.bones = bones;
            this.riseTicksLeft = riseTicksLeft;
        }

        /** Blocks still in place — the pile is cleared the moment this reaches zero. */
        private int remaining() {
            int count = 0;
            for (Block block : bones) {
                if (block.getType() == PILE_BLOCK) {
                    count++;
                }
            }
            return count;
        }

        private Location centre() {
            Block first = bones.get(0);
            return first.getLocation().add(0.5, 0, 0.5);
        }
    }

    /**
     * Buries whatever just died at {@code where}. Refuses to stack a heap on top of a player — a block
     * placed inside somebody suffocates them, and "you were standing where a zombie died" is not a
     * mechanic anybody can read or counter.
     */
    void bury(Location where) {
        World world = fight.world();
        if (world == null || where.getWorld() == null || !where.getWorld().equals(world)) {
            return;
        }
        int cap = fight.config().num("corpse-max-piles", 40);
        if (piles.size() >= cap) {
            if (!capWarned) {
                capWarned = true;
                fight.plugin().getLogger().info(() -> "Necro Overlord's corpse floor hit its " + cap
                        + "-pile cap — further kills leave no new pile until some are cleared.");
            }
            return;
        }
        if (occupiedByPlayer(where)) {
            return;
        }

        List<Block> bones = new ArrayList<>(PILE_HEIGHT);
        Block ground = world.getBlockAt(where.getBlockX(), where.getBlockY() - 1, where.getBlockZ());
        if (!ground.getType().isAir()) {
            Grief.setMechanicBlock(fight.griefContext(), ground, PLOT_BLOCK);
        }
        for (int y = 0; y < PILE_HEIGHT; y++) {
            Block block = world.getBlockAt(where.getBlockX(), where.getBlockY() + y, where.getBlockZ());
            if (!block.getType().isAir() || !Grief.setMechanicBlock(fight.griefContext(), block, PILE_BLOCK)) {
                break;
            }
            bones.add(block);
        }
        if (bones.isEmpty()) {
            return;
        }

        piles.add(new Pile(bones, Math.max(20, fight.config().num("corpse-rise-ticks", 320))));
        buriedTotal++;
        NecroFight.necroticFlourish(where, Sound.BLOCK_BONE_BLOCK_PLACE, 0.6f);
    }

    /**
     * Runs every pile's clock down by {@code intervalTicks}.
     * <p>
     * {@code riseMultiplier} lets a phase make the floor rise faster or slower than the baseline without
     * a second set of config keys — P3 is where the reanimation timer becomes the phase's whole pressure.
     */
    void pulse(int intervalTicks, double riseMultiplier) {
        int warnTicks = Math.max(20, fight.config().num("corpse-rise-warning-ticks", 40));
        for (Iterator<Pile> it = piles.iterator(); it.hasNext(); ) {
            Pile pile = it.next();
            if (pile.remaining() == 0) {
                // Mined out. This is the win state for a pile and it is the only one that costs the
                // group time rather than health, which is the entire point of the mechanic.
                it.remove();
                clearedTotal++;
                continue;
            }
            pile.riseTicksLeft -= (int) Math.round(intervalTicks * Math.max(0.1, riseMultiplier));
            if (pile.riseTicksLeft <= warnTicks) {
                pile.warnCooldownLeft -= intervalTicks;
                if (pile.warnCooldownLeft <= 0) {
                    warnRise(pile);
                    pile.warnCooldownLeft = WARN_REPEAT_TICKS;
                }
            }
            if (pile.riseTicksLeft > 0) {
                continue;
            }
            if (!reanimate(pile)) {
                // The horde is at its performance cap, so the bones stay down — but only until there is
                // room, not for another full cycle. A pile blocked by the cap that re-armed its whole
                // timer would sit there indefinitely while the group is killing fast enough to keep the
                // horde full, which is exactly when the floor is filling fastest: the piles that clog P3
                // worst would be the ones least able to clear themselves. Short retry instead, so the
                // floor drains the moment the horde thins and the phase exit stays reachable by mining.
                pile.riseTicksLeft = Math.max(5, fight.config().num("corpse-rise-retry-ticks", 40));
                continue;
            }
            it.remove();
        }
    }

    /** Live piles still on the floor — what P3's exit condition measures. */
    int liveCount() {
        int count = 0;
        for (Pile pile : piles) {
            if (pile.remaining() > 0) {
                count++;
            }
        }
        return count;
    }

    /** Every pile this fight has ever raised, so a phase can tell "cleared the floor" from "never had one". */
    int buriedTotal() {
        return buriedTotal;
    }

    /**
     * Piles the group has actually mined out, cumulative and never decreasing. This is P3's proof of
     * headway — {@code liveCount()} falling is not, because a pile that stands up as an undead also
     * leaves the floor, and the boss doing his own job is not the group making progress.
     */
    int clearedTotal() {
        return clearedTotal;
    }

    /**
     * The rise telegraph: the bones rattle and soul-fire licks off the heap for the last
     * {@code corpse-rise-warning-ticks} before it stands up. Well past the roster's 15-tick minimum,
     * because the counterplay here is swinging a pickaxe rather than stepping sideways.
     */
    private void warnRise(Pile pile) {
        Location centre = pile.centre();
        Fx.burst(centre.clone().add(0, 1.2, 0), Particle.SOUL, 6, 0.3);
        Fx.sound(centre, Sound.BLOCK_BONE_BLOCK_HIT, 0.7f, 0.5f);
    }

    /** @return false if the horde is full, in which case the pile must stay where it is. */
    private boolean reanimate(Pile pile) {
        Location centre = pile.centre();
        if (!fight.horde().hasRoom()) {
            return false;
        }
        for (Block block : pile.bones) {
            if (block.getType() == PILE_BLOCK) {
                Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
            }
        }
        Fx.burst(centre.clone().add(0, 1, 0), Particle.SCULK_SOUL, 30, 0.5);
        Fx.blockBurst(centre.clone().add(0, 1, 0), PILE_BLOCK, 18, 0.4);
        Fx.sound(centre, Sound.BLOCK_BONE_BLOCK_BREAK, 1.0f, 0.5f);
        fight.horde().raiseOne(centre);
        return true;
    }

    private boolean occupiedByPlayer(Location where) {
        for (Player player : Arena.combatants(where, 1.5)) {
            if (player.getLocation().getBlockY() >= where.getBlockY() - 1
                    && player.getLocation().getBlockY() <= where.getBlockY() + PILE_HEIGHT) {
                return true;
            }
        }
        return false;
    }
}
