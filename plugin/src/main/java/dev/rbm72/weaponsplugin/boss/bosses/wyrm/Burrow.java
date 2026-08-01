package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * P2's underground travel: real tunnels, not a teleport-and-hope. While {@link #underground}, the boss
 * is invisible and physically relocated to a hidden column below the arena floor, and {@link
 * #tickTravel} both moves it toward its target and carves a sparse trail of real holes behind it — the
 * "tunnel mouths" that stay open for the rest of the fight (batch-4 §1.4). {@link #tremorAt} is the
 * telegraph: a rumble at the surface directly above wherever it currently is underground, a second or
 * two before it erupts, so its position is always knowable to a group that watches the ground.
 * <p>
 * {@link #forceSurface} is the fight-end/phase-teardown safety valve — a dive interrupted mid-travel
 * must never leave the boss permanently invisible and stuck under the map.
 */
final class Burrow {

    private final WyrmFight fight;

    private boolean underground;
    private Location diveOrigin;
    private int ticksSinceLastCarve;

    Burrow(WyrmFight fight) {
        this.fight = fight;
    }

    boolean isUnderground() {
        return underground;
    }

    /** Vanishes it from the surface and drops it below the floor at its current column. */
    void dive() {
        if (underground) {
            return;
        }
        LivingEntity boss = fight.instance().entity();
        World world = fight.world();
        if (boss == null || !boss.isValid() || world == null) {
            return;
        }
        diveOrigin = boss.getLocation();
        underground = true;
        // BossInstance lifts any boss that sits below the arena floor back onto solid ground — the
        // rescue that stops a ground boss getting stuck in its own crater. Being under the floor is the
        // entire point here, so it has to be told this one is deliberate.
        fight.instance().setFootingRescueSuspended(true);
        boss.setInvisible(true);
        // Same reasoning as EvasiveRig/AerialRig: BossInstance#tick unconditionally issues a moveTo
        // toward whatever it's targeting, which would otherwise fight the raw setVelocity calls in
        // #tickTravel every tick it isn't corrected. Restored the instant it erupts.
        boss.setAI(false);
        Location under = underColumn(diveOrigin);
        boss.teleport(under);
        Fx.coloredBurst(diveOrigin.clone().add(0, 0.3, 0), WyrmFight.VOID_PURPLE, 2.0f, 40, 0.6);
        Fx.burst(diveOrigin, Particle.CLOUD, 24, 0.5);
        Fx.sound(diveOrigin, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.6f);
    }

    /** One pulse of underground movement toward {@code targetColumn}'s x/z, carving as it goes. */
    void tickTravel(Location targetColumn, double speed, int intervalTicks) {
        LivingEntity boss = fight.instance().entity();
        if (!underground || boss == null || !boss.isValid()) {
            return;
        }
        Location at = boss.getLocation();
        Vector toward = new Vector(targetColumn.getX() - at.getX(), 0, targetColumn.getZ() - at.getZ());
        if (toward.lengthSquared() > 1.0E-4) {
            toward.normalize().multiply(speed);
            boss.setVelocity(toward);
        }
        tremorAt(at);

        ticksSinceLastCarve += intervalTicks;
        int carveEveryTicks = fight.config().num("burrow-carve-interval-ticks", 20);
        if (ticksSinceLastCarve >= carveEveryTicks) {
            ticksSinceLastCarve = 0;
            carvePit(at);
        }
    }

    boolean arrivedNear(Location targetColumn, double thresholdFlat) {
        LivingEntity boss = fight.instance().entity();
        if (boss == null || !boss.isValid()) {
            return false;
        }
        Location at = boss.getLocation();
        double dx = at.getX() - targetColumn.getX();
        double dz = at.getZ() - targetColumn.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= thresholdFlat;
    }

    /** The tell: ground disturbance directly above wherever it is right now, underground. */
    private void tremorAt(Location undergroundAt) {
        World world = undergroundAt.getWorld();
        if (world == null) {
            return;
        }
        Location surface = surfaceAbove(undergroundAt);
        Fx.burst(surface, Particle.CRIT, 10, 0.6);
        Fx.sound(surface, Sound.BLOCK_GRAVEL_STEP, 1.0f, 0.6f);
    }

    private void carvePit(Location undergroundAt) {
        World world = undergroundAt.getWorld();
        if (world == null) {
            return;
        }
        Block core = world.getBlockAt(undergroundAt);
        Grief.setMechanicBlock(fight.griefContext(), core, Material.AIR);
        Grief.setMechanicBlock(fight.griefContext(), core.getRelative(0, 1, 0), Material.AIR);
    }

    /**
     * Surfaces at {@code targetColumn}: a real crater blows the tunnel mouth open, everyone caught in
     * the burst is launched and hurt, and it becomes visible and vulnerable again.
     */
    void erupt(Location targetColumn, double damage, double radius, float launchPower) {
        LivingEntity boss = fight.instance().entity();
        World world = fight.world();
        if (boss == null || world == null) {
            return;
        }
        Location surface = surfaceAbove(targetColumn);
        boss.teleport(surface.clone().add(0, 0.2, 0));
        boss.setInvisible(false);
        boss.setAI(true);
        underground = false;
        fight.instance().setFootingRescueSuspended(false);

        Grief.breakCrater(fight.griefContext(), surface, fight.config().dbl("burrow-crater-radius", 2.5));
        Fx.coloredBurst(surface.clone().add(0, 1, 0), WyrmFight.VOID_PURPLE, 2.6f, 70, 1.0);
        Fx.sound(surface, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.7f);

        for (Player player : Arena.combatants(surface, radius)) {
            player.damage(damage, boss);
            Vector up = player.getVelocity();
            up.setY(Math.max(up.getY(), launchPower));
            player.setVelocity(up);
        }
    }

    /** Safety valve: never leave it invisible and stranded below the map. */
    void forceSurface() {
        if (!underground) {
            return;
        }
        LivingEntity boss = fight.instance().entity();
        underground = false;
        fight.instance().setFootingRescueSuspended(false);
        if (boss == null || !boss.isValid()) {
            return;
        }
        boss.setInvisible(false);
        boss.setAI(true);
        Location back = diveOrigin != null ? diveOrigin : surfaceAbove(boss.getLocation());
        boss.teleport(back);
    }

    private Location underColumn(Location surfaceLoc) {
        World world = surfaceLoc.getWorld();
        int depth = fight.config().num("burrow-depth", 6);
        int groundY = world.getHighestBlockYAt(surfaceLoc.getBlockX(), surfaceLoc.getBlockZ());
        return new Location(world, surfaceLoc.getX(), groundY - depth, surfaceLoc.getZ());
    }

    private Location surfaceAbove(Location undergroundLoc) {
        World world = undergroundLoc.getWorld();
        int groundY = world.getHighestBlockYAt(undergroundLoc.getBlockX(), undergroundLoc.getBlockZ());
        return new Location(world, undergroundLoc.getBlockX() + 0.5, groundY + 1, undergroundLoc.getBlockZ() + 0.5);
    }

    void discardAll() {
        forceSurface();
    }
}
