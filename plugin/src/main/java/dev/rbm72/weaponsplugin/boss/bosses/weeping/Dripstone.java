package dev.rbm72.weaponsplugin.boss.bosses.weeping;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P2's ceiling, and the Colossus's own tears. Real pointed dripstone grows above marked floor tiles and
 * falls; the fallen spike <b>stays</b>, so every dodge costs the group a tile of a room that is already
 * shrinking (batch-3 §5.4). The tears pool as real water on the floor, which slows anyone wading through
 * them — a second, quieter tax on space.
 * <p>
 * Targets clusters rather than individuals on purpose (§5.3): the danger is vertical while the walls are
 * horizontal, and the only way to be safe from both is to be spread out in a room with less and less room
 * to spread out in.
 */
final class Dripstone {

    private final WeepingFight fight;
    private final List<Block> fallen = new ArrayList<>();

    private int growCooldownTicks;
    private int tearCooldownTicks;
    private int pinnedPlayers;

    Dripstone(WeepingFight fight) {
        this.fight = fight;
    }

    void pulse(int intervalTicks) {
        growCooldownTicks -= intervalTicks;
        tearCooldownTicks -= intervalTicks;
        if (growCooldownTicks <= 0) {
            growCooldownTicks = fight.config().num("dripstone-interval-ticks", 160);
            grow();
        }
        if (tearCooldownTicks <= 0) {
            tearCooldownTicks = fight.config().num("tears-interval-ticks", 120);
            pool();
        }
    }

    /** Growth count = 1 + 1 per player (§5.4) — more bodies means more ceiling, not harder ceiling. */
    private void grow() {
        World world = fight.world();
        List<Player> targets = fight.combatants();
        if (world == null || targets.isEmpty()) {
            return;
        }
        int count = 1 + fight.playerCount();
        double height = fight.config().dbl("dripstone-height", 9.0);
        double damage = fight.config().dbl("dripstone-damage", 14.0);
        double radius = fight.config().dbl("dripstone-radius", 1.8);
        int growTicks = fight.config().num("dripstone-grow-ticks", 40);

        for (int i = 0; i < count; i++) {
            Location target = clusterSpot(targets);
            Location column = new Location(world, target.getBlockX(),
                    fight.floorY(target.getBlockX(), target.getBlockZ()) + 1, target.getBlockZ());
            Telegraph.dangerZone(column, radius);
            Fx.burst(column.clone().add(0.5, height, 0.5), Particle.DRIPPING_DRIPSTONE_WATER, 12, 0.3);
            Fx.sound(column, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 0.7f, 1.6f);
            fight.plugin().getServer().getScheduler().runTaskLater(fight.plugin(), () -> {
                if (!fight.instance().entity().isValid()) {
                    return;
                }
                Grief.dropAsBlock(fight.griefContext(), column, Material.POINTED_DRIPSTONE, height,
                        damage, radius, landed -> {
                            Block block = landed.getBlock();
                            fallen.add(block);
                            Fx.sound(landed, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.4f, 0.8f);
                        });
            }, growTicks);
        }
    }

    /**
     * A cluster if there is one, otherwise a lone player. Two players inside the cluster radius means the
     * spike lands between them and both have to move, which is the "punishes clustering" clause.
     */
    private Location clusterSpot(List<Player> targets) {
        double clusterRadius = fight.config().dbl("dripstone-cluster-radius", 4.0);
        Player seed = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        double x = 0;
        double z = 0;
        int count = 0;
        for (Player player : targets) {
            if (player.getLocation().distance(seed.getLocation()) <= clusterRadius) {
                x += player.getLocation().getX();
                z += player.getLocation().getZ();
                count++;
            }
        }
        if (count > 1) {
            pinnedPlayers = Math.max(pinnedPlayers, count);
        }
        Location at = seed.getLocation().clone();
        if (count > 0) {
            at.setX(x / count);
            at.setZ(z / count);
        }
        return at;
    }

    /** Real water, pooling where it falls. Vanilla does the slowing; nothing here simulates it. */
    private void pool() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int pools = Math.max(1, fight.config().num("tears-pools-per-cycle", 2));
        double fraction = fight.config().dbl("tears-placement-fraction", 0.6);
        for (int i = 0; i < pools; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double distance = fight.instance().arena().radius() * fraction
                    * ThreadLocalRandom.current().nextDouble();
            Location centre = fight.instance().arena().center();
            int x = (int) Math.floor(centre.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(centre.getZ() + Math.sin(angle) * distance);
            Block block = world.getBlockAt(x, fight.floorY(x, z) + 1, z);
            if (!block.getType().isAir()) {
                continue;
            }
            if (Grief.setMechanicBlock(fight.griefContext(), block, Material.WATER)) {
                Fx.burst(block.getLocation().add(0.5, 0.3, 0.5), Particle.SPLASH, 10, 0.3);
            }
        }
    }

    /** Fallen spikes still standing — the floor obstruction the group has to fight around from here on. */
    int obstructions() {
        int standing = 0;
        for (Block block : fallen) {
            if (block.getType() == Material.POINTED_DRIPSTONE) {
                standing++;
            }
        }
        return standing;
    }

    /** The largest cluster a spike has been dropped on — P2 exits on a cycle survived without one. */
    int largestCluster() {
        return pinnedPlayers;
    }

    void resetClusterWatch() {
        pinnedPlayers = 0;
    }

    void discardAll() {
        fallen.clear();
    }
}
