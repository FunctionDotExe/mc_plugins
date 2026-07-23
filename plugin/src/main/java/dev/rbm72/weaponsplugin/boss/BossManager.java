package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registry of boss definitions plus every currently-live fight. Drives the
 * shared tick loop (one repeating task for every live instance, not one per
 * boss) and guarantees {@link #shutdownAll()} leaves no entity/task/boss bar
 * behind when the plugin disables.
 */
public final class BossManager {

    private static final long TICK_INTERVAL = 5L;
    private static final double MIN_SCALE = 1.0;
    private static final double MAX_SCALE = 2.5;

    /** Hard mode's extra multiplier on top of the normal group-size health scale. */
    private static final double HARD_MODE_HEALTH_MULTIPLIER = 1.5;
    private static final double HARD_MODE_SCALE_MULTIPLIER = 1.15;
    private static final double HARD_MODE_SPEED_MULTIPLIER = 1.2;

    private final WeaponsPlugin plugin;
    private final Map<String, Boss> bosses = new LinkedHashMap<>();
    private final Map<String, BossInstance> liveByBossId = new LinkedHashMap<>();
    private final Map<UUID, BossInstance> liveByEntity = new LinkedHashMap<>();
    private final Set<String> hardModeIds = new HashSet<>();
    private BukkitTask tickTask;

    public BossManager(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Boss boss) {
        bosses.put(boss.id(), boss);
    }

    public Optional<Boss> get(String id) {
        return Optional.ofNullable(bosses.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<Boss> all() {
        return bosses.values();
    }

    public Optional<BossInstance> spawn(String id, Location at) {
        Boss boss = bosses.get(id.toLowerCase(Locale.ROOT));
        if (boss == null) {
            return Optional.empty();
        }
        if (liveByBossId.containsKey(boss.id())) {
            return Optional.empty();
        }
        World world = at.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        LivingEntity entity = (LivingEntity) world.spawnEntity(at, boss.baseEntityType());
        entity.customName(boss.displayName());
        entity.setCustomNameVisible(true);
        entity.setPersistent(true);
        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
        }

        Arena arena = new Arena(at, boss.arenaRadius());
        int nearbyPlayers = Math.max(1, arena.playersInside().size());
        double scale = Math.min(MAX_SCALE, MIN_SCALE + 0.5 * (nearbyPlayers - 1));
        boolean hardMode = hardModeIds.contains(boss.id());
        double maxHealth = boss.maxHealth() * scale * (hardMode ? HARD_MODE_HEALTH_MULTIPLIER : 1.0);

        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(maxHealth);
        }
        entity.setHealth(maxHealthAttr != null ? maxHealthAttr.getValue() : maxHealth);

        BossInstance instance;
        try {
            instance = new BossInstance(plugin, this, boss, entity, arena, maxHealth);
        } catch (Exception e) {
            // Construction runs phase 1's onEnter synchronously (cinematics, hologram/WorldGuard
            // hookup, entrance title). If any of that throws, the entity above is already spawned
            // but was never registered into liveByBossId/liveByEntity — left alone, that's a
            // permanently orphaned mob with no boss bar, no tick loop, and no way to /bossdespawn it
            // (the manager has no record it exists). Remove it and fail the spawn cleanly instead.
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Boss '" + boss.id() + "' threw while starting its fight — removing the orphaned entity instead of leaving it stuck with no boss bar/despawn.", e);
            if (entity.isValid()) {
                entity.remove();
            }
            return Optional.empty();
        }
        if (hardMode) {
            instance.empower(HARD_MODE_SCALE_MULTIPLIER, HARD_MODE_SPEED_MULTIPLIER);
        }
        liveByBossId.put(boss.id(), instance);
        liveByEntity.put(entity.getUniqueId(), instance);

        if (tickTask == null) {
            startTickLoop();
        }
        return Optional.of(instance);
    }

    /** Flips hard mode for a boss id — takes effect on its next spawn, not the currently live fight (if any). Returns the new state. */
    public boolean toggleHardMode(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (!hardModeIds.remove(key)) {
            hardModeIds.add(key);
            return true;
        }
        return false;
    }

    public boolean isHardMode(String id) {
        return hardModeIds.contains(id.toLowerCase(Locale.ROOT));
    }

    /** True if this boss id currently has a live fight — used by the boss menu to grey out an already-spawned entry. */
    public boolean isLive(String id) {
        return liveByBossId.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Resolves a damaged/attacked entity to its live boss instance, unwrapping Ender Dragon hitbox
     * parts to the real dragon first. A dragon's head/body/wings/tail are each a distinct
     * {@link ComplexEntityPart} with their own UUID — every melee/damage event against a dragon
     * reports one of those parts as {@code getEntity()}, never the {@link org.bukkit.entity.EnderDragon}
     * itself. Looking the part's own UUID up directly always misses, which silently skipped every
     * armor/floor-clamp/crit/execute check for that boss — it wasn't tanky or squishy, it was
     * invisible to every one of those systems and took raw, unmitigated damage every hit.
     */
    public Optional<BossInstance> instanceForDamaged(Entity entity) {
        Entity real = entity instanceof ComplexEntityPart part ? part.getParent() : entity;
        return instanceFor(real.getUniqueId());
    }

    public Optional<BossInstance> instanceFor(UUID entityId) {
        return Optional.ofNullable(liveByEntity.get(entityId));
    }

    public boolean despawn(String id) {
        BossInstance instance = liveByBossId.get(id.toLowerCase(Locale.ROOT));
        if (instance == null) {
            return false;
        }
        instance.end(BossInstance.EndReason.DESPAWNED);
        return true;
    }

    void forget(BossInstance instance) {
        liveByBossId.remove(instance.boss().id());
        liveByEntity.remove(instance.entity().getUniqueId());
    }

    private void startTickLoop() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (BossInstance instance : List.copyOf(liveByBossId.values())) {
                // One instance throwing must not stop the others from ticking this cycle (and,
                // since this is a single shared repeating task, must not kill the loop outright).
                try {
                    instance.tick();
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Boss '" + instance.boss().id() + "' threw during tick — other live bosses still tick this cycle.", e);
                }
            }
        }, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void shutdownAll() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (BossInstance instance : List.copyOf(liveByBossId.values())) {
            instance.end(BossInstance.EndReason.PLUGIN_DISABLE);
        }
    }
}
