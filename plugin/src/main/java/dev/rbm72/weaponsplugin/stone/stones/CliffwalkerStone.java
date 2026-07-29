package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Catches a wall mid-air and runs along its surface, steered by the camera.
 * <p>
 * <b>Two bugs paid for here.</b> The original detector ray-traced along the player's <em>look</em>
 * direction only, which made the ability self-cancelling: grabbing a wall points you along it, and a
 * look-ray parallel to a wall hits nothing, so the run released on the same tick it started and read as a
 * bonk. Detection is now a sweep of the four horizontal faces around the player, so the wall stays found
 * for as long as you are actually beside it and the camera is free to aim where you are going. The second
 * was structural: the physics lived in a 1-tick task built for this one stone, so the tunables were
 * {@code static final} constants in a file the stone could not see. Everything is config-backed now and
 * the stone owns its own per-tick behaviour via {@link #onFastTick}.
 * <p>
 * Stamina is per airtime and only refills on the ground, which is what stops a wall becoming a ladder: each
 * grab gets an upward catch kick, so unlimited re-grabs would be unlimited climbing.
 */
public final class CliffwalkerStone extends Stone {

    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    /** Below this the look direction is too close to perpendicular to say which way along the wall to go. */
    private static final double TANGENT_AMBIGUITY = 0.15;
    private static final int FX_INTERVAL_TICKS = 3;

    private final Map<UUID, Integer> staminaTicks = new HashMap<>();
    private final Map<UUID, Integer> engagedTicks = new HashMap<>();
    private final Map<UUID, Integer> fxTicks = new HashMap<>();

    public CliffwalkerStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "cliffwalker_stone";
    }

    @Override
    public Material material() {
        return Material.SPIDER_EYE;
    }

    @Override
    public String displayNameText() {
        return "Cliffwalker Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Jump at a wall to catch it and", NamedTextColor.GRAY),
                Component.text("run along its surface — steer with", NamedTextColor.GRAY),
                Component.text("your camera, for up to", NamedTextColor.GRAY),
                Component.text(String.format(Locale.ROOT, "%.1fs per airtime.", maxStaminaTicks() / 20.0),
                        NamedTextColor.GRAY),
                Component.text("Refills the moment you land.", NamedTextColor.DARK_GRAY));
    }

    /** How long a continuous wall-run can be sustained before stamina runs out, in ticks. */
    public int maxStaminaTicks() {
        return configInt("max-stamina-ticks", 40);
    }

    private double wallCheckRange() {
        return configDouble("wall-check-range", 1.1);
    }

    private double runSpeed() {
        return configDouble("run-speed", 0.34);
    }

    private double catchKickUp() {
        return configDouble("catch-kick-up", 0.2);
    }

    private int catchTicks() {
        return configInt("catch-ticks", 8);
    }

    private double clingFallSpeed() {
        return configDouble("cling-fall-speed", -0.06);
    }

    private double releaseKickOut() {
        return configDouble("release-kick-out", 0.35);
    }

    private double releaseKickUp() {
        return configDouble("release-kick-up", 0.15);
    }

    @Override
    public void onFastTick(Player player) {
        UUID uuid = player.getUniqueId();

        if (player.isOnGround() || player.isFlying() || player.isSwimming() || player.isInWater()) {
            engagedTicks.remove(uuid);
            staminaTicks.put(uuid, maxStaminaTicks());
            return;
        }

        int stamina = staminaTicks.computeIfAbsent(uuid, k -> maxStaminaTicks());
        if (stamina <= 0) {
            // Out of stamina: release once, then leave the player to fall. Re-arming needs a landing.
            if (engagedTicks.remove(uuid) != null) {
                kickOff(player);
            }
            return;
        }

        Vector tangent = wallTangent(player);
        if (tangent == null) {
            if (engagedTicks.remove(uuid) != null) {
                kickOff(player);
            }
            return;
        }

        int engaged = engagedTicks.merge(uuid, 1, Integer::sum);
        staminaTicks.put(uuid, stamina - 1);

        int catchWindow = Math.max(1, catchTicks());
        Vector velocity = tangent.clone().multiply(runSpeed());
        velocity.setY(engaged <= catchWindow
                ? catchKickUp() * (1.0 - (double) engaged / catchWindow)
                : clingFallSpeed());
        player.setVelocity(velocity);

        int fx = fxTicks.merge(uuid, 1, Integer::sum);
        if (fx % FX_INTERVAL_TICKS == 0) {
            Location at = player.getLocation();
            Fx.point(at, Particle.CRIT, 5);
            Fx.sound(player, Sound.BLOCK_SCAFFOLDING_STEP, 0.5f, 1.4f);
        }
    }

    @Override
    public void onIdleTick(Player player) {
        UUID uuid = player.getUniqueId();
        if (staminaTicks.isEmpty() && engagedTicks.isEmpty()) {
            return;
        }
        staminaTicks.remove(uuid);
        engagedTicks.remove(uuid);
        fxTicks.remove(uuid);
    }

    /** Launches the player off the wall once they run out of stamina or leave its surface. */
    private void kickOff(Player player) {
        Vector away = player.getVelocity().setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = player.getLocation().getDirection().setY(0);
        }
        if (away.lengthSquared() > 1.0E-4) {
            away = away.normalize().multiply(releaseKickOut());
        }
        away.setY(releaseKickUp());
        player.setVelocity(away);
        Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.5f);
    }

    /**
     * The horizontal unit vector that follows the nearest adjacent wall's surface, pointed whichever way the
     * player is looking or already moving — or null if there is no wall beside them to run along.
     * <p>
     * All four horizontal directions are probed and the closest hit wins, rather than only probing where the
     * camera points: while running along a wall the camera is roughly parallel to it, which is precisely the
     * angle at which a look-ray misses.
     */
    private Vector wallTangent(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Location chest = eye.clone().subtract(0, 0.6, 0);
        double range = wallCheckRange();

        Vector normal = null;
        double closest = Double.MAX_VALUE;
        for (BlockFace face : HORIZONTAL) {
            Vector probe = new Vector(face.getModX(), 0, face.getModZ());
            double distance = Math.min(hitDistance(world, eye, probe, range), hitDistance(world, chest, probe, range));
            if (distance < closest) {
                closest = distance;
                // The surface normal points back out of the wall, i.e. opposite the probe that found it.
                normal = probe.clone().multiply(-1);
            }
        }
        if (normal == null) {
            return null;
        }

        Vector tangent = new Vector(-normal.getZ(), 0, normal.getX());
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() > 1.0E-4) {
            look.normalize();
            double along = tangent.dot(look);
            if (Math.abs(along) >= TANGENT_AMBIGUITY) {
                return along < 0 ? tangent.multiply(-1) : tangent;
            }
        }
        // Staring straight into the wall says nothing about which way to run, so carry on whichever way the
        // player was already travelling — the alternative is an arbitrary sideways yank on every grab.
        Vector momentum = player.getVelocity().setY(0);
        if (momentum.lengthSquared() > 1.0E-4 && tangent.dot(momentum) < 0) {
            return tangent.multiply(-1);
        }
        return tangent;
    }

    /** Distance to the first block in {@code direction}, or {@link Double#MAX_VALUE} if nothing is there. */
    private double hitDistance(World world, Location from, Vector direction, double range) {
        RayTraceResult trace = world.rayTraceBlocks(from, direction, range, FluidCollisionMode.NEVER, true);
        if (trace == null || trace.getHitBlock() == null) {
            return Double.MAX_VALUE;
        }
        BlockFace face = trace.getHitBlockFace();
        // A floor or ceiling is not a wall: running along one would just be flying.
        if (face != null && face.getModY() != 0) {
            return Double.MAX_VALUE;
        }
        return trace.getHitPosition().distance(from.toVector());
    }
}
