package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Dragon Elder's signature: real, continuously-thrown {@link LargeFireball} entities that a player
 * can hit back. This is the boss's whole reason melee has anything to do all fight — batch-2 §4:
 * "hit one back and it damages the dragon, which is the single most satisfying vanilla interaction in
 * the game and the reason this fight isn't archer-only."
 * <p>
 * <b>Deliberately not left to vanilla's own fireball-explosion physics.</b> Every other destructive
 * primitive in this roster funnels through {@link Grief} so terrain damage stays grief-gated and
 * ledgered; a raw incendiary {@code LargeFireball} left to explode on its own bypasses both — it always
 * breaks blocks regardless of the server's grief toggle, and it writes blocks the ledger never records.
 * So every fireball spawned here is inert on its own ({@code yield 0}, non-incendiary) and this class
 * takes full manual control of the impact: real damage applied directly, and {@link
 * Grief#explosion} for the fire/crater flourish, exactly like {@code FireballBarrageAttack} already
 * does for its own molten-block volley.
 * <p>
 * <b>Deflection.</b> A player swinging at a live fireball fires {@link EntityDamageByEntityEvent} with
 * the fireball as the victim — the same event vanilla's own Ghast-fireball deflection reacts to. Rather
 * than lean on that NMS behaviour sight-unseen (this cannot be verified without a live server), the
 * reversal is done explicitly here: velocity flips and speeds up, and {@link
 * LargeFireball#setShooter} is reassigned to the attacking player. If the now player-owned fireball
 * goes on to strike the dragon, this class applies the hit directly with the deflecting player as the
 * credited damager, which is what lets it flow through {@code BossDamageListener}'s ordinary
 * per-attacker pipeline (phase floor, filter rules) exactly like a melee swing would.
 */
final class Fireballs {

    private final DragonFight fight;
    private final Set<UUID> live = new HashSet<>();
    private final Map<UUID, UUID> deflectedBy = new HashMap<>();
    private final List<UUID> recentTargets = new ArrayList<>();

    private int deflections;
    private BukkitTask spawnTask;
    private Handler handler;

    Fireballs(DragonFight fight) {
        this.fight = fight;
    }

    int deflectionCount() {
        return deflections;
    }

    /** Arms the deflection listener (fight-scoped, registered once) and starts this phase's throw rate. */
    void arm(int baseIntervalTicks) {
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
        restart(baseIntervalTicks);
    }

    /** Re-paced for a new phase without dropping the listener or the deflection count. */
    void restart(int intervalTicks) {
        cancelSpawnTask();
        int scaled = Math.max(20, intervalTicks - (fight.playerCount() - 1) * fight.config().num("fireball-interval-reduction-per-player", 8));
        spawnTask = fight.plugin().getServer().getScheduler().runTaskTimer(fight.plugin(), this::spawnOne, scaled, scaled);
        fight.instance().trackTask(spawnTask);
    }

    /** Stops new throws without tearing down the deflection listener — used by P4, which grounds the boss for good. */
    void stopSpawning() {
        cancelSpawnTask();
    }

    void discardAll() {
        cancelSpawnTask();
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        for (UUID id : live) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        live.clear();
        deflectedBy.clear();
    }

    private void cancelSpawnTask() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            spawnTask.cancel();
        }
        spawnTask = null;
    }

    private void spawnOne() {
        LivingEntity boss = fight.instance().entity();
        if (!boss.isValid()) {
            return;
        }
        Player target = pickTarget();
        if (target == null) {
            return;
        }
        World world = boss.getWorld();
        Location from = boss.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(from.toVector()).normalize();

        LargeFireball fireball = world.spawn(from, LargeFireball.class, entity -> {
            entity.setShooter(boss);
            entity.setYield(0f);
            entity.setIsIncendiary(false);
            entity.setGravity(false);
            entity.setPersistent(false);
        });
        fireball.setVelocity(direction.multiply(fight.config().dbl("fireball-speed", 1.15)));
        fight.instance().trackEntity(fireball);
        live.add(fireball.getUniqueId());

        Fx.coloredBurst(from, DragonFight.EMBER, 1.4f, 20, 0.5);
        Fx.sound(from, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.2f, 0.7f);
    }

    /** Rotates among current combatants so throws "target distinct players" rather than piling onto one. */
    private Player pickTarget() {
        List<Player> present = new ArrayList<>(fight.combatants());
        if (present.isEmpty()) {
            return null;
        }
        List<Player> stillPresent = present;
        recentTargets.removeIf(id -> stillPresent.stream().noneMatch(p -> p.getUniqueId().equals(id)));
        if (present.size() > 1) {
            List<Player> untargeted = present.stream()
                    .filter(p -> !recentTargets.contains(p.getUniqueId())).toList();
            if (!untargeted.isEmpty()) {
                present = untargeted;
            }
        }
        Player chosen = present.get(ThreadLocalRandom.current().nextInt(present.size()));
        recentTargets.add(chosen.getUniqueId());
        while (recentTargets.size() > 2) {
            recentTargets.remove(0);
        }
        return chosen;
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeflectSwing(EntityDamageByEntityEvent event) {
            if (!(event.getEntity() instanceof LargeFireball fireball) || !live.contains(fireball.getUniqueId())) {
                return;
            }
            if (!(event.getDamager() instanceof Player player)) {
                return;
            }
            event.setCancelled(true);
            Vector reversed = fireball.getVelocity().multiply(-1);
            if (reversed.lengthSquared() < 1.0E-4) {
                reversed = player.getLocation().getDirection();
            }
            fireball.setVelocity(reversed.normalize().multiply(fight.config().dbl("fireball-deflect-speed", 1.7)));
            fireball.setShooter(player);
            deflectedBy.put(fireball.getUniqueId(), player.getUniqueId());
            deflections++;

            Location at = fireball.getLocation();
            Fx.coloredBurst(at, DragonFight.EMBER, 1.8f, 30, 0.5);
            Fx.sound(at, Sound.ITEM_SHIELD_BLOCK, 1.3f, 1.4f);
            Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onImpact(ProjectileHitEvent event) {
            if (!(event.getEntity() instanceof LargeFireball fireball) || !live.remove(fireball.getUniqueId())) {
                return;
            }
            UUID deflectorId = deflectedBy.remove(fireball.getUniqueId());
            Location impact = fireball.getLocation();
            LivingEntity boss = fight.instance().entity();

            if (deflectorId != null && event.getHitEntity() != null && event.getHitEntity().equals(boss)) {
                Player deflector = Bukkit.getPlayer(deflectorId);
                if (deflector != null && boss.isValid()) {
                    boss.damage(fight.config().dbl("fireball-deflect-damage", 24.0), deflector);
                }
                Fx.coloredBurst(boss.getLocation().add(0, 1.2, 0), DragonFight.DRAGON_RED, 2.2f, 45, 0.8);
                Fx.sound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 1.2f, 1.0f);
            } else {
                double damage = fight.config().dbl("fireball-impact-damage", 14.0);
                double radius = fight.config().dbl("fireball-impact-radius", 3.2);
                float power = (float) fight.config().dbl("fireball-impact-power", 1.6);
                for (Player victim : Arena.combatants(impact, radius)) {
                    victim.damage(damage, boss);
                    victim.setFireTicks(Math.max(victim.getFireTicks(), fight.config().num("fireball-burn-ticks", 60)));
                }
                Grief.explosion(fight.griefContext(), impact, power);
            }
            if (fireball.isValid()) {
                fireball.remove();
            }
        }

        /** Belt-and-braces: a stray vanilla auto-explosion from the fireball must never break blocks unrecorded. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onAutoExplode(EntityExplodeEvent event) {
            if (event.getEntity() instanceof LargeFireball fireball && live.contains(fireball.getUniqueId())) {
                event.blockList().clear();
            }
        }
    }
}
