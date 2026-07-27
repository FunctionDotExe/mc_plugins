package dev.rbm72.weaponsplugin.fx;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small reusable particle/sound building blocks so individual weapons don't
 * each reimplement the same shapes.
 */
public final class Fx {

    private Fx() {
    }

    /**
     * Global VFX scale-up applied inside every helper below, so every weapon/boss ability that
     * routes through this class gets bigger, denser effects without touching 100+ call sites.
     * COUNT/DENSITY multiplies how many particles spawn per call; SPREAD widens the volume they
     * fill; SIZE enlarges dust-particle scale so colored bursts read clearly at max particle count.
     * <p>
     * Lowered from the original 3.0/1.8 (COUNT/DENSITY) after players reported boss fights reading
     * as a wall of particles rather than readable attacks — see {@link #runtimeScale()} for the
     * additional, server-adjustable knob on top of this baseline.
     */
    private static final double COUNT_MULTIPLIER = 2.0;
    private static final double SPREAD_MULTIPLIER = 1.6;
    private static final double SIZE_MULTIPLIER = 1.6;
    private static final double DENSITY_MULTIPLIER = 1.3;

    /** Particles spawned more than this far from a viewer aren't sent to them at all. */
    private static final double VIEWER_RADIUS = 64.0;
    private static final double VIEWER_RADIUS_SQUARED = VIEWER_RADIUS * VIEWER_RADIUS;

    private static final StackWalker CALLER_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final String FX_PACKAGE = Fx.class.getPackageName();
    private static final String BOSS_PACKAGE_PREFIX = "dev.rbm72.weaponsplugin.boss";

    /** Which personal slider in {@link PlayerParticlePrefs} a given effect should be scaled by. */
    enum ParticleCategory {
        WEAPON, BOSS
    }

    private static Plugin ownerPlugin;

    /** Called once from the plugin's onEnable so {@link #runtimeScale()} can read live config. */
    public static void init(Plugin plugin) {
        ownerPlugin = plugin;
    }

