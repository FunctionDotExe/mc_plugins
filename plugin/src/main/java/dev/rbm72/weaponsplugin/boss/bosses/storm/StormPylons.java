package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Breeze;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P3's Storm Pylons: real physical structures at the arena edge, each guarded by a real Breeze. While
 * any stands, the phase's {@code filterDamage} lets only currently-discharged players hurt him at all
 * (batch-1 §3.3) — expressed there, not here, because a damage <em>rule</em> belongs on the phase
 * mechanic that owns {@code filterDamage} and this class only has to answer "how many still stand".
 * <p>
 * Struck down with ordinary melee (a {@link BlockDamageEvent} chips their health the same way a real
 * hit would) rather than requiring a special interaction — reaching one at all, through height and a
 * Breeze in the way, is the actual objective; another button on top of that would be a second lock on
 * a door the group already has to climb to.
 */
final class StormPylons {

    private final StormFight fight;
    private final List<Pylon> pylons = new ArrayList<>();
    private Handler handler;

    private static final class Pylon {
        Block core;
        double hp;
        double maxHp;
        UUID guardId;
        boolean destroyed;
    }

    StormPylons(StormFight fight) {
        this.fight = fight;
    }

    void build() {
        if (!pylons.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        boolean solo = fight.playerCount() <= 1;
        int count = fight.config().num("pylon-count", solo ? 2 : 4);
        double fraction = fight.config().dbl("pylon-fraction", 0.92);
        double maxHp = fight.config().dbl("pylon-max-hp", 40.0);
        // §3.3: "to reach a pylon you have to use wind charges... or build up temporary scaffolding
        // towers" — a 3-tall totem sitting at ground height was reachable by plain walking, which never
        // exercised that puzzle at all. Tall enough that nobody clears it with a jump alone.
        int height = fight.config().num("pylon-height", 7);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            for (int y = 0; y < height; y++) {
                Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY() + y, spot.getBlockZ());
                Material material = y == height - 1 ? Material.LIGHTNING_ROD : Material.COPPER_BLOCK;
                Grief.setMechanicBlock(fight.griefContext(), block, material);
            }
            Pylon pylon = new Pylon();
            pylon.core = world.getBlockAt(spot.getBlockX(), spot.getBlockY() + height - 1, spot.getBlockZ());
            pylon.hp = maxHp;
            pylon.maxHp = maxHp;
            Breeze guard = world.spawn(spot.clone().add(0, height - 1, 0), Breeze.class);
            guard.setPersistent(false);
            fight.instance().trackEntity(guard);
            pylon.guardId = guard.getUniqueId();
            pylons.add(pylon);
            Fx.coloredBurst(spot.clone().add(0, 1.5, 0), StormFight.STORM_YELLOW, 1.6f, 30, 0.6);
        }
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    int standingCount() {
        int count = 0;
        for (Pylon pylon : pylons) {
            if (!pylon.destroyed) {
                count++;
            }
        }
        return count;
    }

    int destroyedCount() {
        return pylons.size() - standingCount();
    }

    boolean anyStanding() {
        return standingCount() > 0;
    }

    /**
     * Total HP fraction chipped off every pylon so far (destroyed ones count as a full point each) — a
     * monotonic progress signal so a group genuinely wearing pylons down without quite destroying one
     * resets the floor-lock timeout's clock, the same way a completed destruction already does. Without
     * this, sustained damage on a standing pylon looked identical to no engagement at all.
     */
    double damageProgress() {
        double total = 0.0;
        for (Pylon pylon : pylons) {
            total += pylon.destroyed ? 1.0 : Math.max(0.0, 1.0 - pylon.hp / pylon.maxHp);
        }
        return total;
    }

    void pulse() {
        for (Pylon pylon : pylons) {
            if (pylon.destroyed) {
                continue;
            }
            Fx.coloredBurst(pylon.core.getLocation().add(0.5, 1.5, 0.5), StormFight.STORM_YELLOW, 1.0f, 4, 0.25);
            Location beam = pylon.core.getLocation().add(0.5, 2, 0.5);
            Fx.line(beam, fight.instance().entity().getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 20);
        }
    }

    private void destroy(Pylon pylon) {
        pylon.destroyed = true;
        Grief.setMechanicBlock(fight.griefContext(), pylon.core, Material.AIR);
        Location at = pylon.core.getLocation().add(0.5, 1, 0.5);
        Fx.coloredBurst(at, StormFight.STORM_WHITE, 2.6f, 70, 1.0);
        Fx.sound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.6f, 0.7f);
        var guard = fight.plugin().getServer().getEntity(pylon.guardId);
        if (guard != null) {
            guard.remove();
        }
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("A PYLON FALLS", NamedTextColor.YELLOW),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        for (Pylon pylon : pylons) {
            var guard = fight.plugin().getServer().getEntity(pylon.guardId);
            if (guard != null) {
                guard.remove();
            }
        }
        pylons.clear();
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
            for (Pylon pylon : pylons) {
                if (pylon.destroyed || !pylon.core.equals(event.getBlock())) {
                    continue;
                }
                pylon.hp -= fight.config().dbl("pylon-hit-damage", 4.0);
                Fx.blockBurst(event.getBlock().getLocation().add(0.5, 0.5, 0.5), Material.COPPER_BLOCK, 12, 0.4);
                if (pylon.hp <= 0) {
                    destroy(pylon);
                }
                return;
            }
        }

        /** The guard Breeze dropping a real wind charge on death — batch-1 §3.4: "kill them, they drop wind charges". */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onGuardDeath(EntityDeathEvent event) {
            if (!(event.getEntity() instanceof Breeze) || event.getEntity().getType() != EntityType.BREEZE) {
                return;
            }
            for (Pylon pylon : pylons) {
                if (pylon.guardId != null && pylon.guardId.equals(event.getEntity().getUniqueId())) {
                    event.getDrops().add(new ItemStack(Material.WIND_CHARGE, 2));
                    return;
                }
            }
        }
    }
}
