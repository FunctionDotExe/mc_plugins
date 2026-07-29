package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The mass the Bulk keeps shedding, and the arithmetic that makes killing it a decision. Every Bulkling
 * death is classified at the moment it happens: inside a catalyst's radius it feeds the floor, outside it
 * is clean (batch-3 §3.2). Left alive too long it gets reabsorbed and the Bulk heals — so the correct
 * play is a narrow band between killing too eagerly and killing too slowly, and both edges of that band
 * are counted here.
 */
final class Bulklings {

    private final BulkFight fight;
    private final Map<UUID, Integer> ageTicks = new HashMap<>();

    private Handler handler;
    private int shedCooldownTicks;

    private int cleanKills;
    private int fedKills;

    private boolean reabsorbing;
    private UUID reabsorbTarget;
    private int reabsorbTelegraphTicks;
    private int reabsorbCooldownTicks;
    private int reabsorbDenied;
    private int reabsorbCompleted;

    Bulklings(BulkFight fight) {
        this.fight = fight;
    }

    void arm() {
        if (handler != null) {
            return;
        }
        handler = new Handler();
        fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
    }

    // --------------------------------------------------------------- shedding

    void pulseShedding(int intervalTicks) {
        for (Map.Entry<UUID, Integer> entry : ageTicks.entrySet()) {
            entry.setValue(entry.getValue() + intervalTicks);
        }
        ageTicks.keySet().removeIf(id -> {
            Entity entity = fight.plugin().getServer().getEntity(id);
            return entity == null || !entity.isValid();
        });

        shedCooldownTicks -= intervalTicks;
        if (shedCooldownTicks > 0) {
            return;
        }
        int cap = fight.config().num("bulkling-cap", 3 + fight.playerCount());
        shedCooldownTicks = Math.max(20, fight.config().num("bulkling-shed-interval-ticks", 140)
                / Math.max(1, fight.playerCount()));
        if (alive().size() >= cap) {
            return;
        }
        shed();
    }

    private void shed() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location bossAt = fight.instance().entity().getLocation();
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        Location at = bossAt.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
        at.setY(world.getHighestBlockYAt(at.getBlockX(), at.getBlockZ()) + 1);

