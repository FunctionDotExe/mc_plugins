package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P2's floor: real, shallow water poured across several sections rather than a single lake, so the
 * "safe floor" becomes a shape the group has to read instead of a distance to keep from the boss
 * (batch-1 §3.3 — "the safe floor is now defined by water, not by distance").
 * <p>
 * {@link #strike} is what makes it dangerous: a bolt landing anywhere in a connected water body damages
 * everyone standing in that same body, found by a bounded flood-fill rather than a fixed radius — a
 * long puddle conducts along its whole length, which is the point (a real conductivity rule, not a
 * blast radius wearing a water texture).
 */
final class Flooding {

    /** Hard ceiling on one flood-fill's traversal, so an unusually large connected pool can't go quadratic. */
    private static final int MAX_TRAVERSAL = 500;

    private final StormFight fight;
    private final List<Block> flooded = new ArrayList<>();

    Flooding(StormFight fight) {
        this.fight = fight;
    }

    /** Pours several shallow pools across the floor. Called once, from P2's {@code onArm}. */
    void floodSections() {
        World world = fight.world();
        if (world == null || !flooded.isEmpty()) {
            return;
        }
        int sections = Math.max(1, fight.config().num("flood-sections", 3));
        double radius = fight.config().dbl("flood-section-radius", 5.0);
        double fraction = fight.config().dbl("flood-section-fraction", 0.45);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < sections; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / sections;
            Location centre = surfaceSpot(angle, fraction);
            int ir = (int) Math.ceil(radius);
            double r2 = radius * radius;
            for (int x = -ir; x <= ir; x++) {
                for (int z = -ir; z <= ir; z++) {
                    if (x * x + z * z > r2) {
                        continue;
                    }
                    Block ground = world.getHighestBlockAt(centre.getBlockX() + x, centre.getBlockZ() + z);
                    if (ground.getType().isAir() || ground.getType() == Material.WATER) {
                        continue;
                    }
                    Block above = ground.getRelative(0, 1, 0);
                    if (Grief.setBlock(fight.griefContext(), above, Material.WATER)) {
                        flooded.add(above);
                    }
                }
            }
            Fx.sound(centre, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.7f);
        }
    }

    /**
     * A real strike at {@code target}: a genuine {@link World#strikeLightning} bolt, not the cosmetic
     * effect-only variant — batch-1 §3.4 asks for "real lightning bolts, real fire ignition" in so many
     * words, and vanilla's own bolt already deals its own damage and starts real fire on whatever it
     * hits, so reimplementing that by hand would just be a worse copy of the thing right there in the
     * API. This method's own damage is the part vanilla does not do: conducting through the whole
     * connected water body underneath, flood-filled live so it stays exactly as correct as the water
     * actually is, including whatever a player has drained or a crater has broken.
     */
    void strike(Location target, double conductedDamage) {
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        Telegraph.dangerZone(target, 2.0);
        Fx.line(target.clone().add(0, 15, 0), target.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 20);
        world.strikeLightning(target);
        Fx.sound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.3f, 0.8f);

        Block origin = world.getBlockAt(target.getBlockX(), target.getBlockY() - 1, target.getBlockZ());
        if (origin.getType() != Material.WATER) {
            return;
        }
        Set<Block> body = connectedWater(origin);
        for (Player player : fight.combatants()) {
            Block underfoot = player.getLocation().getBlock().getRelative(0, -1, 0);
            if (body.contains(underfoot) || body.contains(player.getLocation().getBlock())) {
                player.damage(conductedDamage, fight.instance().entity());
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), StormFight.STORM_WHITE, 1.6f, 20, 0.4);
            }
        }
        for (Block block : body) {
            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                Fx.burst(block.getLocation().add(0.5, 0.6, 0.5), Particle.ELECTRIC_SPARK, 3, 0.2);
            }
        }
    }

    private Set<Block> connectedWater(Block start) {
        Set<Block> visited = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty() && visited.size() < MAX_TRAVERSAL) {
            Block current = queue.poll();
            for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Block next = current.getRelative(offset[0], 0, offset[1]);
                if (visited.contains(next) || next.getType() != Material.WATER) {
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }
        return visited;
    }

    private Location surfaceSpot(double angleRadians, double fraction) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()));
        return spot;
    }
}
