package dev.rbm72.weaponsplugin.stone;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.stone.stones.CliffwalkerStone;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives {@link CliffwalkerStone} every tick: needs frame-rate responsiveness (grabbing a wall the
 * instant the wielder reaches it, steering them along it) that the shared 0.5s stone-tick cadence
 * is too coarse for, so it runs on its own 1-tick timer instead of going through
 * {@link Stone#onEquipTick}.
 *
 * <p>The first version of this only zeroed fall speed near a wall, which read as "stuck", not
 * "running" — no lateral motion, and it wouldn't grab until you were already falling, so jumping
 * straight at a wall just bonked. This version finds the wall's surface tangent and continuously
 * drives the player along it (steered by look direction), with an upward "catch" kick on grab and
 * an outward kick on release, so it reads as an actual run rather than a stall.
 */
public final class StoneWallRunTask extends BukkitRunnable {

    private static final double WALL_CHECK_RANGE = 1.1;
    private static final double RUN_SPEED = 0.34;
    private static final double CATCH_KICK_UP = 0.2;
    private static final int CATCH_TICKS = 8;
    private static final double CLING_FALL_SPEED = -0.06;
    private static final double RELEASE_KICK_OUT = 0.35;
    private static final double RELEASE_KICK_UP = 0.15;
    private static final int FX_INTERVAL_TICKS = 3;

    private final StoneManager manager;
    private final Map<UUID, Integer> staminaTicks = new HashMap<>();
    private final Map<UUID, Integer> engagedTicks = new HashMap<>();
    private int tickCount = 0;

    public StoneWallRunTask(StoneManager manager) {
        this.manager = manager;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        tickCount++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!manager.hasEquipped(player, CliffwalkerStone.class)) {
                reset(uuid);
                continue;
            }
            if (player.isOnGround() || player.isFlying() || player.isSwimming()) {
                if (engagedTicks.containsKey(uuid)) {
                    engagedTicks.remove(uuid);
                }
                staminaTicks.put(uuid, CliffwalkerStone.MAX_STAMINA_TICKS);
                continue;
            }

            int stamina = staminaTicks.computeIfAbsent(uuid, k -> CliffwalkerStone.MAX_STAMINA_TICKS);
            if (stamina <= 0) {
                continue;
            }

            Vector tangent = wallTangent(player);
            if (tangent == null) {
                if (engagedTicks.remove(uuid) != null) {
                    kickOff(player);
                }
                continue;
            }

            int engaged = engagedTicks.merge(uuid, 1, Integer::sum);
            staminaTicks.put(uuid, stamina - 1);

            Vector velocity = tangent.clone().multiply(RUN_SPEED);
            velocity.setY(engaged <= CATCH_TICKS ? CATCH_KICK_UP * (1.0 - (double) engaged / CATCH_TICKS) : CLING_FALL_SPEED);
            player.setVelocity(velocity);

            if (tickCount % FX_INTERVAL_TICKS == 0) {
                Location at = player.getLocation();
                Fx.point(at, Particle.CRIT, 5);
                Fx.sound(player, Sound.BLOCK_SCAFFOLDING_STEP, 0.5f, 1.4f);
            }
        }
    }

    private void reset(UUID uuid) {
        staminaTicks.remove(uuid);
        engagedTicks.remove(uuid);
    }

    /** Launches the player off the wall once they run out of stamina or leave its surface. */
    private void kickOff(Player player) {
        Vector away = player.getVelocity().setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = player.getLocation().getDirection().setY(0);
        }
        if (away.lengthSquared() > 1.0E-4) {
            away = away.normalize().multiply(RELEASE_KICK_OUT);
        }
        away.setY(RELEASE_KICK_UP);
        player.setVelocity(away);
        Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.5f);
    }

    /**
     * If a wall sits close enough beside the player's facing to run along, returns the horizontal
     * unit vector that follows its surface in whichever direction the player is looking/moving —
     * letting them steer the run just by turning the camera. Null if there's no wall to grab.
     */
    private Vector wallTangent(Player player) {
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            return null;
        }
        look.normalize();

        RayTraceResult trace = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), look, WALL_CHECK_RANGE, FluidCollisionMode.NEVER, true);
        if (trace == null || trace.getHitBlock() == null) {
            return null;
        }
        BlockFace face = trace.getHitBlockFace();
        if (face == null || face.getModY() != 0) {
            return null;
        }

        Vector normal = new Vector(face.getModX(), 0, face.getModZ());
        Vector tangent = new Vector(-normal.getZ(), 0, normal.getX());
        return tangent.dot(look) < 0 ? tangent.multiply(-1) : tangent;
    }
}
