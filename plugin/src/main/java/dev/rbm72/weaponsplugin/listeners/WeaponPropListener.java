package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.kit.Props;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * The single guarantee that a weapon's real objects stay real without letting weapons grief the world.
 * <p>
 * Replaces the old {@code TntWeaponListener}, which did this for exactly one entity type belonging to
 * exactly one weapon (Blastcaller's TNT). That was fine while TNT was the only real explosive in the
 * roster and actively unsafe the moment the §0.1 doctrine pass started adding more: every new wind
 * charge, end crystal or primed charge would have cratered the map until someone remembered to extend a
 * listener nothing pointed at. This one matches on the {@link Props} marker instead of on a type, so an
 * ability is safe because it used the shared spawner, not because its author read this file.
 * <p>
 * What it does <em>not</em> touch is the part that makes the object worth using. Entity damage and
 * knockback are computed by vanilla before {@link EntityExplodeEvent} fires, so emptying the block list
 * leaves a genuinely real explosion — real damage falloff, real velocity, real sound — that simply does
 * not eat terrain. That is the §0.1 trade: the weapon gets the physics, the world keeps its blocks.
 */
public final class WeaponPropListener implements Listener {

    private final WeaponsPlugin plugin;

    public WeaponPropListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Strips terrain damage from any weapon prop that explodes — TNT, wind charge, end crystal, or
     * whatever the next batch adds.
     * <p>
     * MONITOR-priority-safe by design: it mutates only the block list, never the cancelled state, so it
     * composes with the arena ledger's own explosion hook rather than racing it.
     */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (Props.isProp(plugin, event.getEntity())) {
            event.blockList().clear();
        }
    }

    /**
     * Stops a weapon's props from editing blocks outside an explosion: a tagged falling block trying to
     * settle into the floor, and a wind charge toggling the doors, buttons and candles it drifts past.
     * <p>
     * Falling blocks are already spawned with {@code cancelDrop} and {@code dropItem(false)}, and this is
     * the third belt on the same trousers on purpose — the failure mode is a permanent block appearing in
     * someone's build, which is the one outcome that costs trust rather than balance.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPropChangesBlock(EntityChangeBlockEvent event) {
        if (!Props.isProp(plugin, event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (event.getEntity() instanceof FallingBlock falling) {
            falling.remove();
        }
    }

    /**
     * Refuses to let a loaned item be placed as a block.
     * <p>
     * An offering is a real item so it can be <em>used</em> — eaten, thrown, drunk. Placing it converts
     * something on a timer into permanent world state that the reclaim sweep will never find, which is
     * the loan turning back into the resource generator {@link Props#offering} exists to avoid.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlaceLoanedItem(BlockPlaceEvent event) {
        if (Props.isLoaned(plugin, event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * Refuses the fire a weapon's real lightning bolt would start where it lands.
     * <p>
     * {@link org.bukkit.entity.LightningStrike} has no "causes fire" switch in the Bukkit interface, so this is
     * the only place the decision can be made — and it has to be made, because a called-down bolt that ignites
     * whatever it was aimed at is a weapon that burns down the build it was fired next to. Everything else the
     * bolt does is left intact: the damage, the mob conversions, the creeper charge, the lightning-rod
     * interaction. Only the ignition is dropped, which is the same trade {@link #onExplode} makes for TNT.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPropIgnitesBlock(org.bukkit.event.block.BlockIgniteEvent event) {
        if (Props.isProp(plugin, event.getIgnitingEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Keeps a weapon's fire inside the ledger that is holding an undo for it.
     * <p>
     * {@link dev.rbm72.weaponsplugin.items.kit.TempTerrain} guarantees it can put back every block it wrote —
     * and only the blocks it wrote. Real fire breaks that guarantee on its own: a fire block ticks, and vanilla
     * spreads it into any flammable neighbour, so a five-block trail through a spruce forest becomes a hundred
     * burning blocks the ledger has never heard of and will never restore. That is permanent grief arriving a
     * minute after the ability that caused it, which is exactly the failure the §0.1 pass must not ship.
     * <p>
     * So spread out of a tracked block is refused, and so is the burn that consumes what it spread to. The fire
     * on the trail still burns entities standing in it, still lights them, still has to be walked around — it
     * simply cannot leave the footprint the ledger wrote. Untracked fire (a player's flint and steel, a boss's
     * own ledgered fire) is not this listener's business and is left alone.
     */
    @EventHandler(ignoreCancelled = true)
    public void onTrackedFireSpread(org.bukkit.event.block.BlockSpreadEvent event) {
        if (plugin.tempTerrain() != null && plugin.tempTerrain().tracks(event.getSource())) {
            event.setCancelled(true);
        }
    }

    /** The other half of {@link #onTrackedFireSpread}: the flammable block being eaten by ledgered fire. */
    @EventHandler(ignoreCancelled = true)
    public void onTrackedFireBurn(org.bukkit.event.block.BlockBurnEvent event) {
        if (plugin.tempTerrain() != null && plugin.tempTerrain().tracks(event.getIgnitingBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Drops of a loaned item vanish on the spot rather than lying on the ground.
     * <p>
     * Without this, "throw it out of your inventory before the reclaim fires" is a two-second exploit that
     * keeps the item permanently — the sweep only looks in inventories.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDropLoanedItem(PlayerDropItemEvent event) {
        if (Props.isLoaned(plugin, event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }
}
