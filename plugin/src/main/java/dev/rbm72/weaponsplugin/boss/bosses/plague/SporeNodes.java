package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P2's Spore Nodes: physical growths that spread real corrupted ground (mud, soul sand, fungal growth —
 * {@link PlagueFight#CORRUPTED_GROUND}) outward from themselves for as long as they live. Struck down
 * with ordinary melee, same {@link BlockDamageEvent}-chip approach {@code StormPylons} uses — reaching
 * and destroying one <em>is</em> the objective, so a second interaction on top of a hit would just be a
 * lock on a door the group already has to open.
 */
final class SporeNodes {

    private final PlagueFight fight;
    private final List<Node> nodes = new ArrayList<>();
    private Handler handler;

    private static final class Node {
        Block core;
        double hp;
        double radius;
        boolean destroyed;
    }

    SporeNodes(PlagueFight fight) {
        this.fight = fight;
    }

    void build() {
        if (!nodes.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        boolean solo = fight.playerCount() <= 1;
        int count = fight.config().num("spore-node-count", solo ? 3 : 5);
        double fraction = fight.config().dbl("spore-node-fraction", 0.7);
        double maxHp = fight.config().dbl("spore-node-max-hp", 30.0);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            Grief.setMechanicBlock(fight.griefContext(), block, Material.SCULK);
            Node node = new Node();
            node.core = block;
            node.hp = maxHp;
            nodes.add(node);
            Fx.coloredBurst(spot.clone().add(0, 0.6, 0), PlagueFight.TOXIC, 1.4f, 20, 0.4);
        }
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    /** Every live node spreads its own patch further outward — passive terrain denial while it stands. */
    void pulse(int intervalTicks) {
        for (Node node : nodes) {
            if (node.destroyed) {
                continue;
            }
            node.radius = Math.min(fight.config().dbl("spore-node-max-radius", 8.0),
                    node.radius + fight.config().dbl("spore-node-growth-per-second", 0.1) * (intervalTicks / 20.0));
            if (node.radius >= 1.0) {
                Material material = PlagueFight.CORRUPTED_GROUND[
                        ThreadLocalRandom.current().nextInt(PlagueFight.CORRUPTED_GROUND.length)];
                Grief.spread(fight.griefContext(), node.core.getLocation(), material, node.radius);
            }
            Fx.burst(node.core.getLocation().add(0.5, 0.4, 0.5), org.bukkit.Particle.SPORE_BLOSSOM_AIR, 4, 0.3);
        }
    }

    int destroyedCount() {
        int destroyed = 0;
        for (Node node : nodes) {
            if (node.destroyed) {
                destroyed++;
            }
        }
        return destroyed;
    }

    int total() {
        return nodes.size();
    }

    private void destroy(Node node) {
        node.destroyed = true;
        Grief.setMechanicBlock(fight.griefContext(), node.core, Material.AIR);
        Location at = node.core.getLocation().add(0.5, 0.5, 0.5);
        Fx.coloredBurst(at, PlagueFight.SICKLY, 2.4f, 60, 0.9);
        Fx.sound(at, Sound.BLOCK_SCULK_BREAK, 1.4f, 0.8f);
        for (var player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("A SPORE NODE FALLS", NamedTextColor.GREEN),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    void discard() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        nodes.clear();
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

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(BlockDamageEvent event) {
            for (Node node : nodes) {
                if (node.destroyed || !node.core.equals(event.getBlock())) {
                    continue;
                }
                node.hp -= fight.config().dbl("spore-node-hit-damage", 5.0);
                Fx.blockBurst(event.getBlock().getLocation().add(0.5, 0.5, 0.5), Material.SCULK, 12, 0.4);
                if (node.hp <= 0) {
                    destroy(node);
                }
                return;
            }
        }
    }
}
