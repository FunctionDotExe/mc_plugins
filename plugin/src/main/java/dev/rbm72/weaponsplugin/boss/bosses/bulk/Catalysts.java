package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The sculk catalyst nodes the whole boss is built around (batch-3 §3.1): a mob dying inside one's
 * influence radius blooms sculk across the arena floor, so the reflexive play — kill everything, kill it
 * where it stands — is the losing one.
 * <p>
 * Each node is an {@link ArenaTotem} wearing a real sculk catalyst rather than a placed catalyst block,
 * for two reasons. It has to be <em>destructible</em>, which a block is not without inventing hit
 * detection for it; and a real catalyst block is a tile entity, which {@code ArenaLedger} refuses to
 * overwrite once placed, so a placed node could never be removed mid-fight. The bloom itself is driven
 * by {@link SculkFloor} rather than by vanilla's own catalyst behaviour for the same ledger reason —
 * sculk vanilla grows on its own is sculk the arena restore has never heard of, and this fight grows a
 * lot of it.
 */
final class Catalysts {

    private final BulkFight fight;
    private final List<ArenaTotem> nodes = new ArrayList<>();

    Catalysts(BulkFight fight) {
        this.fight = fight;
    }

    /** Node count = 2 + 1 per 2 players (§3.4) — more bodies means more clean ground to fight on, not more danger. */
    void place() {
        if (!nodes.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = fight.config().num("catalyst-count", 2 + fight.playerCount() / 2);
        double fraction = fight.config().dbl("catalyst-placement-fraction", 0.55);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double health = fight.config().dbl("catalyst-health", 60.0);

        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            ArenaTotem node = ArenaTotem.spawn(fight.plugin(), fight.instance(), spot, Material.SCULK_CATALYST,
                    Component.text("Catalyst Node", NamedTextColor.DARK_AQUA),
                    health,
                    Math.max(1200, fight.config().num("catalyst-lifetime-ticks", 24000)),
                    destroyed -> onDestroyed(destroyed.location()),
                    expired -> {
                    });
            nodes.add(node);
            Fx.burst(spot.clone().add(0, 1, 0), Particle.SCULK_SOUL, 20, 0.5);
            Fx.sound(spot, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.2f, 0.7f);
        }
    }

    private void onDestroyed(Location at) {
        Fx.burst(at.clone().add(0, 1, 0), Particle.SCULK_CHARGE_POP, 30, 0.6);
        Fx.sound(at, Sound.BLOCK_SCULK_CATALYST_BREAK, 1.4f, 0.6f);
    }

    /** Draws each live node's influence radius on the floor — the rule has to be visible to be obeyed. */
    void pulse() {
        prune();
        double radius = radius();
        for (ArenaTotem node : nodes) {
            Fx.ring(node.location().add(0, 0.3, 0), Particle.SCULK_SOUL, radius, 22);
        }
    }

    double radius() {
        return fight.config().dbl("catalyst-radius", 8.0);
    }

    /** True if a death here would feed the Bulk — the single question the whole fight is about. */
    boolean inRange(Location at) {
        prune();
        double radius = radius();
        for (ArenaTotem node : nodes) {
            Location nodeAt = node.location();
            if (nodeAt.getWorld() == null || !nodeAt.getWorld().equals(at.getWorld())) {
                continue;
            }
            double dx = nodeAt.getX() - at.getX();
            double dz = nodeAt.getZ() - at.getZ();
            if (Math.sqrt(dx * dx + dz * dz) <= radius) {
                return true;
            }
        }
        return false;
    }

    int standing() {
        prune();
        return nodes.size();
    }

    /** P4 removes the catalysts entirely — no more adds, no more nodes, just the mass and the floor. */
    void discardAll() {
        for (ArenaTotem node : nodes) {
            node.discard();
        }
        nodes.clear();
    }

    private void prune() {
        for (Iterator<ArenaTotem> it = nodes.iterator(); it.hasNext(); ) {
            if (!it.next().isValid()) {
                it.remove();
            }
        }
    }

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
