package dev.rbm72.weaponsplugin.boss.props;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A destructible arena hazard prop — a "totem"/"sigil" that isn't a mob in any gameplay sense
 * (no AI, no attacks, never targets or hurts anyone by touch) but stands as a real, hittable object
 * players must break, with its own HP pool. Backed by an AI-disabled, invulnerable-to-everything-but-
 * players mob so it gets a free real hitbox instead of reinventing click detection.
 * <p>
 * Two outcomes, wired up by the attack that spawns it: {@code onDestroyed} fires the instant its HP
 * hits zero from a player hit; {@code onExpired} fires if it survives its full lifetime untouched.
 */
public final class ArenaTotem {

    private static final Map<UUID, ArenaTotem> ACTIVE = new HashMap<>();

    private final WeaponsPlugin plugin;
    private final LivingEntity entity;
    private final double maxHealth;
    private final Component baseName;
    private final Consumer<ArenaTotem> onDestroyed;
    private final Consumer<ArenaTotem> onExpired;
    private double health;
    private boolean resolved;

    private ArenaTotem(WeaponsPlugin plugin, LivingEntity entity, double maxHealth, Component baseName,
                        Consumer<ArenaTotem> onDestroyed, Consumer<ArenaTotem> onExpired) {
        this.plugin = plugin;
        this.entity = entity;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.baseName = baseName;
        this.onDestroyed = onDestroyed;
        this.onExpired = onExpired;
    }

    /** How far above/below the requested height a snapped placement may end up before it's rejected. */
    private static final int MAX_SNAP_DRIFT = 24;
    /** Blocks of clear space a placement needs above it — a totem is a mob-sized hitbox players must reach and hit. */
    private static final int REQUIRED_HEADROOM = 2;

    /**
     * Snaps a requested spawn point onto reachable, standable ground so a weak point / sigil is never
     * buried in a hillside, perched in a treetop, or left floating out of melee reach (a NoAI totem
     * has gravity off and never falls to correct itself).
     * <p>
     * Searches downward from a little above the requested height for the first block that is real
     * ground — solid, not a tree part — with {@link #REQUIRED_HEADROOM} clear blocks above it. Both
     * halves of that test matter: without the tree filter a point lands on the canopy of whatever oak
     * the fight wandered under, and without the headroom check it lands under one, boxed in by leaves
     * players have to chop through to reach the thing gating all damage on the boss.
     * <p>
     * Starting from {@code refY} rather than the surface keeps the search local to the fight instead
     * of teleporting a point to the top of a hill the arena merely touches. If nothing qualifies
     * within {@link #MAX_SNAP_DRIFT} the surface column is the fallback, and the raw requested height
     * the last resort.
     */
    private static Location groundSnap(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return loc;
        }
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        double refY = loc.getY();

        // A few blocks of slack upward: the boss can be standing one step below a ledge the ring
        // wants a point on, and starting exactly at refY would walk straight past it.
        int from = (int) Math.floor(refY) + 4;
        Integer standable = firstStandableBelow(world, bx, bz, from, refY);
        if (standable != null) {
            return new Location(world, bx + 0.5, standable, bz + 0.5, loc.getYaw(), loc.getPitch());
        }

