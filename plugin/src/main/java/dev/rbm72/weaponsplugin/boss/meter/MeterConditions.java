package dev.rbm72.weaponsplugin.boss.meter;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * The library of conditions the four meter skins are actually built from.
 * <p>
 * Every entry here exists because at least one of the designs asks for it in so many words — proximity
 * to the boss, standing on a particular block, being in water, standing near a physical prop, holding
 * a fire source, moving continuously. They live in one place rather than in each boss because the
 * expensive ones (the block scans behind {@link #nearBlock}) have a performance shape that has to be
 * got right once: a naive cubic scan per player per tick is how a meter turns into the tick that
 * eats the server.
 * <p>
 * <b>All of these are physical, not statistical.</b> That is the point of §0.1 of the design — the
 * cure for Chill is a real lit campfire in the world, not a "near heat" flag a boss sets. Every
 * condition here reads real blocks, real fluid state, or real item stacks, so a player can look at
 * the world and know what their meter is about to do.
 */
public final class MeterConditions {

    /**
     * How far above and below the player's feet a proximity scan reaches. Deliberately shallow: props
     * that cure a meter (campfires, lightning rods) sit on the floor the player is standing on, and a
     * tall scan mostly buys the cost of scanning ceiling and basement for nothing.
     */
    private static final int PROP_SCAN_VERTICAL = 2;

    /** Hard ceiling on any scan radius a caller may ask for, so no boss config can make a scan quadratic-expensive. */
    private static final double MAX_SCAN_RADIUS = 8.0;

    private MeterConditions() {
    }

    /** Always true — the passive trickle every skin except Void Echo starts from. */
    public static MeterCondition always() {
        return ctx -> true;
    }

    /** Never true. Useful as the "this skin has no cure of that kind" placeholder. */
    public static MeterCondition never() {
        return ctx -> false;
    }

    /** Within {@code radius} blocks of the boss, horizontally (see {@link MeterContext#distanceToBoss()}). */
    public static MeterCondition nearBoss(double radius) {
        return ctx -> ctx.distanceToBoss() <= radius;
    }

    /** Beyond {@code radius} of the boss — the "ranged players pay a different rent" half of a skin. */
    public static MeterCondition awayFromBoss(double radius) {
        return ctx -> ctx.distanceToBoss() > radius;
    }

    /**
     * Standing on (or in) any of {@code materials} — blue ice, mud, soul sand, sculk, whatever the
     * fight has converted the floor into.
     * <p>
     * Checks the block at the feet <em>and</em> the block below, because the two cases the designs care
     * about sit on opposite sides of that line: you stand <em>on</em> ice and mud, but you stand
     * <em>in</em> fire, powder snow and water. Asking only one of the two silently drops half the rules.
     */
    public static MeterCondition standingOn(Material... materials) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (Material material : materials) {
            set.add(material);
        }
        return ctx -> set.contains(ctx.blockAtFeet().getType()) || set.contains(ctx.blockBelow().getType());
    }

    /** Real fluid state, not a block-type guess — waterlogged stairs conduct exactly like an open pool. */
    public static MeterCondition inWater() {
        return ctx -> ctx.player().isInWater();
    }

    /**
     * The player is physically burning or standing in flame — the Plague Warden's cure, expressed as
     * the thing it actually is rather than as proximity to a pyre entity.
     */
    public static MeterCondition inFire() {
        return ctx -> ctx.player().getFireTicks() > 0
                || isFire(ctx.blockAtFeet())
                || isFire(ctx.blockBelow());
    }

    /**
     * Within {@code radius} of a block of one of {@code materials}, scanning the horizontal disc around
     * the player at floor level.
     * <p>
     * This is the shape of every "go stand near the prop" cure in the roster (campfires, lightning
     * rods, pyres) and it is deliberately a <em>block</em> scan rather than a registry of prop
     * locations: props get snuffed, destroyed, buried by falling ice and rebuilt by players, and a
     * registry drifts out of sync with all four of those. Reading the world is the only version that
     * cannot lie to the player about whether the campfire in front of them counts.
     */
    public static MeterCondition nearBlock(double radius, Material... materials) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (Material material : materials) {
            set.add(material);
        }
        return nearBlockMatching(radius, block -> set.contains(block.getType()));
    }

    /**
     * The Frost Queen's cure specifically: a campfire that is actually <em>lit</em>. She snuffs them
     * one per pulse in her final phase, so a scan that matched the block type alone would keep curing
     * players standing at a dead fire — which reads as the mechanic being broken rather than as her
     * having taken the heat away.
     */
    public static MeterCondition nearLitCampfire(double radius) {
        return nearBlockMatching(radius, block -> {
            Material type = block.getType();
            if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
                return false;
            }
            BlockData data = block.getBlockData();
            return !(data instanceof Lightable lightable) || lightable.isLit();
        });
    }

    /**
     * Touching a lightning rod — the Storm Tyrant's cure. A small radius rather than a strict block
     * equality: the rod occupies one block and a player standing against it is in the block beside it,
     * so exact matching would make the cure feel broken about half the time.
     */
    public static MeterCondition touchingBlock(Material material) {
        return nearBlock(1.8, material);
    }

    /** Holding {@code materials} in either hand — a carried torch, blaze rod, or flint and steel. */
    public static MeterCondition holding(Material... materials) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (Material material : materials) {
            set.add(material);
        }
        return ctx -> {
            var inv = ctx.player().getInventory();
            return heldMatches(inv.getItemInMainHand(), set) || heldMatches(inv.getItemInOffHand(), set);
        };
    }

    /**
     * Actually travelling, at {@code blocksPerSecond} or better. This is Void Echo's cure: the stack
     * bleeds off while you keep moving and does not while you stand and trade hits.
     * <p>
     * Measured from the meter's own position history rather than from velocity, because velocity reads
     * non-zero for a player being shoved around by knockback, levitation, or a wind charge — all three
     * of which are things the boss did <em>to</em> you, and none of which should count as you doing the
     * mechanic.
     */
    public static MeterCondition moving(double blocksPerSecond) {
        return ctx -> ctx.blocksPerSecond() >= blocksPerSecond;
    }

    /** The inverse of {@link #moving} — the "camping is what this meter punishes" half of a skin. */
    public static MeterCondition stationary(double blocksPerSecond) {
        return ctx -> ctx.blocksPerSecond() < blocksPerSecond;
    }

    /** The player's own meter is already at or past {@code fraction} — for a skin that accelerates as it fills. */
    public static MeterCondition selfAbove(double fraction) {
        return ctx -> ctx.fraction() >= fraction;
    }

    // ---------------------------------------------------------------- internals

    private static boolean heldMatches(ItemStack stack, Set<Material> set) {
        return stack != null && set.contains(stack.getType());
    }

    private static boolean isFire(Block block) {
        Material type = block.getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.LAVA) {
            return true;
        }
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
            return false;
        }
        BlockData data = block.getBlockData();
        return !(data instanceof Lightable lightable) || lightable.isLit();
    }

    /**
     * Bounded disc scan around the player's feet. Radius is clamped and the vertical span is fixed at
     * {@link #PROP_SCAN_VERTICAL}, so the worst case is a known constant per player per pulse rather
     * than something a config typo can turn into tens of thousands of block reads. Returns on the
     * first hit, which in practice is immediate for the player standing at the prop — the expensive
     * full sweep only happens for players who are <em>not</em> being cured, which is the correct place
     * for the cost to land.
     */
    private static MeterCondition nearBlockMatching(double radius, java.util.function.Predicate<Block> match) {
        double clamped = Math.max(1.0, Math.min(MAX_SCAN_RADIUS, radius));
        int span = (int) Math.ceil(clamped);
        double radiusSq = clamped * clamped;
        return ctx -> {
            World world = ctx.world();
            if (world == null) {
                return false;
            }
            Location at = ctx.location();
            int baseX = at.getBlockX();
            int baseY = at.getBlockY();
            int baseZ = at.getBlockZ();
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    if (dx * dx + dz * dz > radiusSq) {
                        continue;
                    }
                    for (int dy = -PROP_SCAN_VERTICAL; dy <= PROP_SCAN_VERTICAL; dy++) {
                        // getBlockAt on an unloaded chunk would force-load it; every meter check happens
                        // inside an arena the fight is already keeping loaded, so this stays local.
                        if (match.test(world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        };
    }
}
