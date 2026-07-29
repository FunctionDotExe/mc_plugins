package dev.rbm72.weaponsplugin.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

/**
 * The one answer to "may this plugin overwrite that block, given it has to be able to put it back?"
 * <p>
 * Two independent undo logs now exist — {@link dev.rbm72.weaponsplugin.boss.grief.ArenaLedger} for a
 * fight's terrain damage, and {@link dev.rbm72.weaponsplugin.items.kit.TempTerrain} for the short-lived
 * terrain a player's weapon lays down — and they must agree exactly on what is off-limits. When the
 * rule lived only inside the arena ledger, every new writer either re-derived it (and drifted) or
 * skipped it (and corrupted a world). Both of those are silent failures discovered days later, so the
 * rule lives here and neither ledger owns a private copy.
 */
public final class BlockGuard {

    private BlockGuard() {
    }

    /**
     * True for a block type nothing in this plugin may modify, ledger or no ledger. Bedrock and
     * barriers are world structure rather than terrain; the portal/command/structure blocks would let
     * an errant write break something a restore cannot meaningfully repair.
     */
    public static boolean isProtected(Material type) {
        return type == Material.BEDROCK || type == Material.BARRIER
                || type == Material.END_PORTAL_FRAME || type == Material.COMMAND_BLOCK
                || type == Material.STRUCTURE_BLOCK || type == Material.JIGSAW;
    }

    /**
     * True when {@code block} can be changed and later restored faithfully from its
     * {@link org.bukkit.block.data.BlockData} alone.
     * <p>
     * Tile entities fail this on purpose: block data carries no inventory, no sign text and no spawner
     * config, so "restoring" a chest hands the player back an empty husk. Refusing the write outright
     * makes player storage untouchable by anything here, which is worth far more than the handful of
     * blocks it costs an attack.
     */
    public static boolean isRestorable(Block block) {
        return block != null
                && !isProtected(block.getType())
                && !(block.getState() instanceof TileState);
    }
}
