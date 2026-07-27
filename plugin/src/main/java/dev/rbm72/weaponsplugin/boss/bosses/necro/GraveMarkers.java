package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The graves he plants, and the reason killing his army is never the win condition.
 * <p>
 * A marker is a real thing standing on the arena floor: a soul-soil plot with a bone headstone raised
 * out of it, and a hittable prop in the middle of it. While it stands it feeds undead into the fight
 * forever, so a group that only fights the horde loses to arithmetic — the source has to be destroyed.
 * <p>
 * The hittable part is an {@link ArenaTotem}, which manages its own HP in Java and only accepts damage
 * traceable to a player, so his own AoEs and the sun cannot break a grave for the group. Note that a
 * totem wears its icon in the helmet slot and is therefore sun-proof by construction, which is exactly
 * right here: daylight is meant to melt his army, not quietly finish the group's objectives for them.
 * <p>
 * The plot's ground conversion is left behind when a marker dies — the arena should read as a graveyard
 * by the end of the fight — and only the raised headstone is cleared. All of it is in the arena ledger.
 */
final class GraveMarkers {

    private static final Material HEADSTONE = Material.BONE_BLOCK;
    private static final Material PLOT = Material.SOUL_SOIL;
    /** Radius, in blocks, of the disturbed-earth plot around a marker. */
    private static final int PLOT_RADIUS = 1;

    private final NecroFight fight;
    private final UndeadHorde horde;
    private final List<Marker> markers = new ArrayList<>();

    private int destroyed;
    private int plantWarningLeft;
    private Location pendingSpot;

    GraveMarkers(NecroFight fight, UndeadHorde horde) {
        this.fight = fight;
        this.horde = horde;
    }

    private static final class Marker {

        private final ArenaTotem totem;
        private final List<Block> raised;
        private int spawnCooldownLeft;

        private Marker(ArenaTotem totem, List<Block> raised, int spawnCooldownLeft) {
            this.totem = totem;
            this.raised = raised;
            this.spawnCooldownLeft = spawnCooldownLeft;
        }
    }

    /**
     * Keeps {@code wanted} markers standing, planting at most one at a time so each one gets its own
     * visible, audible arrival rather than three appearing at once.
     */
    void pulse(int intervalTicks, int wanted) {
        reapDestroyed();
        feedTheHorde(intervalTicks);
        if (plantWarningLeft > 0) {
            plantWarningLeft -= intervalTicks;
            markPlot();
            if (plantWarningLeft <= 0) {
                plant();
            }
            return;
        }
        if (markers.size() < wanted) {
            beginPlanting();
        }
    }

    /** Markers players have broken this fight — P1's exit condition. */
    int destroyedCount() {
        return destroyed;
    }

    int liveCount() {
        return markers.size();
    }

    /**
     * Retires every standing marker without counting it as destroyed. For the fight ending, not for a
     * real outcome — {@link ArenaTotem#discard} is what stops its prop entity and its lifetime timer.
     */
    void discardAll() {
        for (Marker marker : markers) {
            marker.totem.discard();
            clearHeadstone(marker);
        }
        markers.clear();
    }

    // ------------------------------------------------------------------ upkeep

    private void reapDestroyed() {
        for (Iterator<Marker> it = markers.iterator(); it.hasNext(); ) {
            Marker marker = it.next();
            if (marker.totem.isValid()) {
                continue;
            }
            // The totem's own onDestroyed already fired the count; whatever ended it, the headstone
            // should not be left hanging in the air with nothing holding it up.
            clearHeadstone(marker);
            it.remove();
        }
    }

    private void feedTheHorde(int intervalTicks) {
        int interval = Math.max(40, fight.config().num("grave-spawn-interval-ticks", 140));
        for (Marker marker : markers) {
            marker.spawnCooldownLeft -= intervalTicks;
            if (marker.spawnCooldownLeft > 0) {
                continue;
            }
            marker.spawnCooldownLeft = interval;
            Location at = marker.totem.location();
            Fx.burst(at.clone().add(0, 0.6, 0), Particle.SCULK_SOUL, 16, 0.5);
            Fx.sound(at, Sound.BLOCK_SOUL_SOIL_BREAK, 0.9f, 0.6f);
            horde.raiseOne(at);
        }
    }

