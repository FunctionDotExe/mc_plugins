package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P1's single fuse and P3's stacked <b>TNT Clusters</b> — real TNT blocks with a real crawling
 * {@link FireTrail} fuse lit toward them, and three independent, physical ways to stop one (§1.4 TNT
 * Cluster row): douse the stack directly, wall it off with any placed block, or solve the fuse itself
 * (the {@link FireTrail}'s own douse/block rule).
 * <p>
 * Deliberately fight-scoped rather than rebuilt per phase, and deliberately <em>self-replenishing</em>:
 * a detonated cluster is replaced after a short delay rather than simply being gone, because P1 and P3
 * both gate their exit on a <em>count of successful neutralisations</em> (one, then three more) and a
 * group that fails its only chance at the objective would otherwise ride the 45-second floor-lock
 * timeout to a phase they never actually solved (Pitfall 3 in spirit: failing must cost something, not
 * end the puzzle). {@link #setTarget} lets each phase choose how many clusters live at once and reset
 * the count the phase itself is scoring, without tearing down the physical system in between.
 */
final class TntClusters implements Listener {

    private static final int FUSE_MAX_TILES = 16;

    private final InfernoFight fight;
    private final List<Cluster> clusters = new ArrayList<>();
    private boolean listening;

    private boolean armed;
    private int liveTarget = 1;
    private int neutralizedThisPhase;
    private int respawnCountdownTicks;

    private static final class Cluster {
        List<Block> blocks;
        Location core;
        FireTrail fuse;
        boolean resolved;
    }

    TntClusters(InfernoFight fight) {
        this.fight = fight;
    }

    /** Arms this phase's cluster pressure: how many stand live at once, and resets this phase's tally. */
    void setTarget(int liveCount) {
        armed = true;
        liveTarget = Math.max(1, liveCount);
        neutralizedThisPhase = 0;
        if (!listening) {
            listening = true;
            fight.plugin().getServer().getPluginManager().registerEvents(this, fight.plugin());
        }
    }

    /** Stops replacing detonated/neutralised clusters; whatever is standing keeps running to resolution. */
    void disarm() {
        armed = false;
    }

    int neutralizedThisPhase() {
        return neutralizedThisPhase;
    }

    void pulse(int intervalTicks) {
        for (Iterator<Cluster> it = clusters.iterator(); it.hasNext(); ) {
            Cluster cluster = it.next();
            if (cluster.resolved) {
                continue;
            }
            cluster.fuse.pulse(intervalTicks);
            if (cluster.fuse.extinguished() || cluster.fuse.blocked()) {
                neutralize(cluster, "the fuse never arrives");
                continue;
            }
            if (cluster.fuse.arrived()) {
                detonate(cluster);
                continue;
            }
            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                Fx.coloredBurst(cluster.core.clone().add(0.5, 1.0, 0.5), InfernoFight.EMBER, 0.9f, 3, 0.2);
            }
        }
        clusters.removeIf(c -> c.resolved);

        if (!armed) {
            return;
        }
        respawnCountdownTicks -= intervalTicks;
        if (clusters.size() < liveTarget && respawnCountdownTicks <= 0) {
            spawnOne();
            respawnCountdownTicks = fight.config().num("tnt-respawn-delay-ticks", 160);
        }
    }

    private void spawnOne() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location center = fight.instance().arena().center();
        double radius = fight.instance().arena().radius();
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double fraction = fight.config().dbl("tnt-cluster-fraction", 0.5);
        Location spot = center.clone().add(Math.cos(angle) * radius * fraction, 0, Math.sin(angle) * radius * fraction);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()));
        Block ground = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
        Block base = ground.getRelative(0, 1, 0);
        if (!base.getType().isAir()) {
            return;
        }

        int height = fight.config().num("tnt-cluster-height", 3);
        List<Block> blocks = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            Block block = base.getRelative(0, y, 0);
            if (Grief.setMechanicBlock(fight.griefContext(), block, Material.TNT)) {
                blocks.add(block);
            }
        }
        if (blocks.isEmpty()) {
            return;
        }

        Location rim = center.clone().add(Math.cos(angle + 0.6) * radius * 0.95, 0, Math.sin(angle + 0.6) * radius * 0.95);
        rim.setY(world.getHighestBlockYAt(rim.getBlockX(), rim.getBlockZ()));
        List<Block> fusePath = FireTrail.pathBetween(world, rim, base.getLocation(), FUSE_MAX_TILES);

        Cluster cluster = new Cluster();
        cluster.blocks = blocks;
        cluster.core = base.getLocation();
        cluster.fuse = new FireTrail(fight, fusePath,
                fight.config().num("tnt-fuse-step-ticks", 9),
                fight.config().dbl("tnt-fuse-damage", 2.0),
                fight.config().num("tnt-fuse-fire-ticks", 40),
                fight.config().dbl("tnt-fuse-burning-add", 8.0),
                () -> {
                    // Re-check on arrival, not just at spawn: a bucket poured on the stack after the fuse
                    // was already lit must still save it, which the fuse's own extinguish check cannot
                    // see because dousing the TNT itself never touches a fire block.
                });
        clusters.add(cluster);

        Fx.coloredBurst(base.getLocation().add(0.5, 1.2, 0.5), InfernoFight.DEEP_FIRE, 1.6f, 30, 0.5);
        Fx.sound(base.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.9f, 0.7f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("A FUSE IS LIT", NamedTextColor.RED),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void neutralize(Cluster cluster, String reason) {
        if (cluster.resolved) {
            return;
        }
        cluster.resolved = true;
        neutralizedThisPhase++;
        removeBlocks(cluster);
        fight.instance().recordProgress();
        Fx.coloredBurst(cluster.core.clone().add(0.5, 1.0, 0.5), InfernoFight.STEAM, 1.6f, 40, 0.6);
        Fx.sound(cluster.core, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.8f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("CLUSTER NEUTRALISED", NamedTextColor.GREEN),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void detonate(Cluster cluster) {
        if (cluster.resolved) {
            return;
        }
        cluster.resolved = true;
        Location at = cluster.core.clone().add(0.5, 1.0, 0.5);
        removeBlocks(cluster);
        Grief.explosion(fight.griefContext(), at, (float) fight.config().dbl("tnt-detonate-power", 2.6));
        double damage = fight.config().dbl("tnt-detonate-damage", 20.0);
        double radius = fight.config().dbl("tnt-detonate-radius", 5.0);
        for (Player player : Arena.combatants(at, radius)) {
            player.damage(damage, fight.instance().entity());
            fight.burning().add(player, fight.config().dbl("tnt-detonate-burning-add", 30.0));
        }
        // A detonated cluster undoes the ground it sat on — "a platform gone" per the design, expressed
        // here by handing that column back to the lava rather than leaving a scorched but standing tile.
        fight.lava().collapseColumn(cluster.core);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE CLUSTER BLOWS", NamedTextColor.DARK_RED),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void removeBlocks(Cluster cluster) {
        for (Block block : cluster.blocks) {
            if (block.getType() == Material.TNT) {
                Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
            }
        }
        cluster.fuse.expire();
    }

    /** Fight teardown: clear every live cluster and its fuse without playing the detonation/neutralise beat. */
    void discardAll() {
        for (Cluster cluster : clusters) {
            if (!cluster.resolved) {
                removeBlocks(cluster);
            }
        }
        clusters.clear();
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
    }

    // ---------------------------------------------------------------- douse / wall-off

    /** "Wall it off" — placing any block within reach of the stack neutralises it (§1.4 TNT Cluster row). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        double radius = fight.config().dbl("tnt-wall-off-radius", 2.4);
        Location at = event.getBlock().getLocation();
        for (Cluster cluster : clusters) {
            if (!cluster.resolved && cluster.core.distanceSquared(at) <= radius * radius) {
                neutralize(cluster, "walled off");
                return;
            }
        }
    }

    /** "Douse it" — a water bucket emptied near the stack, whether or not the fuse has even arrived yet. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        double radius = fight.config().dbl("tnt-douse-radius", 2.6);
        Location at = event.getBlock().getLocation();
        for (Cluster cluster : clusters) {
            if (!cluster.resolved && cluster.core.distanceSquared(at) <= radius * radius) {
                neutralize(cluster, "doused");
                return;
            }
        }
    }
}