        // Nothing usable near the requested height (a cliff face, a deep overhang): fall back to the
        // surface of this column, which is at least somewhere a player can walk to.
        int surface = Math.min(world.getMaxHeight() - 1, world.getHighestBlockYAt(bx, bz) + 1);
        standable = firstStandableBelow(world, bx, bz, surface, refY);
        if (standable != null) {
            return new Location(world, bx + 0.5, standable, bz + 0.5, loc.getYaw(), loc.getPitch());
        }
        return new Location(world, bx + 0.5, refY, bz + 0.5, loc.getYaw(), loc.getPitch());
    }

    /**
     * Walks down column {@code (bx, bz)} from {@code fromY} and returns the Y a totem should stand at
     * (one above the first real ground with clear headroom), or null if nothing within
     * {@link #MAX_SNAP_DRIFT} of {@code refY} qualifies.
     */
    private static Integer firstStandableBelow(World world, int bx, int bz, int fromY, double refY) {
        int y = Math.min(world.getMaxHeight() - 1, fromY);
        int floor = world.getMinHeight() + 1;
        while (y > floor) {
            Material type = world.getBlockAt(bx, y, bz).getType();
            if (isGround(type) && hasHeadroom(world, bx, y, bz)) {
                if (Math.abs((y + 1) - refY) <= MAX_SNAP_DRIFT) {
                    return y + 1;
                }
                return null;
            }
            y--;
        }
        return null;
    }

    /** Real, standable ground — solid but not part of a tree, which is scenery a weak point must never sit on or in. */
    private static boolean isGround(Material type) {
        return type.isSolid() && !isTreePart(type);
    }

    /** True if the blocks directly above {@code y} are clear enough for a mob-sized totem to be seen and hit. */
    private static boolean hasHeadroom(World world, int bx, int y, int bz) {
        for (int offset = 1; offset <= REQUIRED_HEADROOM; offset++) {
            Material above = world.getBlockAt(bx, y + offset, bz).getType();
            if (above.isSolid() || isTreePart(above)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tree trunk/canopy a weak point should never perch on or be buried under — walked past to reach
     * real ground, and disqualifying as headroom.
     */
    private static boolean isTreePart(Material type) {
        String name = type.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_LEAVES")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE") || name.contains("MUSHROOM_BLOCK");
    }

    /** True if this entity (from any Bukkit event) is a currently-live arena totem. */
    public static boolean isTotem(UUID entityId) {
        return ACTIVE.containsKey(entityId);
    }

    public static ArenaTotem get(UUID entityId) {
        return ACTIVE.get(entityId);
    }

    /**
     * Spawns a totem at {@code loc}, wearing {@code headItem} as its visual identity, with
     * {@code maxHealth} HP that only player hits (melee or projectile) can reduce. If it's still
     * alive after {@code lifetimeTicks}, {@code onExpired} fires and it's removed; if a player breaks
     * it first, {@code onDestroyed} fires instead. Force-removed on fight end via the boss instance.
     */
    public static ArenaTotem spawn(WeaponsPlugin plugin, BossInstance instance, Location loc, Material headItem,
                                    Component label, double maxHealth, int lifetimeTicks,
                                    Consumer<ArenaTotem> onDestroyed, Consumer<ArenaTotem> onExpired) {
        return spawn(plugin, instance, loc, headItem, label, maxHealth, lifetimeTicks, onDestroyed, onExpired, true);
    }

    /**
     * As above, with control over ground snapping. Pass {@code snapToGround = false} to leave the
     * totem exactly where it is placed — the totem already has gravity disabled, so an airborne
     * placement simply hangs there. That is what makes a weak point reachable only by bows, throwables
     * and ranged abilities rather than by walking up and swinging at it.
     */
    public static ArenaTotem spawn(WeaponsPlugin plugin, BossInstance instance, Location loc, Material headItem,
                                    Component label, double maxHealth, int lifetimeTicks,
                                    Consumer<ArenaTotem> onDestroyed, Consumer<ArenaTotem> onExpired,
                                    boolean snapToGround) {
        Location placed = snapToGround ? groundSnap(loc) : loc.clone();
        World world = placed.getWorld();
        LivingEntity mob = (LivingEntity) world.spawnEntity(placed, EntityType.ZOMBIE);
        mob.setPersistent(true);
        mob.setSilent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCollidable(false);
        // Stays exactly where it's placed — it's a fixed weak point, not a mob. Without this a totem
        // spawned a hair above the ground surface would fall (or drift on slabs/stairs) off its intended
        // spot; pinned here, it always stands where the placement logic put it.
        mob.setGravity(false);
        mob.setCanPickupItems(false);
        if (mob instanceof Mob m) {
            m.setAI(false);
            m.setTarget(null);
        }
        var maxHealthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(Math.max(1.0, maxHealth));
        }
        mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getValue());
        var knockbackAttr = mob.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(1.0);
        }
        // Any helmet at all stops an undead totem igniting in daylight — a side effect, not the point.
        EntityEquipment equipment = mob.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(headItem));
            equipment.setHelmetDropChance(0f);
        }
        mob.getPersistentDataContainer().set(ArenaTotemDamageListener.marker(plugin), PersistentDataType.BYTE, (byte) 1);
        instance.trackEntity(mob);

        ArenaTotem totem = new ArenaTotem(plugin, mob, Math.max(1.0, maxHealth), label, onDestroyed, onExpired);
        ACTIVE.put(mob.getUniqueId(), totem);
        totem.updateName();
        totem.startLifetimeTimer(lifetimeTicks);
        return totem;
    }

    private void startLifetimeTimer(int lifetimeTicks) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (resolved || !entity.isValid()) {
                    cancel();
                    return;
                }
                if (ticks >= lifetimeTicks) {
                    resolve(false);
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Called only by {@link ArenaTotemDamageListener} once it's confirmed the hit came from a player. */
    void applyPlayerDamage(double amount) {
        if (resolved) {
            return;
        }
        health -= amount;
        Location loc = entity.getLocation().add(0, 1.2, 0);
        Fx.coloredBurst(loc, Color.fromRGB(230, 230, 230), 1.0f, 10, 0.3);
        Fx.sound(loc, Sound.ENTITY_ZOMBIE_HURT, 0.8f, 1.3f);
        if (health <= 0) {
            resolve(true);
            return;
        }
        updateName();
    }

    private void updateName() {
        int pips = 10;
        int filled = (int) Math.ceil(pips * Math.max(0.0, health) / maxHealth);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < pips; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        entity.customName(baseName.append(Component.text("  " + bar, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        entity.setCustomNameVisible(true);
    }

    private void resolve(boolean destroyed) {
        if (resolved) {
            return;
        }
        resolved = true;
        ACTIVE.remove(entity.getUniqueId());
        Location loc = entity.getLocation().add(0, 1, 0);
        if (destroyed) {
            Fx.coloredBurst(loc, Color.fromRGB(230, 230, 230), 1.6f, 30, 0.5);
            Fx.burst(loc, Particle.CLOUD, 20, 0.4);
            Fx.sound(loc, Sound.ENTITY_ZOMBIE_DEATH, 1.0f, 1.2f);
        }
        entity.remove();
        if (destroyed) {
            onDestroyed.accept(this);
        } else {
            onExpired.accept(this);
        }
    }

    /** Force-removes this weak point without firing {@code onDestroyed}/{@code onExpired} — a phase change or fight end retiring an unfinished set, not a real outcome. */
    public void discard() {
        if (resolved) {
            return;
        }
        resolved = true;
        ACTIVE.remove(entity.getUniqueId());
        if (entity.isValid()) {
            entity.remove();
        }
    }

    public Location location() {
        return entity.getLocation();
    }

    /** True while this totem is still standing and un-resolved — the "did it survive" check. */
    public boolean isValid() {
        return !resolved && entity.isValid();
    }
}
