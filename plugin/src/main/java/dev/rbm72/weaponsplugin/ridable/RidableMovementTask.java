package dev.rbm72.weaponsplugin.ridable;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Optional;

/**
 * Drives custom mount steering every tick, since arbitrary tamed mobs (Ravager, Ender Dragon,
 * Wither, Spider, Phantom) have no vanilla "ridden creature" controller once their AI is off.
 * Deliberately teleport-based rather than {@code setVelocity}-based: a NoAI mob's own
 * {@code travel()} recomputes/damps velocity from its (nonexistent) AI move-input every tick, so
 * pushing it with velocity alone gets fought and mostly cancelled out. Since raw teleporting has no
 * collision of its own, every candidate move is checked against solid blocks first (so mounts can't
 * clip through walls), and ground mounts additionally ray-trace downward each tick to hug terrain —
 * "flying" a ground mob or free-falling through the floor were both symptoms of skipping that.
 * Reads each rider's per-tick {@link Input} snapshot for WASD/jump/sneak. Excludes any
 * {@link Ridable} that reports {@link Ridable#customSteering()} false (the Strider — vanilla
 * already drives {@code Steerable} entities server-side once mounted).
 */
public final class RidableMovementTask extends BukkitRunnable {

    private static final double GROUND_SPEED = 0.35;
    private static final double FLIGHT_SPEED = 0.5;
    private static final double FLIGHT_VERTICAL_STEP = 0.4;
    private static final double GROUND_FALL_STEP = 0.5;
    private static final double GROUND_RAY_DISTANCE = 6.0;
    /** A found surface at or above this relative height is a walkable step, not a fall. */
    private static final double STEP_TOLERANCE = 0.6;

    private final RidableManager manager;

    public RidableMovementTask(RidableManager manager) {
        this.manager = manager;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<LivingEntity> mountOpt = manager.mountedEntity(player);
            if (mountOpt.isEmpty()) {
                continue;
            }
            LivingEntity mount = mountOpt.get();
            // Belt-and-suspenders: even with AI off, a hurt-response can slap a target back on.
            if (mount instanceof Mob mob) {
                mob.setTarget(null);
            }
            Optional<Ridable> ridableOpt = manager.mountedRidable(player);
            if (ridableOpt.isEmpty() || !ridableOpt.get().customSteering()) {
                continue;
            }
            steer(player, mount, ridableOpt.get());
        }
    }

    private void steer(Player player, LivingEntity mount, Ridable ridable) {
        Input input = player.getCurrentInput();
        float yaw = player.getLocation().getYaw();

        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = forward.clone().rotateAroundY(-Math.PI / 2);

        double forwardAmount = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        double strafeAmount = (input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0);

        Vector move = forward.multiply(forwardAmount).add(right.multiply(strafeAmount));
        if (move.lengthSquared() > 1.0) {
            move.normalize();
        }
        move.multiply(ridable.flies() ? FLIGHT_SPEED : GROUND_SPEED);

        Location current = mount.getLocation();
        double dx = move.getX();
        double dz = move.getZ();

        // Wall collision: if the diagonal move is blocked, try each axis alone so it can slide.
        // Ground mounts ignore a solid block exactly at foot height when deciding "blocked" — that's
        // a 1-block step, not a wall, and groundFollowY below climbs onto it automatically.
        boolean allowStepUp = !ridable.flies();
        if (blocked(current.clone().add(dx, 0, dz), mount, allowStepUp)) {
            boolean xBlocked = blocked(current.clone().add(dx, 0, 0), mount, allowStepUp);
            boolean zBlocked = blocked(current.clone().add(0, 0, dz), mount, allowStepUp);
            dx = xBlocked ? 0 : dx;
            dz = zBlocked ? 0 : dz;
        }

        double newX = current.getX() + dx;
        double newZ = current.getZ() + dz;
        double newY;

        if (ridable.flies()) {
            // Sneak can't drive "descend": the vanilla client dismounts on shift the instant it's
            // pressed while riding anything, so isSneak() never reads true here. Pitch does the job
            // instead — look down to dive, look up to climb — with jump as an extra boost of lift.
            double pitchLift = -(player.getEyeLocation().getPitch() / 90.0) * FLIGHT_VERTICAL_STEP;
            double dy = pitchLift + (input.isJump() ? FLIGHT_VERTICAL_STEP : 0);
            if (dy != 0 && blocked(current.clone().add(dx, dy, dz), mount, false)) {
                dy = 0;
            }
            newY = current.getY() + dy;
        } else {
            newY = groundFollowY(new Location(current.getWorld(), newX, current.getY(), newZ), current.getY());
        }

        Location next = new Location(current.getWorld(), newX, newY, newZ,
                yaw + (float) ridable.yawOffsetDegrees(), 0);
        mount.teleport(next);

        // Vanilla ambient roar/flap (e.g. Ender Dragon) fires from the mob's own AI regardless of
        // rider control; mute it while grounded so a mount parked on the ground stays quiet, and
        // only let it sound off while genuinely airborne. Ability sounds use Fx.sound (a world-level
        // playSound), which isn't tied to the entity's silent flag, so this doesn't affect those.
        if (ridable.flies()) {
            mount.setSilent(mount.isOnGround());
        }
    }

    /** Ray-traces straight down from just above the candidate spot to hug terrain instead of flying/clipping. */
    private double groundFollowY(Location candidateXZ, double currentY) {
        World world = candidateXZ.getWorld();
        if (world == null) {
            return currentY;
        }
        Location rayStart = candidateXZ.clone();
        rayStart.setY(currentY + 2);
        RayTraceResult hit = world.rayTraceBlocks(rayStart, new Vector(0, -1, 0), GROUND_RAY_DISTANCE,
                FluidCollisionMode.NEVER, true);
        if (hit == null) {
            return currentY - GROUND_FALL_STEP;
        }
        double groundY = hit.getHitPosition().getY();
        if (groundY >= currentY - STEP_TOLERANCE) {
            return groundY;
        }
        return Math.max(groundY, currentY - GROUND_FALL_STEP);
    }

    /**
     * True if any block the mount's body would occupy at this location is solid. When
     * {@code allowStepUp} is set, the cell exactly at foot height is skipped — a single solid block
     * there is a step to climb, not a wall to bounce off.
     */
    private boolean blocked(Location candidate, LivingEntity mount, boolean allowStepUp) {
        World world = candidate.getWorld();
        if (world == null) {
            return false;
        }
        int heightCells = (int) Math.ceil(Math.max(1.0, mount.getBoundingBox().getHeight())) + 1;
        int startCell = allowStepUp ? 1 : 0;
        for (int i = startCell; i < heightCells; i++) {
            if (!candidate.clone().add(0, i, 0).getBlock().isPassable()) {
                return true;
            }
        }
        return false;
    }
}
