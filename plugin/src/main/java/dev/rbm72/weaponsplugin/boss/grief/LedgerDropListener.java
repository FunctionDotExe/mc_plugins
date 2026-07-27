package dev.rbm72.weaponsplugin.boss.grief;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;

import java.util.Optional;

/**
 * Stops a fight's own block work from being farmed.
 * <p>
 * Several bosses ask players to <b>mine</b> something — the Necro Overlord's corpse piles and grave
 * markers most of all, where a pickaxe is the counterplay to his whole third phase. Those blocks are
 * fabricated by the boss and then unfabricated by {@link ArenaLedger}, which restores the original floor
 * when the fight ends. Without this listener the group keeps the drops from every one of them: a bone
 * block per pile per kill, with the arena handed back pristine afterwards. On a live server that is a
 * bone farm with a boss bar attached, and it scales with exactly the behaviour the fight is trying to
 * encourage.
 * <p>
 * So: if the fight recorded the block, breaking it yields nothing. The ledger holds what was really
 * there, and that is what the player gets back. Blocks the fight never touched — the arena's own stone,
 * anything a player brought and placed themselves — are not in the ledger and drop normally.
 * <p>
 * Only applies while restore is on. With restore disabled the fight's blocks are permanent, nothing is
 * handed back at the end, and mining them out is honest work rather than duplication.
 */
public final class LedgerDropListener implements Listener {

    private final WeaponsPlugin plugin;

    public LedgerDropListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Fires after the block is already gone but before its items exist in the world, which is the only
     * hook that can suppress drops without also suppressing the break — the players must still be able
     * to clear the pile, they just do not get to keep it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (event.getItems().isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        Optional<BossInstance> owner = plugin.bossManager().instanceCovering(block.getLocation());
        if (owner.isEmpty()) {
            return;
        }
        if (!owner.get().ledger().tracks(block)) {
            return;
        }
        // Clearing the list rather than cancelling the event: cancelling would also cancel the break.
        event.getItems().clear();
    }
}