    /**
     * Classifies the real external call site that triggered the current particle effect as
     * weapon- or boss-side by package, so the right personal slider applies. Walks past any frames
     * still inside this class (helper methods calling each other) to find that caller — every
     * boss file lives under {@code boss.*}, everything else (weapons, accessories, armor, stones,
     * consumables) is weapon-side.
     */
    private static ParticleCategory currentCategory() {
        Class<?> caller = CALLER_WALKER.walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(c -> !c.getPackageName().equals(FX_PACKAGE))
                        .findFirst())
                .orElse(null);
        return caller != null && caller.getPackageName().startsWith(BOSS_PACKAGE_PREFIX)
                ? ParticleCategory.BOSS : ParticleCategory.WEAPON;
    }

    /**
     * The single choke point every particle spawn in this class routes through. Instead of
     * broadcasting one packet count to every nearby client via {@code World#spawnParticle},
     * this sends an individually-scaled packet to each viewer within {@link #VIEWER_RADIUS},
     * so {@code /particles weapon} / {@code /particles boss} can turn a player's own view of
     * an effect up or down without touching what anyone else sees.
     */
    private static void spawnScaled(World world, double x, double y, double z, Particle particle,
                                     int preScaledCount, double offsetX, double offsetY, double offsetZ,
                                     double extra, Object data, ParticleCategory category) {
        if (preScaledCount <= 0) {
            return;
        }
        for (Player viewer : world.getPlayers()) {
            Location viewerLoc = viewer.getLocation();
            double dx = viewerLoc.getX() - x;
            double dy = viewerLoc.getY() - y;
            double dz = viewerLoc.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > VIEWER_RADIUS_SQUARED) {
                continue;
            }
            int count = (int) Math.ceil(preScaledCount * PlayerParticlePrefs.multiplier(viewer, category));
            if (count <= 0) {
                continue;
            }
            if (data == null) {
                viewer.spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra);
            } else {
                viewer.spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data);
            }
        }
    }

    private static void spawnScaled(Location loc, Particle particle, int preScaledCount,
                                     double offsetX, double offsetY, double offsetZ, double extra,
                                     Object data, ParticleCategory category) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        spawnScaled(world, loc.getX(), loc.getY(), loc.getZ(), particle, preScaledCount,
                offsetX, offsetY, offsetZ, extra, data, category);
    }

    /**
     * Extra multiplier on top of the constants above, read live off {@code fx.particle-scale} in
     * config.yml (default 1.0) so a server can turn boss particle volume up or down — including via
     * {@code /bossparticles} — without a restart or recompile. Read live rather than cached so
     * {@code /bossreload} picks it up the same way every other tuned number in this plugin does.
     */
    public static double runtimeScale() {
        if (ownerPlugin == null) {
            return 1.0;
        }
        return Math.max(0.1, Math.min(2.0, ownerPlugin.getConfig().getDouble("fx.particle-scale", 1.0)));
    }

    private static int scaleCount(int count) {
        return (int) Math.ceil(count * COUNT_MULTIPLIER * runtimeScale());
    }

    private static double scaleSpread(double spread) {
        return spread * SPREAD_MULTIPLIER;
    }

    private static int scaleDensity(int points) {
        return (int) Math.ceil(points * DENSITY_MULTIPLIER * runtimeScale());
    }

    /** Clamped to Bukkit's hard {@code DustOptions} size range [0.01, 4.0] — exceeding it throws. */
    private static float scaleSize(float size) {
        return Math.max(0.01f, Math.min(4.0f, size * (float) SIZE_MULTIPLIER));
    }

    public static void trail(Location loc, Particle particle, int count, double spread, double speed) {
        double scaledSpread = scaleSpread(spread);
        spawnScaled(loc, particle, scaleCount(count), scaledSpread, scaledSpread, scaledSpread, speed, null, currentCategory());
    }

    public static void burst(Location loc, Particle particle, int count, double spread) {
        double scaledSpread = scaleSpread(spread);
        spawnScaled(loc, particle, scaleCount(count), scaledSpread, scaledSpread, scaledSpread, 0.05, null, currentCategory());
    }

    public static void point(Location loc, Particle particle, int count) {
        spawnScaled(loc, particle, scaleCount(count), 0, 0, 0, 0, null, currentCategory());
    }

    /**
     * A burst of block-crack particles for a specific material. {@link Particle#BLOCK} requires a
     * {@link org.bukkit.block.data.BlockData} object per-call — calling it through {@link #burst} or
     * a raw {@code spawnParticle} with no data throws at the server level every time. This is the
     * only safe way to spawn it.
     */
    public static void blockBurst(Location loc, Material material, int count, double spread) {
        double scaledSpread = scaleSpread(spread);
        spawnScaled(loc, Particle.BLOCK, scaleCount(count), scaledSpread, scaledSpread, scaledSpread, 0.1,
                material.createBlockData(), currentCategory());
    }

    /**
     * A bright screen-flash particle at a single point. {@link Particle#FLASH} is one of the handful
     * of particle types that require a data object per-call (a {@link Color}, unused visually but
     * mandatory) — calling it through {@link #burst}/{@link #point}/a raw {@code spawnParticle} with
     * no data throws at the server level every time. This is the only safe way to spawn it.
     */
    public static void flash(Location loc, int count) {
        spawnScaled(loc, Particle.FLASH, count, 0, 0, 0, 0, Color.WHITE, currentCategory());
    }

    /**
     * A burst/point of dragon-breath particles. {@link Particle#DRAGON_BREATH} requires a
     * {@link Float} data value per-call — calling it through {@link #burst}/{@link #point}/{@link #line}
     * with no data throws at the server level every time. This is the only safe way to spawn it.
     */
    public static void dragonBreathBurst(Location loc, int count, double spread) {
        double scaledSpread = scaleSpread(spread);
        spawnScaled(loc, Particle.DRAGON_BREATH, scaleCount(count), scaledSpread, scaledSpread, scaledSpread, 0.05,
                1.0f, currentCategory());
    }

    /** Same data requirement as {@link #dragonBreathBurst}, laid out along a line instead of a burst. */
    public static void dragonBreathLine(Location from, Location to, int points) {
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        ParticleCategory category = currentCategory();
        Vector step = to.toVector().subtract(from.toVector());
        int scaledPoints = scaleDensity(points);
        for (int i = 0; i <= scaledPoints; i++) {
            double t = (double) i / scaledPoints;
            Location point = from.clone().add(step.clone().multiply(t));
            spawnScaled(point, Particle.DRAGON_BREATH, 1, 0, 0, 0, 0, 1.0f, category);
        }
    }

    /** A single expanding ring of particles at a fixed radius. */
    public static void ring(Location center, Particle particle, double radius, int points) {
        ring(center, particle, radius, points, 0);
    }

    /**
     * Same as {@link #ring} but rotated by {@code angleOffsetRadians}. Callers that already
     * run their own per-tick {@code BukkitRunnable} (whirlpools, quakes, slams) just need to
     * accumulate an angle each tick and pass it here to turn a static circle into a spinning one.
     */
    public static void ring(Location center, Particle particle, double radius, int points, double angleOffsetRadians) {
        ringInternal(center, particle, radius, points, angleOffsetRadians, currentCategory());
    }

    private static void ringInternal(Location center, Particle particle, double radius, int points,
                                      double angleOffsetRadians, ParticleCategory category) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int scaledPoints = scaleDensity(points);
        for (int i = 0; i < scaledPoints; i++) {
            double angle = angleOffsetRadians + (2 * Math.PI * i) / scaledPoints;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            spawnScaled(world, x, center.getY(), z, particle, 1, 0, 0, 0, 0, null, category);
        }
    }

    /**
     * One frame of a vertical spinning column (tornado core, rising vortex, orbiting runes).
     * {@code strands} points are spaced evenly around the vertical axis through {@code base},
     * offset upward by {@code height}, rotated by {@code angleOffsetRadians}. Call every tick
     * from the ability's own runnable with an incrementing angle (and, for a rising effect,
     * an incrementing height) to animate it.
     */
    public static void helixFrame(Location base, Particle particle, double radius, int strands, double angleOffsetRadians, double height) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        ParticleCategory category = currentCategory();
        int scaledStrands = scaleDensity(strands);
        for (int i = 0; i < scaledStrands; i++) {
            double angle = angleOffsetRadians + (2 * Math.PI * i) / scaledStrands;
            double x = base.getX() + radius * Math.cos(angle);
            double z = base.getZ() + radius * Math.sin(angle);
            spawnScaled(world, x, base.getY() + height, z, particle, 1, 0, 0, 0, 0, null, category);
        }
    }

    /**
     * Several rings growing outward over a few ticks — used for slams/shockwaves. The category is
     * resolved once up front (from the real caller, synchronously) and captured for every tick of
     * the runnable, since by the time it fires later the call stack no longer has anything to do
     * with the code that originally scheduled it.
     */
    public static void expandingRings(Plugin plugin, Location center, Particle particle, double maxRadius, int rings, long tickInterval) {
        ParticleCategory category = currentCategory();
        new BukkitRunnable() {
            int ring = 0;

            @Override
            public void run() {
                if (ring >= rings) {
                    cancel();
                    return;
                }
                double radius = maxRadius * (ring + 1) / (double) rings;
                ringInternal(center, particle, radius, 20 + ring * 6, 0, category);
                ring++;
            }
        }.runTaskTimer(plugin, 0L, tickInterval);
    }

    /** Dark-red dust burst used as shared "impact" feedback on every weapon hit. */
    public static void bloodSpray(Location loc) {
        Particle.DustOptions blood = new Particle.DustOptions(Color.fromRGB(138, 3, 3), scaleSize(1.3f));
        spawnScaled(loc, Particle.DUST, scaleCount(10), 0.25, 0.25, 0.25, 0, blood, currentCategory());
    }

    /** A custom-colored dust burst — gives a weapon's signature ability its own identity instead of a generic particle. */
    public static void coloredBurst(Location loc, Color color, float dustSize, int count, double spread) {
        Particle.DustOptions dust = new Particle.DustOptions(color, scaleSize(dustSize));
        double scaledSpread = scaleSpread(spread);
        spawnScaled(loc, Particle.DUST, scaleCount(count), scaledSpread, scaledSpread, scaledSpread, 0, dust, currentCategory());
    }

    /** Same as {@link #ring} but every point is a custom-colored dust particle instead of a fixed type. */
    public static void coloredRing(Location center, Color color, float dustSize, double radius, int points, double angleOffsetRadians) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        ParticleCategory category = currentCategory();
        Particle.DustOptions dust = new Particle.DustOptions(color, scaleSize(dustSize));
        int scaledPoints = scaleDensity(points);
        for (int i = 0; i < scaledPoints; i++) {
            double angle = angleOffsetRadians + (2 * Math.PI * i) / scaledPoints;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            spawnScaled(world, x, center.getY(), z, Particle.DUST, 1, 0, 0, 0, 0, dust, category);
        }
    }

    /** A straight line of particles between two points — arcs, darts, beams. */
    public static void line(Location from, Location to, Particle particle, int points) {
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        ParticleCategory category = currentCategory();
        Vector step = to.toVector().subtract(from.toVector());
        int scaledPoints = scaleDensity(points);
        for (int i = 0; i <= scaledPoints; i++) {
            double t = (double) i / scaledPoints;
            Location point = from.clone().add(step.clone().multiply(t));
            spawnScaled(point, particle, 1, 0, 0, 0, 0, null, category);
        }
    }

    public static void sound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static void sound(Location loc, Sound sound, float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(loc, sound, volume, pitch);
    }

    /**
     * Spawns a small glowing {@link ItemDisplay} that spins in place at a fixed location and
     * despawns itself after {@code durationTicks}. The modern, resource-pack-free replacement for
     * the old "invisible armor stand holding an item" trick — no hitbox, no nametag, no equipment
     * slot to fight with, and rotation is a real interpolated transform instead of a head-pose hack.
     * Used for floating orbs/runes/icons that sell a signature ability (a soul orb over a summon,
     * a core at the center of a whirlpool, and so on).
     */
    public static ItemDisplay spinningIcon(Plugin plugin, Location loc, Material material, float scale, int durationTicks, double yawDegreesPerTick) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        ItemDisplay display = world.spawn(loc, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(material));
            // GROUND is the "dropped item" display context — Mojang calibrates it to a small,
            // centered size for every item shape (flat 2D icons and full 3D block models alike).
            // FIXED (the item-frame context) skips that shrink for most items and renders them
            // close to full block size, which is what was blowing icons up to fill the screen.
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTransformation(spinTransform(scale, 0f));
            entity.setPersistent(false);
        });

        new BukkitRunnable() {
            int ticks = 0;
            float yaw = 0f;

            @Override
            public void run() {
                if (ticks >= durationTicks || display.isDead()) {
                    if (!display.isDead()) {
                        display.remove();
                    }
                    cancel();
                    return;
                }
                yaw += (float) yawDegreesPerTick;
                display.setTransformation(spinTransform(scale, yaw));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return display;
    }

    /**
     * Cosmetic-only physical debris: glowing, non-pickup-able dropped items flung outward from
     * {@code origin} in random directions, self-removing after {@code durationTicks}. Gives an
     * ultimate/finale a "things are actually flying apart" beat that pure particles can't sell —
     * used for the big two-boss capstone cinematics. Never touches gameplay (no hurtbox, no drops).
     * Callers should track the returned entities with the boss instance for fight-end cleanup.
     */
    public static List<Item> shatterDebris(Plugin plugin, Location origin, Material material, int count, double speed, int durationTicks) {
        World world = origin.getWorld();
        if (world == null) {
            return List.of();
        }
        List<Item> spawned = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Item item = world.dropItem(origin, new ItemStack(material));
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setGlowing(true);
            item.setUnlimitedLifetime(true);
            item.setGravity(true);
            double dx = ThreadLocalRandom.current().nextDouble(-1, 1);
            double dz = ThreadLocalRandom.current().nextDouble(-1, 1);
            double dy = ThreadLocalRandom.current().nextDouble(0.4, 1.0);
            Vector direction = new Vector(dx, dy, dz).normalize().multiply(speed);
            item.setVelocity(direction);
            spawned.add(item);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!item.isDead()) {
                        item.remove();
                    }
                }
            }.runTaskLater(plugin, durationTicks);
        }
        return spawned;
    }

    private static final int DAMAGE_NUMBER_DURATION_TICKS = 16;

    /**
     * Spawns a floating {@link TextDisplay} showing a hit's damage amount, rising and fading over
     * {@link #DAMAGE_NUMBER_DURATION_TICKS} — far more readable at a glance than particle-only
     * feedback, especially in a crowded boss fight. Crits render bigger and gold instead of white.
     */
    public static void damageNumber(Plugin plugin, Location loc, double amount, boolean crit) {
        damageNumber(plugin, loc, amount, crit, false);
    }

    /**
     * Same as {@link #damageNumber(Plugin, Location, double, boolean)}, but an execute-threshold hit
     * gets its own skull-tagged dark red styling — the biggest damage spikes in a fight (finishing
     * blows below the execute threshold) should read as distinct from an ordinary crit at a glance.
     * Execute styling wins over crit when a hit is both.
     */
    public static void damageNumber(Plugin plugin, Location loc, double amount, boolean crit, boolean execute) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        String text = String.format(Locale.ROOT, "%.1f", amount);
        NamedTextColor color = execute ? NamedTextColor.DARK_RED : crit ? NamedTextColor.GOLD : NamedTextColor.WHITE;
        String tag = execute ? "☠" : crit ? "✦" : "";
        float scale = execute ? 1.5f : crit ? 1.35f : 1.0f;
        double dx = ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
        double dz = ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
        Location spawnAt = loc.clone().add(dx, 0, dz);

        TextDisplay display = world.spawn(spawnAt, TextDisplay.class, entity -> {
            entity.text(Component.text((tag.isEmpty() ? "" : tag + " ") + text + (tag.isEmpty() ? "" : " " + tag), color)
                    .decoration(TextDecoration.BOLD, execute));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            entity.setShadowed(true);
            entity.setSeeThrough(true);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 1, 0)));
            entity.setPersistent(false);
        });

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= DAMAGE_NUMBER_DURATION_TICKS || display.isDead()) {
                    if (!display.isDead()) {
                        display.remove();
                    }
                    cancel();
                    return;
                }
                display.teleport(display.getLocation().add(0, 0.06, 0));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static Transformation spinTransform(float scale, float yawDegrees) {
        return new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) Math.toRadians(yawDegrees), 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0));
    }

    /**
     * Spawns a thin glowing {@link BlockDisplay} column from {@code base} up to {@code base + height}
     * and removes it after {@code durationTicks} — a solid, physically-present beam/pillar prop rather
     * than a fan of loose particles. Good for light-pillar, laser-beam, and obelisk-style ultimates.
     */
    public static BlockDisplay glowPillar(Plugin plugin, Location base, Material material, float thickness, float height, int durationTicks) {
        World world = base.getWorld();
        if (world == null) {
            return null;
        }
        float half = thickness / 2f;
        BlockDisplay display = world.spawn(base, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTransformation(new Transformation(
                    new Vector3f(-half, 0, -half),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(thickness, height, thickness),
                    new AxisAngle4f(0, 0, 1, 0)));
            entity.setPersistent(false);
        });

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!display.isDead()) {
                    display.remove();
                }
            }
        }.runTaskLater(plugin, durationTicks);

        return display;
    }
}