    // ----------------------------------------------------------------- planting

    private void beginPlanting() {
        double spread = fight.config().dbl("grave-ring-radius-fraction", 0.62);
        pendingSpot = surfaceSpot(ThreadLocalRandom.current().nextDouble(Math.PI * 2), spread);
        plantWarningLeft = Math.max(20, fight.config().num("grave-plant-telegraph-ticks", 40));
        Fx.sound(pendingSpot, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.4f);
    }

    /** Pure telegraph — the ground he is about to open, marked long before anything comes out of it. */
    private void markPlot() {
        if (pendingSpot == null) {
            return;
        }
        Fx.ring(pendingSpot, Particle.SOUL, 1.6, 12);
        Fx.burst(pendingSpot.clone().add(0, 0.2, 0), Particle.SCULK_SOUL, 5, 0.5);
    }

    private void plant() {
        Location spot = pendingSpot;
        pendingSpot = null;
        if (spot == null) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }

        breakGround(world, spot);
        List<Block> raised = raiseHeadstone(world, spot);
        ArenaTotem totem = ArenaTotem.spawn(fight.plugin(), fight.instance(), spot,
                Material.WITHER_SKELETON_SKULL,
                Component.text("Grave Marker", NamedTextColor.DARK_GREEN),
                fight.config().dbl("grave-health", 60.0),
                // Long enough that it never expires on its own: a grave is destroyed by players or it
                // stands. Expiry is only a backstop, and it is treated as an ordinary removal.
                Math.max(1200, fight.config().num("grave-lifetime-ticks", 24000)),
                destroyedTotem -> onDestroyed(spot),
                expired -> {
                });
        markers.add(new Marker(totem, raised, fight.config().num("grave-spawn-interval-ticks", 140)));

        NecroFight.necroticFlourish(spot, Sound.ENTITY_WITHER_SPAWN, 0.5f);
        Fx.expandingRings(fight.plugin(), spot, Particle.SOUL, 3.0, 3, 3L);
    }

    /** Converts the plot to disturbed earth. Left behind when the marker dies — the arena keeps the scar. */
    private void breakGround(World world, Location spot) {
        for (int x = -PLOT_RADIUS; x <= PLOT_RADIUS; x++) {
            for (int z = -PLOT_RADIUS; z <= PLOT_RADIUS; z++) {
                Block ground = world.getBlockAt(spot.getBlockX() + x, spot.getBlockY() - 1, spot.getBlockZ() + z);
                if (!ground.getType().isAir()) {
                    Grief.setMechanicBlock(fight.griefContext(), ground, PLOT);
                }
            }
        }
    }

    /**
     * The headstone itself, raised one block clear of the marker so it never sits inside the prop's own
     * hitbox and block the group from swinging at it.
     */
    private List<Block> raiseHeadstone(World world, Location spot) {
        List<Block> raised = new ArrayList<>(2);
        int baseX = spot.getBlockX() + 1;
        int baseZ = spot.getBlockZ();
        for (int y = 0; y < 2; y++) {
            Block block = world.getBlockAt(baseX, spot.getBlockY() + y, baseZ);
            if (!block.getType().isAir() || !Grief.setMechanicBlock(fight.griefContext(), block, HEADSTONE)) {
                break;
            }
            raised.add(block);
        }
        return raised;
    }

    private void clearHeadstone(Marker marker) {
        for (Block block : marker.raised) {
            if (block.getType() == HEADSTONE) {
                Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
            }
        }
        marker.raised.clear();
    }

    private void onDestroyed(Location spot) {
        destroyed++;
        Fx.blockBurst(spot.clone().add(0, 1, 0), HEADSTONE, 30, 0.6);
        Fx.burst(spot.clone().add(0, 1, 0), Particle.SOUL_FIRE_FLAME, 24, 0.5);
        Fx.sound(spot, Sound.BLOCK_BONE_BLOCK_BREAK, 1.2f, 0.6f);
        Fx.sound(spot, Sound.ENTITY_WITHER_HURT, 1.0f, 0.7f);
    }

    /** Same fixed-centre anchoring as the wave lanes — see {@code UndeadHorde}'s note on why not the boss. */
    private Location surfaceSpot(double angleRadians, double fraction) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }
}
