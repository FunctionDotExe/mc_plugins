package dev.rbm72.weaponsplugin.boss.grief;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;
import java.util.Optional;

/**
 * Routes explosion damage into the owning fight's {@link ArenaLedger}.
 * <p>
 * Explosions are the one way a boss wrecks terrain without going through {@link Grief} — the server
 * computes the block list itself and destroys it before any of our code sees it. Real TNT makes that
 * worse rather than better: a boss that lights a stack of it is producing terrain damage with no boss
 * entity attached to the event at all.
 * <p>
 * Hooking the explosion events lets the ledger capture every affected block <em>while it still exists</em>,
 * and drop from the list anything it can't cover (a chest, or a fight that has spent its restore
 * budget) so that block simply survives the blast. Without this, one crater is enough to make an
 * otherwise-clean rollback permanently wrong, in a way nobody notices until the arena has been chewed
 * up over a dozen clears.
 */
public final class ExplosionLedgerListener implements Listener {

    private final WeaponsPlugin plugin;

    public ExplosionLedgerListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Creeper, TNT entity, end crystal, and every {@code world.createExplosion} a boss attack fires. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        capture(event.getLocation(), event.blockList());
    }

    /** A lit TNT <em>block</em> and bed/anchor blasts, which carry no entity to identify. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        capture(event.getBlock().getLocation(), event.blockList());
    }

    private void capture(Location at, List<Block> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        Optional<BossInstance> owner = plugin.bossManager().instanceCovering(at);
        if (owner.isEmpty()) {
            return;
        }
        ArenaLedger ledger = owner.get().ledger();
        if (!ledger.enabled()) {
            return;
        }
        ledger.recordExplosion(blocks);
    }
}
