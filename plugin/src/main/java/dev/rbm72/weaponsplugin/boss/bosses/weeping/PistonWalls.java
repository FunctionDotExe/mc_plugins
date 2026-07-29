package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Piston;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The machine the fight happens inside: four wall sections on a visible cycle, each advancing one course
 * toward the middle until the arena is a small chamber (batch-3 §5.2). Space is the resource, the loss is
 * monotonic, and the group's only lever is <b>jamming</b> — break a section's redstone feed, or wedge a
 * block into its piston face, and that side stops for a while. The Colossus repairs the feed and it
 * resumes.
 * <p>
 * <b>How the shove is actually done, and why.</b> The wall is a real, standing course of blocks that is
 * removed and re-laid one block closer each cycle; the pistons along its base are real piston blocks and
 * their vanilla extend sound fires on every advance. It is <em>not</em> four banks of individually
 * redstone-actuated pistons pushing the course, which is what a literal reading of §5.4 would need: at
 * four sections of twenty-odd blocks that is a thousand block updates and a thousand piston pushes every
 * cycle, and a server-shaped reason to make the room stop closing. Everything a player interacts with —
 * the feed, the pistons, the wall, the crush — is a real block or a real hit.
 * <p>
 * <b>The winnability floor</b> (§5.8's "real work") is {@link #minChamber()}: the walls physically cannot
 * advance past it, it scales with the group size, and P4's chamber is therefore always large enough to
 * fight in however badly the group handled the cycle.
 */
final class PistonWalls {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private static final class Section {
        BlockFace side;
        int inset;
        final List<Block> wall = new ArrayList<>();
        final List<Block> feed = new ArrayList<>();
        int jammedTicks;
        int cycleTicks;
        int jams;
    }

    private final WeepingFight fight;
    private final List<Section> sections = new ArrayList<>();

    private Handler handler;
    private boolean halted;

    /** Ratchet behind {@link #minChamber()} — the smallest floor this fight has ever reported. */
    private int chamberFloor = Integer.MAX_VALUE;

    PistonWalls(WeepingFight fight) {
        this.fight = fight;
    }

    // ------------------------------------------------------------------ build

    void build() {
        if (!sections.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        for (int i = 0; i < SIDES.length; i++) {
            Section section = new Section();
            section.side = SIDES[i];
            section.inset = 0;
            // Staggered so the four sides advance on visibly different beats rather than as one event —
            // a group can only be in one place at a time, and that is the whole tension of the phase.
            section.cycleTicks = fight.config().num("wall-first-delay-ticks", 200) + i * 40;
            layWall(section);
            layFeed(section);
            sections.add(section);
        }
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    private void layWall(Section section) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        clear(section.wall);
        // The new course runs one block inward — straight through the columns this section's redstone
        // feed currently occupies. Clearing the feed here rather than leaving it to layFeed (which runs
        // after) is what stops the wall being laid around the feed and then punched full of holes when
        // layFeed clears those same blocks a moment later.
        clear(section.feed);

        Location centre = fight.instance().arena().center();
        int distance = distanceOf(section);
        // Three courses: tall enough that nobody hops a closing wall, short enough that re-laying four
        // sides of it every cycle stays a few hundred block writes rather than a few thousand.
        int height = Math.max(3, fight.config().num("wall-height", 3));
        Vector inward = new Vector(-section.side.getModX(), 0, -section.side.getModZ());

        for (int offset = -distance; offset <= distance; offset++) {
            int x;
            int z;
            if (section.side == BlockFace.NORTH || section.side == BlockFace.SOUTH) {
                x = centre.getBlockX() + offset;
                z = centre.getBlockZ() + section.side.getModZ() * distance;
            } else {
                x = centre.getBlockX() + section.side.getModX() * distance;
                z = centre.getBlockZ() + offset;
            }
            int baseY = fight.floorY(x, z) + 1;
            for (int y = 0; y < height; y++) {
                Block block = world.getBlockAt(x, baseY + y, z);
                Material material = y == 0 ? Material.PISTON : Material.DEEPSLATE_BRICKS;
                if (!Grief.setMechanicBlock(fight.griefContext(), block, material)) {
                    continue;
                }
                if (material == Material.PISTON && block.getBlockData() instanceof Piston data) {
                    data.setFacing(faceOf(inward));
                    block.setBlockData(data, false);
                }
                section.wall.add(block);
            }
        }
    }

    /** The redstone feed players cut to jam a side — deliberately behind the wall, so jamming means committing. */
    private void layFeed(Section section) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        clear(section.feed);

        Location centre = fight.instance().arena().center();
        int distance = distanceOf(section) - 1;
        int length = Math.max(2, fight.config().num("wall-feed-length", 4));
        for (int i = 0; i < length; i++) {
            int offset = i - length / 2;
            int x;
            int z;
            if (section.side == BlockFace.NORTH || section.side == BlockFace.SOUTH) {
                x = centre.getBlockX() + offset;
                z = centre.getBlockZ() + section.side.getModZ() * distance;
            } else {
                x = centre.getBlockX() + section.side.getModX() * distance;
                z = centre.getBlockZ() + offset;
            }
            int y = fight.floorY(x, z) + 1;
            Block block = world.getBlockAt(x, y, z);
            Material material = i == 0 ? Material.REDSTONE_BLOCK : Material.REDSTONE_WIRE;
            if (Grief.setMechanicBlock(fight.griefContext(), block, material)) {
                section.feed.add(block);
            }
        }
    }

    // ---------------------------------------------------------------- ticking

    void pulse(int intervalTicks) {
        if (halted) {
            return;
        }
        for (Section section : sections) {
            if (section.jammedTicks > 0) {
                section.jammedTicks -= intervalTicks;
                if (section.jammedTicks <= 0) {
                    repair(section);
                }
                markJammed(section);
                continue;
            }
            section.cycleTicks -= intervalTicks;
            if (section.cycleTicks <= fight.config().num("wall-warning-ticks", 40)
                    && section.cycleTicks + intervalTicks > fight.config().num("wall-warning-ticks", 40)) {
                warn(section);
            }
            if (section.cycleTicks > 0) {
                continue;
            }
            section.cycleTicks = fight.config().num("wall-cycle-ticks", 240);
            advance(section);
        }
    }

    private void advance(Section section) {
        if (distanceOf(section) <= minChamber()) {
            return;
        }
        section.inset++;
        crush(section);
        layWall(section);
        layFeed(section);
        Location at = wallCentre(section);
        Fx.sound(at, Sound.BLOCK_PISTON_EXTEND, 1.6f, 0.5f);
        Fx.burst(at.clone().add(0, 1, 0), Particle.BLOCK_CRUMBLE, 24, 0.6);
    }

    /**
     * Crush: heavy damage and a shove inward for anyone standing where the course is about to be, never
     * an instant kill and never a trap — the wall is visibly and audibly moving, and the telegraph
     * (§5.4) is generous.
     */
    private void crush(Section section) {
        double damage = fight.config().dbl("wall-crush-damage", 12.0);
        Location line = wallCentre(section);
        Vector inward = new Vector(-section.side.getModX(), 0, -section.side.getModZ());
        double reach = distanceOf(section) + 1.5;
        for (Player player : fight.combatants()) {
            double along = section.side == BlockFace.NORTH || section.side == BlockFace.SOUTH
                    ? Math.abs(player.getLocation().getZ() - line.getZ())
                    : Math.abs(player.getLocation().getX() - line.getX());
            if (along > 1.6 || player.getLocation().distance(fight.instance().arena().center()) > reach + 2) {
                continue;
            }
            player.damage(damage, fight.instance().entity());
            player.setVelocity(player.getVelocity().add(inward.clone().multiply(0.8)));
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), WeepingFight.SORROW_BLUE, 1.6f, 20, 0.4);
        }
    }

    private void warn(Section section) {
        Location at = wallCentre(section);
        Fx.sound(at, Sound.BLOCK_PISTON_CONTRACT, 1.2f, 0.6f);
        Fx.coloredBurst(at.clone().add(0, 1.5, 0), WeepingFight.SORROW_BLUE, 1.4f, 16, 0.5);
    }

    private void markJammed(Section section) {
        Location at = wallCentre(section);
        Fx.burst(at.clone().add(0, 1.2, 0), Particle.SMOKE, 3, 0.2);
    }

    private void jam(Section section, Player by) {
        if (section.jammedTicks > 0) {
            return;
        }
        section.jams++;
        section.jammedTicks = fight.config().num("wall-jam-ticks",
                fight.playerCount() <= 1 ? 400 : 280);
        Location at = wallCentre(section);
        Fx.sound(at, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.4f, 0.7f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("WALL JAMMED — " + sideName(section.side), NamedTextColor.GREEN),
                    2000L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        if (by != null) {
            Fx.sound(by.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 0.7f);
        }
    }

    /** Jams are temporary by design (§5.4) — it visibly repairs the feed and the side resumes. */
    private void repair(Section section) {
        layFeed(section);
        Location at = wallCentre(section);
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), WeepingFight.SORROW_BLUE, 1.6f, 24, 0.5);
        Fx.sound(at, Sound.BLOCK_ANVIL_USE, 0.9f, 1.5f);
    }

    // ---------------------------------------------------------------- queries

    /** Total jams landed this fight — P1 and P3 both exit on a count of them. */
    int totalJams() {
        int total = 0;
        for (Section section : sections) {
            total += section.jams;
        }
        return total;
    }

    int jammedSections() {
        int jammed = 0;
        for (Section section : sections) {
            if (section.jammedTicks > 0) {
                jammed++;
            }
        }
        return jammed;
    }

    /** The current usable radius — the smallest of the four sides' distances from the centre. */
    int chamberRadius() {
        int smallest = Integer.MAX_VALUE;
        for (Section section : sections) {
            smallest = Math.min(smallest, distanceOf(section));
        }
        return smallest == Integer.MAX_VALUE ? (int) fight.instance().arena().radius() : smallest;
    }

    /**
     * The floor the room can never close past. Scales with the group so five people are not fighting
     * shoulder-to-shoulder in a space built for one, which is §5.5's self-balancing answer: bigger groups
     * keep more space and need more of it.
     */
    int minChamber() {
        // Never rises. The default scales with the party and the party count is live, so someone dying
        // and running back in would otherwise push the floor back outward, past the course physically
        // standing at the old floor. Every geometric read goes through distanceOf, so the result would be
        // a crush line — and a SealedCeiling roof — at a radius the walls are not actually at. The room's
        // loss is monotonic by design; its floor has to be too.
        int floor = fight.config().num("wall-min-chamber", 8 + fight.playerCount());
        chamberFloor = Math.min(chamberFloor, floor);
        return chamberFloor;
    }

    /** P4: the walls stop where they are. Whatever room is left is the room the fight ends in. */
    void halt() {
        halted = true;
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        sections.clear();
    }

    // ----------------------------------------------------------------- helpers

    /** Returns a tracked course of blocks to air and forgets them, through the ledger both ways. */
    private void clear(List<Block> blocks) {
        for (Block block : blocks) {
            Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
        }
        blocks.clear();
    }

    private int distanceOf(Section section) {
        int start = (int) Math.round(fight.instance().arena().radius()
                * fight.config().dbl("wall-start-fraction", 0.85));
        return Math.max(minChamber(), start - section.inset);
    }

    private Location wallCentre(Section section) {
        Location centre = fight.instance().arena().center().clone();
        int distance = distanceOf(section);
        return centre.add(section.side.getModX() * distance, 1, section.side.getModZ() * distance);
    }

    private static BlockFace faceOf(Vector direction) {
        if (Math.abs(direction.getX()) >= Math.abs(direction.getZ())) {
            return direction.getX() >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static String sideName(BlockFace side) {
        return side.name().charAt(0) + side.name().substring(1).toLowerCase();
    }

    private final class Handler implements Listener {

        /** Cutting the feed — the primary jam, and the one that works with no items at all. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            for (Section section : sections) {
                if (section.feed.contains(event.getBlock())) {
                    jam(section, event.getPlayer());
                    return;
                }
            }
        }

        /**
         * Wedging a block into a piston face — §5.4's second jam, and the reason the arena hands out
         * building blocks. Any block placed directly against a section's wall counts.
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block placed = event.getBlock();
            for (Section section : sections) {
                for (BlockFace face : SIDES) {
                    if (section.wall.contains(placed.getRelative(face))) {
                        jam(section, event.getPlayer());
                        return;
                    }
                }
            }
        }
    }
}
