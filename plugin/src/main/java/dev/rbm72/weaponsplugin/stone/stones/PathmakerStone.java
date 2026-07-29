package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import dev.rbm72.weaponsplugin.util.Grounded;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Catches a step into open air with a real block, so a run off a ledge becomes a bridge instead of a fall.
 * <p>
 * The tempting version of this stone is a potion effect or a velocity nudge that keeps you up. This one
 * places {@link Material#POLISHED_DIORITE}: a block that is really there, that other players can stand on,
 * that a boss's terrain can be walled off with, that supports a fight happening on top of it — and that
 * {@link dev.rbm72.weaponsplugin.items.kit.TempTerrain} takes back a few seconds later, which is what keeps
 * a movement stone from being a free building material.
 * <p>
 * Two limits do the balancing, and they matter more than the block count. It only fires over a genuine drop
 * ({@link #catchDepth} blocks of nothing below), so it can't be used to pave over ordinary ground; and it is
 * rate-limited per second, so the bridge advances at a walking pace rather than instantly spanning a
 * canyon. Sneaking suppresses it outright — the deliberate step off a ledge is how you tell the stone you
 * meant to drop.
 */
public final class PathmakerStone extends Stone {

    /** Bright and obviously artificial: a bridge the wielder can see the edge of at a glance. */
    private static final Material BRIDGE_MATERIAL = Material.POLISHED_DIORITE;

    /** Wall-clock times each player's recent bridge blocks were laid, for the per-second cap. */
    private final Map<UUID, long[]> recentPlacements = new HashMap<>();

    public PathmakerStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "pathmaker_stone";
    }

    @Override
    public Material material() {
        return Material.SCAFFOLDING;
    }

    @Override
    public String displayNameText() {
        return "Pathmaker Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Run off a ledge and a real block", NamedTextColor.GRAY),
                Component.text("appears under your next step —", NamedTextColor.GRAY),
                Component.text("a bridge anyone can stand on,", NamedTextColor.GRAY),
                Component.text("gone a few seconds later.", NamedTextColor.DARK_GRAY),
                Component.text("Sneak to step off on purpose.", NamedTextColor.DARK_GRAY));
    }

    /** How much empty space has to be below the player before this counts as a drop worth bridging. */
    private int catchDepth() {
        return Math.max(2, configInt("catch-depth", 4));
    }

    private int lifetimeTicks() {
        return Math.max(20, configInt("lifetime-ticks", 60));
    }

    /** Bridge blocks per second. The pace of the bridge, and the whole balance lever. */
    private int maxBlocksPerSecond() {
        return Math.max(1, configInt("max-blocks-per-second", 6));
    }

    @Override
    public void onFastTick(Player player) {
        if (player.isSneaking() || player.isFlying() || player.isInsideVehicle() || player.isGliding()) {
            return;
        }
        // Only on the way down, and only once the player has actually left the ground: bridging upward mid-jump
        // would place a block into the arc they are still travelling through.
        if (Grounded.onGround(player) || player.getVelocity().getY() > 0) {
            return;
        }

        Block support = player.getLocation().getBlock().getRelative(0, -1, 0);
        if (!support.getType().isAir() && !support.isLiquid()) {
            return;
        }
        if (!isOverDrop(support)) {
            return;
        }
        if (!allowPlacement(player)) {
            return;
        }

        if (plugin.tempTerrain().place(player, support, BRIDGE_MATERIAL, lifetimeTicks())) {
            Fx.sound(player, Sound.BLOCK_STONE_PLACE, 0.5f, 1.5f);
            Fx.blockBurst(support.getLocation().add(0.5, 1.0, 0.5), BRIDGE_MATERIAL, 6, 0.2);
        }
    }

    @Override
    public void onIdleTick(Player player) {
        if (!recentPlacements.isEmpty()) {
            recentPlacements.remove(player.getUniqueId());
        }
    }

    /** True if there is nothing to land on within {@link #catchDepth} blocks below {@code support}. */
    private boolean isOverDrop(Block support) {
        for (int dy = 1; dy <= catchDepth(); dy++) {
            Block below = support.getRelative(0, -dy, 0);
            if (!below.getType().isAir() && !below.isLiquid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The per-second budget, as a ring buffer of placement timestamps.
     * <p>
     * A plain "one block every N ticks" gate would let a player bridge indefinitely at that exact rate; a
     * window means a burst of blocks crossing a gap costs the next second of bridging, so a long span is
     * paced rather than free.
     */
    private boolean allowPlacement(Player player) {
        int budget = maxBlocksPerSecond();
        long[] window = recentPlacements.computeIfAbsent(player.getUniqueId(), k -> new long[budget]);
        if (window.length != budget) {
            // Config changed under a live player; a fresh window is the honest answer.
            window = new long[budget];
            recentPlacements.put(player.getUniqueId(), window);
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < window.length; i++) {
            if (now - window[i] >= 1000L) {
                window[i] = now;
                return true;
            }
        }
        return false;
    }
}
