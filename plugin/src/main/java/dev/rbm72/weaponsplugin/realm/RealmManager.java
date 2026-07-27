package dev.rbm72.weaponsplugin.realm;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns every realm's dimension: creates each realm's world lazily on first entry (or reattaches to
 * it after a restart, since Bukkit doesn't auto-load worlds it didn't see in server.properties) and
 * caches it for the life of the server. Dropping a player into a realm lands them somewhere random
 * on the open fight floor (so a group doesn't stack on one tile) and, if that realm's boss isn't
 * already live there, spawns it on the throne dais — the realm exists only to host that one fight.
 */
public final class RealmManager {

    private static final double DEFAULT_RADIUS = 36.0;
    private static final long AMBIENT_INTERVAL_TICKS = 60L;
    /** Random player landing spots stay this far inside the wall/moat band, clear of any pillar footing. */
    private static final double LANDING_WALL_MARGIN = 10.0;

    private final WeaponsPlugin plugin;
    private final Map<String, World> worlds = new HashMap<>();
    private final Map<String, Integer> radii = new HashMap<>();

    public RealmManager(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Teleports {@code player} into {@code realm}'s arena (a random spot on the open floor), spawning
     * its boss on the dais if it isn't already live.
     *
     * @return true if the player actually made it in. Callers that charge the player for entry (the
     *         realm crystal) must only consume the item when this returns true — world creation
     *         touches a lot of Bukkit surface and a failure part-way through used to leave the player
     *         standing where they were, minus a crystal, with no idea what had happened.
     */
    public boolean enter(Player player, Realm realm) {
        WorldLookup lookup;
        try {
            lookup = worldFor(realm);
        } catch (Exception | LinkageError e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Realm '" + realm.id() + "' failed to open — the player keeps their crystal.", e);
            player.sendMessage(Component.text("That realm could not be opened. Your crystal is intact.",
                    NamedTextColor.RED));
            return false;
        }
        World world = lookup.world();
        int radius = radiusFor(realm, world);
        Location landing = randomLanding(world, radius);
        player.teleport(landing);
        player.playSound(landing, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 0.7f);
        player.sendMessage(Component.text("You step into ", NamedTextColor.LIGHT_PURPLE)
                .append(realm.displayName())
                .append(Component.text(".", NamedTextColor.LIGHT_PURPLE)));

        // A freshly-created world (this player's first-ever entry into this realm) gets extra ticks
        // before the boss spawn runs: worldFor just returned from WorldCreator.createWorld() in this
        // same tick, and the arena's initial "who's actually here" snapshot (BossManager.spawn reads
        // it for health/scale) is only reliable once the player's world-membership and location have
        // fully settled — which is not guaranteed within a single deferred tick immediately after a
        // cross-dimension teleport into a dimension that didn't exist a moment ago. A world Bukkit
        // already had loaded (every entry after the first) doesn't have this hazard, so it keeps the
        // original one-tick defer.
        long spawnDelayTicks = lookup.freshlyCreated() ? 3L : 1L;
        plugin.bossManager().get(realm.bossId()).ifPresent(boss -> {
            if (!plugin.bossManager().isLive(boss.id())) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> plugin.bossManager().spawn(boss.id(), throneSpawn(world)), spawnDelayTicks);
            }
        });
        return true;
    }

    private record WorldLookup(World world, boolean freshlyCreated) {
    }

    /**
     * This realm's arena radius, computed on demand rather than read from a map only
     * {@link #createWorld} populates. Bukkit keeps a world loaded across a plugin reload and can load
     * one it finds on disk at startup, so {@code worldFor} routinely returns an existing world without
     * ever calling {@code createWorld} — leaving the old {@code radii.get(...)} to unbox a null and
     * throw, which killed the entry (and ate the crystal) for every realm already generated.
     */
    private int radiusFor(Realm realm, World world) {
        Integer cached = radii.get(realm.id());
        if (cached != null) {
            return cached;
        }
        double arenaRadius = plugin.bossManager().get(realm.bossId()).map(Boss::arenaRadius).orElse(DEFAULT_RADIUS);
        int radius = (int) Math.ceil(arenaRadius);
        radii.put(realm.id(), radius);
        return radius;
    }

    /** The fixed throne-dais point — always used for the boss itself and as the world's default spawn. */
    private Location throneSpawn(World world) {
        return new Location(world, 0.5, RealmChunkGenerator.spawnY(), 0.5);
    }

    /**
     * A random point on the open fight floor, clear of the dais and the wall/moat/pillar band.
     * {@code getHighestBlockYAt} queries the terrain actually generated at that column, so this
     * lands correctly on top of whatever height a bumpy/terraced floor style put there — never
     * inside a block.
     */
    private Location randomLanding(World world, int radius) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double innerBound = RealmChunkGenerator.DAIS_RADIUS + 3.0;
        double outerBound = Math.max(innerBound + 5.0, radius - LANDING_WALL_MARGIN);
        double dist = rng.nextDouble(innerBound, outerBound);
        double angle = rng.nextDouble(0, Math.PI * 2);
        int bx = (int) Math.round(dist * Math.cos(angle));
        int bz = (int) Math.round(dist * Math.sin(angle));
        int y = world.getHighestBlockYAt(bx, bz) + 1;
        return new Location(world, bx + 0.5, y, bz + 0.5);
    }

    private WorldLookup worldFor(Realm realm) {
        World cached = worlds.get(realm.id());
        if (cached != null) {
            return new WorldLookup(cached, false);
        }
        World world = plugin.getServer().getWorld(realm.worldName());
        boolean freshlyCreated = world == null;
        if (world == null) {
            world = createWorld(realm);
        }
        worlds.put(realm.id(), world);
        return new WorldLookup(world, freshlyCreated);
    }

    private World createWorld(Realm realm) {
        ArenaTheme theme = realm.theme();
        double arenaRadius = plugin.bossManager().get(realm.bossId()).map(Boss::arenaRadius).orElse(DEFAULT_RADIUS);
        int radius = (int) Math.ceil(arenaRadius);
        radii.put(realm.id(), radius);
        WorldCreator creator = new WorldCreator(realm.worldName())
                .generator(new RealmChunkGenerator(theme, radius))
                .environment(theme.environment())
                .type(WorldType.FLAT)
                .generateStructures(false);
        World world = creator.createWorld();
        world.setSpawnLocation(throneSpawn(world));
        // Force the dais chunk fully generated and loaded right now, synchronously, rather than
        // leaving it to whatever loads it later (the boss spawning there, or the player's proximity).
        // A boss spawned into a chunk that is still settling into the world's tracked/ticking state —
        // which only a just-created custom-generated world can be in, an already-existing one never
        // hits this — has been seen reporting entity.isValid() == false for a tick or two right after
        // spawn, which used to tear the whole fight down instantly (see the debounce in
        // BossInstance#tick). Getting the chunk fully synchronously here closes the gap this class's
        // other first-entry races (this file's history is nothing but exactly this shape of bug).
        world.getChunkAt(throneSpawn(world));
        // No constrained world border: the physical wall is the only containment a player should
        // ever see. Left at Bukkit's default (effectively unbounded), so no border shimmer renders.
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        // Only NORMAL worlds have a day/night clock. Calling setTime on a NETHER or THE_END realm
        // throws "Cannot set time in world without world clock" — and since this runs partway through
        // world setup, that exception escaped createWorld, aborted enter() before it could teleport the
        // player or spawn the boss, and left the realm half-built (never cached, ambience never
        // started) while the crystal that triggered it had already been consumed. Three realms use
        // NETHER, so every first entry into one of them silently ate the player's crystal.
        if (world.getEnvironment() == World.Environment.NORMAL) {
            world.setTime(18000L);
        }
        if (theme.forceRain()) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setStorm(true);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
        } else {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setStorm(false);
        }
        startAmbience(world, radius, theme);
        return world;
    }

    /** Drifting themed particles (and, for storm-themed realms, harmless lightning) — sky flavor a custom dimension can't get otherwise. */
    private void startAmbience(World world, int radius, ArenaTheme theme) {
        Particle particle = theme.emberParticle();
        if (particle == null && !theme.periodicLightning()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            double x = rng.nextDouble(-radius, radius);
            double z = rng.nextDouble(-radius, radius);
            if (particle != null) {
                double y = RealmChunkGenerator.PLATFORM_Y + rng.nextDouble(40, 70);
                if (particle == Particle.DUST) {
                    world.spawnParticle(Particle.DUST, x, y, z, 6, radius * 0.3, 4.0, radius * 0.3, 0.0,
                            new Particle.DustOptions(theme.emberColor(), 2.2f));
                } else {
                    world.spawnParticle(particle, x, y, z, 6, radius * 0.3, 4.0, radius * 0.3, 0.02);
                }
            }
            if (theme.periodicLightning() && rng.nextInt(4) == 0) {
                double groundY = RealmChunkGenerator.PLATFORM_Y + RealmChunkGenerator.DAIS_HEIGHT + 1;
                world.strikeLightningEffect(new Location(world, x, groundY, z));
            }
        }, AMBIENT_INTERVAL_TICKS, AMBIENT_INTERVAL_TICKS);
    }
}
