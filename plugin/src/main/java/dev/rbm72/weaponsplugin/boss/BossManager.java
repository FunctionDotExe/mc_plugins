package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.modifier.BossModifier;
import dev.rbm72.weaponsplugin.boss.modifier.BossModifiers;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
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

    /** How far past an arena's radius an explosion still counts as that fight's damage to record. */
    private static final double EXPLOSION_LEDGER_MARGIN = 24.0;
    private static final double MIN_SCALE = 1.0;
    private static final double MAX_SCALE = 2.5;

    private final WeaponsPlugin plugin;
    private final Map<String, Boss> bosses = new LinkedHashMap<>();
    private final Map<String, BossInstance> liveByBossId = new LinkedHashMap<>();
    private final Map<UUID, BossInstance> liveByEntity = new LinkedHashMap<>();
    private final BossModifiers modifiers;
    private BukkitTask tickTask;

    public BossManager(WeaponsPlugin plugin) {
        this.plugin = plugin;
        this.modifiers = new BossModifiers(plugin);
    }

    /**
     * Which composable affixes are armed for each boss. Read at spawn and then fixed for that fight —
     * a live fight never changes difficulty under the group in it.
     */
    public BossModifiers modifiers() {
        return modifiers;
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
        // Snapshotted once, here, and handed to the instance: the affix set is read at spawn and then
        // fixed for the whole fight, so an admin arming an affix mid-pull can never change the rules
        // under the group already in the arena.
        Set<BossModifier> affixes = modifiers.of(boss.id());

        BossInstance instance;
        double maxHealth;
        int nearbyPlayers;
        try {
            entity.customName(boss.displayName());
            entity.setCustomNameVisible(true);
            entity.setPersistent(true);
            // Tagged before anything else can throw: an entity that fails mid-setup is exactly the one
            // that ends up orphaned, and the tag is what lets /bosskillall find it later.
            BossEntities.markBoss(entity);
            if (entity instanceof Mob mob) {
                mob.setRemoveWhenFarAway(false);
            }

            Arena arena = new Arena(at, boss.arenaRadius());
            nearbyPlayers = Math.max(1, (int) arena.playersInside().stream().filter(Arena::isCombatant).count());
            double scale = Math.min(MAX_SCALE, MIN_SCALE + 0.5 * (nearbyPlayers - 1));
            maxHealth = boss.maxHealth() * scale * modifiers.healthMultiplier(boss.id());

            AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(maxHealth);
            }
            entity.setHealth(maxHealthAttr != null ? maxHealthAttr.getValue() : maxHealth);

            // Every attack a boss makes is telegraphed and scheduled by this framework. The base mob's
            // own melee goal is not: it swings on a vanilla cooldown with no wind-up, no cast bar and no
            // config key, and on a Warden it swings for 30. Zeroing the attribute is what makes that
            // impossible rather than merely unlikely — target/anger suppression runs on a 5-tick pulse
            // and a vanilla swing fits between two of them. Scripted damage is unaffected: attacks pass
            // an explicit amount to victim.damage(...) and never read this attribute. A slipped swing now
            // arrives as a 0-damage event, which BossDamageListener drops so it cannot even tilt a camera.
            AttributeInstance attackDamageAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attackDamageAttr != null) {
                attackDamageAttr.setBaseValue(0.0);
            }

            // Bumps every boss up to actual-boss size by default. Runs before BossInstance
            // construction below, so a boss with its own deliberate size (set in its first phase's
            // onEnter, e.g. AmalgamatedBulk/WeepingColossus/Voidwyrm) simply overrides this value.
            AttributeInstance entityScaleAttr = entity.getAttribute(Attribute.SCALE);
            if (entityScaleAttr != null) {
                entityScaleAttr.setBaseValue(boss.baseScale());
            }

            // Construction runs phase 1's onEnter synchronously (cinematics, hologram/WorldGuard
            // hookup, entrance title). Everything from entity setup through here is one unit: if any
            // of it throws, the entity above is already spawned but was never registered into
            // liveByBossId/liveByEntity — left alone, that's a permanently orphaned mob with no boss
            // bar, no tick loop, and no way to /bossdespawn it (the manager has no record it exists).
            // Remove it and fail the spawn cleanly instead of leaking the exception up to the command
            // dispatcher as a generic "unexpected error".
            instance = new BossInstance(plugin, this, boss, entity, arena, maxHealth, affixes);
        } catch (Exception | LinkageError e) {
            // LinkageError too, not just Exception: an optional integration (WorldGuard/DecentHolograms)
            // that isn't installed can fail to *link* its classes when first touched during construction,
            // which surfaces as NoClassDefFoundError (a LinkageError, not an Exception). Catching only
            // Exception let that escape and leave a spawned-but-unregistered orphan mob — no boss bar, no
            // tick loop, no way to despawn it. Treat a link failure exactly like any other spawn failure.
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Boss '" + boss.id() + "' threw while starting its fight — removing the orphaned entity instead of leaving it stuck with no boss bar/despawn.", e);
            if (entity.isValid()) {
                entity.remove();
            }
            return Optional.empty();
        }
        double affixScale = modifiers.scaleMultiplier(boss.id());
        double affixSpeed = modifiers.speedMultiplier(boss.id());
        if (affixScale != 1.0 || affixSpeed != 1.0) {
            instance.empower(affixScale, affixSpeed);
        }
        liveByBossId.put(boss.id(), instance);
        liveByEntity.put(entity.getUniqueId(), instance);

        if (tickTask == null) {
            startTickLoop();
        }
        // One line per spawn, deliberately at INFO. A boss that registers here is guaranteed to be
        // ticking (boss bar, targeting, attacks); one that never logs never got this far. Both "no boss
        // bar" reports so far were impossible to tell apart from reading code alone — this makes the
        // difference visible in the log, along with the world and health it actually spawned with.
        long loggedHealth = Math.round(maxHealth);
        int loggedPlayers = nearbyPlayers;
        String loggedAffixes = affixes.isEmpty() ? "" : ", affixes " + modifiers.names(boss.id());
        plugin.getLogger().info(() -> "Boss '" + boss.id() + "' spawned in world '" + world.getName()
                + "' at " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ()
                + " with " + loggedHealth + " HP (" + loggedPlayers + " player(s) in arena"
                + loggedAffixes + ")");
        return Optional.of(instance);
    }

    /**
     * Flips {@link BossModifier#HARD} for a boss id — takes effect on its next spawn, not the currently
     * live fight (if any). Returns the new state.
     * <p>
     * Kept as its own method on top of the general affix registry because {@code /bosshardmode} and the
     * boss menu's shift-click are muscle memory, and "hard mode" is the one affix worth a dedicated
     * shortcut. Everything else goes through {@code /bossaffix}.
     */
    public boolean toggleHardMode(String id) {
        return modifiers.toggle(id, BossModifier.HARD);
    }

    public boolean isHardMode(String id) {
        return modifiers.has(id, BossModifier.HARD);
    }

    /** True if this boss id currently has a live fight — used by the boss menu to grey out an already-spawned entry. */
    public boolean isLive(String id) {
        return liveByBossId.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /**
     * True while this player is standing in a live boss fight, and therefore already spending two
     * boss-bar slots on that fight (the health bar and the mechanic readout).
     * <p>
     * Exists so other systems can stand down from the boss-bar area during a fight. Minecraft stacks
     * boss bars at a fixed vertical stride and draws each bar's <em>name above it</em>, so past about
     * four bars the names start colliding with the bar above — which in play reads as the mechanic
     * readout being unreadable at exactly the moment it matters most.
     */
    public boolean isInFight(Player player) {
        for (BossInstance instance : liveByBossId.values()) {
            if (instance.barViewers().contains(player)) {
                return true;
            }
        }
        return false;
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

    /**
     * The live fight whose arena contains {@code loc}, if any.
     * <p>
     * Exists for terrain damage that doesn't come from a boss entity we can identify — an explosion, a
     * lit TNT block — where the only thing we know is where it went off. Whatever fight owns that
     * ground owns the undo log for it.
     */
    public Optional<BossInstance> instanceCovering(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return Optional.empty();
        }
        for (BossInstance instance : liveByBossId.values()) {
            Arena arena = instance.arena();
            World arenaWorld = arena.world();
            if (arenaWorld == null || !arenaWorld.equals(loc.getWorld())) {
                continue;
            }
            // Generous margin: attacks deliberately reach past the strict radius (thrown blocks land
            // long, knockback shoves fights outward), and a blast just outside the line still has to
            // be recorded or it punches a permanent hole through an otherwise clean rollback.
            double reach = arena.radius() + EXPLOSION_LEDGER_MARGIN;
            if (loc.distanceSquared(arena.center()) <= reach * reach) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    public boolean despawn(String id) {
        BossInstance instance = liveByBossId.get(id.toLowerCase(Locale.ROOT));
        if (instance == null) {
            return false;
        }
        instance.end(BossInstance.EndReason.DESPAWNED);
        return true;
    }

    /**
     * Ends every live fight and restores every arena synchronously, in one pass — {@code /bossclear}'s
     * "reset everything, right now" behind an admin command that has no single boss id to target.
     * Goes through the exact same {@link BossInstance#end} teardown as {@link #despawn}, so it can never
     * leak an entity/task/meter hold that despawn wouldn't; the only difference is
     * {@link BossInstance.EndReason#CLEARED} forces its ledger restore immediate instead of batched.
     *
     * @return how many live fights were cleared.
     */
    public int clearAll() {
        List<BossInstance> live = List.copyOf(liveByBossId.values());
        for (BossInstance instance : live) {
            instance.end(BossInstance.EndReason.CLEARED);
        }
        return live.size();
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

    /** What one {@code /bosskillall} actually did, so the command can report both halves separately. */
    public record KillAllResult(int fightsCleared, int entitiesRemoved) {
    }

    /**
     * {@code /bosskillall}: everything {@link #clearAll()} does, plus a sweep for engine entities no
     * live fight owns any more.
     * <p>
     * The two halves answer different failures and neither covers the other. {@code clearAll} works off
     * the live registry, so it is exact and it restores arenas — but an orphan is by definition not in
     * that registry, and no amount of clearing will touch it. The sweep works off the world, so it
     * finds orphans — but it has no ledger for them and cannot put their terrain back. Run together
     * they are the operator escape hatch: no boss mob of ours survives, and every arena a live fight
     * was still holding goes back to how it started.
     */
    public KillAllResult killAll() {
        int cleared = clearAll();
        int removed = BossEntities.sweep(this);
        plugin.getLogger().info(() -> "/bosskillall: cleared " + cleared + " live fight(s) and removed "
                + removed + " boss engine entity/entities.");
        return new KillAllResult(cleared, removed);
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
