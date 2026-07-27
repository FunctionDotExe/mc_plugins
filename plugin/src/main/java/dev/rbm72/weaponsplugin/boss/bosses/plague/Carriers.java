package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P1's Bloated Carriers: slow, swelling adds that burst into a real lingering cloud on death — a
 * deliberate cleanse trade (batch-1 §4.3/§4.4). Killed away from the group, the burst is free
 * pressure release for whoever wants to step into it; killed on top of the group, it is mass Infection.
 * Nothing forces either choice — the placement decision is the entire point.
 */
final class Carriers {

    private final PlagueFight fight;
    private final Set<UUID> carrierIds = new HashSet<>();
    private Handler handler;

    Carriers(PlagueFight fight) {
        this.fight = fight;
    }

    void spawn() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = fight.config().num("carrier-base-count", 2) + fight.playerCount();
        Location centre = fight.instance().arena().center();
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(centre, angle, 0.5);
            var carrier = fight.instance().addManager().spawn(world, spot, EntityType.ZOMBIE_VILLAGER, entity -> {
                var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(maxHealth.getBaseValue() * 1.4);
                    entity.setHealth(maxHealth.getBaseValue());
                }
                var scale = entity.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(scale.getBaseValue() * 1.3);
                }
                entity.setPersistent(false);
            });
            carrierIds.add(carrier.getUniqueId());
        }
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    private Location surfaceSpot(Location centre, double angleRadians, double fraction) {
        World world = centre.getWorld();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * fraction;
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }

    void discard() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        carrierIds.clear();
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeath(EntityDeathEvent event) {
            if (!carrierIds.remove(event.getEntity().getUniqueId())) {
                return;
            }
            Location at = event.getEntity().getLocation();
            World world = at.getWorld();
            if (world == null) {
                return;
            }
            double radius = fight.config().dbl("carrier-burst-radius", 4.0);
            AreaEffectCloud cloud = world.spawn(at, AreaEffectCloud.class, entity -> {
                entity.setRadius((float) radius);
                entity.setDuration(fight.config().num("carrier-burst-duration-ticks", 200));
                entity.addCustomEffect(new PotionEffect(PotionEffectType.POISON,
                        fight.config().num("carrier-burst-poison-ticks", 60), 0), true);
                entity.setParticle(Particle.SPORE_BLOSSOM_AIR);
                entity.setColor(PlagueFight.TOXIC);
            });
            fight.instance().trackEntity(cloud);
            Fx.coloredBurst(at.clone().add(0, 1, 0), PlagueFight.TOXIC, 2.4f, 60, 1.0);
            Fx.sound(at, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.2f, 0.6f);
        }
    }
}