        LivingEntity add = fight.instance().addManager().spawn(world, at, EntityType.SLIME, entity -> {
            if (entity instanceof Slime slime) {
                slime.setSize(Math.max(1, fight.config().num("bulkling-size", 2)));
            }
            entity.setPersistent(false);
            entity.setRemoveWhenFarAway(false);
            var max = entity.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(fight.config().dbl("bulkling-health", 18.0));
                entity.setHealth(max.getValue());
            }
            entity.customName(Component.text("Bulkling", NamedTextColor.GREEN));
            entity.setCustomNameVisible(false);
        });
        fight.instance().trackEntity(add);
        ageTicks.put(add.getUniqueId(), 0);
        Fx.burst(at.clone().add(0, 0.5, 0), Particle.ITEM_SLIME, 18, 0.4);
        Fx.sound(at, Sound.ENTITY_SLIME_SQUISH, 1.0f, 1.3f);
    }

    List<LivingEntity> alive() {
        List<LivingEntity> live = new ArrayList<>();
        for (UUID id : ageTicks.keySet()) {
            Entity entity = fight.plugin().getServer().getEntity(id);
            if (entity instanceof LivingEntity living && living.isValid()) {
                live.add(living);
            }
        }
        return live;
    }

    int aliveCount() {
        return alive().size();
    }

    int cleanKills() {
        return cleanKills;
    }

    int fedKills() {
        return fedKills;
    }

    // ----------------------------------------------------------- reabsorption

    /**
     * P3's clamp on the other edge of the band. It reaches for its oldest shed piece with a long, obvious
     * telegraph; killing that Bulkling first denies the pull, and letting it home heals and swells the
     * Bulk. One target at a time (§3.4), so this is always a solvable ask however few players there are.
     */
    void pulseReabsorption(int intervalTicks) {
        if (!reabsorbing) {
            if (reabsorbTarget == null) {
                reabsorbCooldownTicks -= intervalTicks;
                if (reabsorbCooldownTicks <= 0) {
                    beginReabsorb();
                }
            }
            return;
        }
        LivingEntity target = target();
        if (target == null) {
            // Killed inside the window: the pull is denied, which is exactly the phase's exit condition.
            reabsorbDenied++;
            endReabsorb();
            Fx.sound(fight.instance().arena().center(), Sound.ENTITY_SLIME_DEATH, 1.4f, 1.4f);
            return;
        }
        if (reabsorbTelegraphTicks > 0) {
            reabsorbTelegraphTicks -= intervalTicks;
            Fx.line(fight.instance().entity().getLocation().add(0, 1, 0),
                    target.getLocation().add(0, 0.5, 0), Particle.ITEM_SLIME, 16);
            return;
        }
        Location bossAt = fight.instance().entity().getLocation();
        Vector pull = bossAt.toVector().subtract(target.getLocation().toVector());
        if (pull.length() <= fight.config().dbl("reabsorb-arrive-distance", 2.5)) {
            completeReabsorb(target);
            return;
        }
        target.setVelocity(target.getVelocity().add(pull.normalize()
                .multiply(fight.config().dbl("reabsorb-pull-strength", 0.22))));
        Fx.line(bossAt.clone().add(0, 1, 0), target.getLocation().add(0, 0.5, 0), Particle.ITEM_SLIME, 16);
    }

    private void beginReabsorb() {
        List<LivingEntity> candidates = alive();
        if (candidates.isEmpty()) {
            reabsorbCooldownTicks = fight.config().num("reabsorb-retry-ticks", 60);
            return;
        }
        LivingEntity oldest = candidates.get(0);
        int bestAge = -1;
        for (LivingEntity candidate : candidates) {
            int age = ageTicks.getOrDefault(candidate.getUniqueId(), 0);
            if (age > bestAge) {
                bestAge = age;
                oldest = candidate;
            }
        }
        reabsorbing = true;
        reabsorbTarget = oldest.getUniqueId();
        reabsorbTelegraphTicks = fight.config().num("reabsorb-telegraph-ticks", 50);
        Fx.coloredBurst(oldest.getLocation().add(0, 1, 0), BulkFight.OOZE_GREEN, 2.0f, 34, 0.6);
        Fx.sound(oldest.getLocation(), Sound.ENTITY_SLIME_JUMP, 1.6f, 0.5f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("IT IS PULLING ONE BACK — kill it first", NamedTextColor.GREEN),
                    2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void completeReabsorb(LivingEntity target) {
        reabsorbCompleted++;
        var max = fight.instance().entity().getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            double heal = fight.config().dbl("reabsorb-heal", 30.0);
            fight.instance().entity().setHealth(
                    Math.min(max.getValue(), fight.instance().entity().getHealth() + heal));
        }
        ageTicks.remove(target.getUniqueId());
        target.remove();
        Location at = fight.instance().entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.5, 0), BulkFight.OOZE_GREEN, 2.6f, 50, 0.9);
        Fx.sound(at, Sound.ENTITY_SLIME_SQUISH, 1.6f, 0.4f);
        endReabsorb();
    }

    private void endReabsorb() {
        reabsorbing = false;
        reabsorbTarget = null;
        reabsorbTelegraphTicks = 0;
        reabsorbCooldownTicks = fight.config().num("reabsorb-interval-ticks", 220);
    }

    private LivingEntity target() {
        if (reabsorbTarget == null) {
            return null;
        }
        Entity entity = fight.plugin().getServer().getEntity(reabsorbTarget);
        return entity instanceof LivingEntity living && living.isValid() ? living : null;
    }

    boolean reabsorbing() {
        return reabsorbing;
    }

    int reabsorbDenied() {
        return reabsorbDenied;
    }

    int reabsorbCompleted() {
        return reabsorbCompleted;
    }

    // -------------------------------------------------------------- lifecycle

    /** P4: it stops shedding entirely and whatever is still on the floor is pulled home for free. */
    void stopShedding() {
        shedCooldownTicks = Integer.MAX_VALUE;
        reabsorbing = false;
        reabsorbTarget = null;
        for (Iterator<UUID> it = ageTicks.keySet().iterator(); it.hasNext(); ) {
            Entity entity = fight.plugin().getServer().getEntity(it.next());
            if (entity != null) {
                entity.remove();
            }
            it.remove();
        }
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        ageTicks.clear();
    }

    private final class Handler implements Listener {

        /**
         * The whole boss, in one branch: where a Bulkling dies decides whether the group made progress or
         * paid for it. Only tracked adds count, so a wandering cow does not sculk the arena.
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeath(EntityDeathEvent event) {
            UUID id = event.getEntity().getUniqueId();
            if (ageTicks.remove(id) == null) {
                return;
            }
            Location at = event.getEntity().getLocation();
            event.getDrops().clear();
            event.setDroppedExp(0);
            if (fight.catalysts().inRange(at)) {
                fedKills++;
                fight.sculk().bloom(at);
                return;
            }
            cleanKills++;
            Fx.burst(at.clone().add(0, 0.5, 0), Particle.ITEM_SLIME, 14, 0.4);
        }
    }
}
