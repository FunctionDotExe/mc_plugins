package dev.rbm72.weaponsplugin.items.kit;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Weapon;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.WindCharge;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Every real Minecraft object a weapon ability is allowed to put into the world, spawned already
 * defanged and already tagged.
 * <p>
 * Batch-1 §0.1 asks each ability to name the object that produces its experience rather than draw a
 * particle ring around a {@code damage()} call. The reason 57 weapons did not is not that the objects
 * are hard to spawn — it is that each one needs three or four easy-to-forget settings before it is safe
 * in the open world, and getting any of them wrong ships a griefing weapon:
 * <ul>
 *   <li>a {@link FallingBlock} that keeps {@code dropItem} places or drops itself on landing, so an
 *       ability becomes a block duplicator;</li>
 *   <li>a {@link TNTPrimed} craters the map unless something empties its block list;</li>
 *   <li>a {@link WindCharge} pops player-placed blocks (buttons, doors, candles) on impact;</li>
 *   <li>an {@link AreaEffectCloud} with no {@code source} credits its kills to nobody, so the weapon
 *       never gets a kill, PvP logging misses it, and it happily poisons its own caster.</li>
 * </ul>
 * So each factory here returns the object with those already handled, and stamps it with the weapon's
 * id plus a marker key. {@link dev.rbm72.weaponsplugin.listeners.WeaponPropListener} watches for that
 * marker and strips block damage from anything carrying it — which means a new explosive ability is
 * safe by construction rather than by its author remembering.
 * <p>
 * Nothing here writes a block. Terrain goes through {@link TempTerrain}, which holds the undo log.
 */
public final class Props {

    /** Marker every weapon-spawned prop carries, so one listener can recognise all of them. */
    private static final String PROP_MARKER_KEY = "weapon_prop";

    private Props() {
    }

    /** The marker key {@link dev.rbm72.weaponsplugin.listeners.WeaponPropListener} matches on. */
    public static NamespacedKey markerKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, PROP_MARKER_KEY);
    }

    /**
     * Stamps {@code entity} as this weapon's prop: the marker key plus the weapon id.
     * <p>
     * Public because a weapon spawning something this class has no factory for still has to be
     * recognisable to the prop listener — an untagged explosive is one that craters the world.
     */
    public static void tag(WeaponsPlugin plugin, Entity entity, Weapon weapon) {
        tag(plugin, entity, weapon.id());
    }

    /**
     * As {@link #tag(WeaponsPlugin, Entity, Weapon)} for a source that isn't a weapon.
     * <p>
     * Movement stones spawn the same objects weapons do — a wind charge to launch off, an ender pearl to
     * blink with — and need the same guarantee that the prop listener strips their block damage. The id is
     * free-form because the listener only ever matches on the marker; the id exists so a stray prop in the
     * world can be traced back to whatever spawned it.
     */
    public static void tag(WeaponsPlugin plugin, Entity entity, String sourceId) {
        entity.getPersistentDataContainer().set(markerKey(plugin), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, sourceId);
    }

    /** True if {@code entity} was spawned by a weapon ability through this class. */
    public static boolean isProp(WeaponsPlugin plugin, Entity entity) {
        return entity != null && entity.getPersistentDataContainer()
                .has(markerKey(plugin), PersistentDataType.BYTE);
    }

    /**
     * A real gravity-driven block, tagged and unable to place or drop itself.
     * <p>
     * {@code setHurtEntities(false)} is deliberate even for an anvil meant to crush someone: vanilla
     * falling-block damage scales with fall distance and ignores every number on the weapon, so the
     * ability applies its own damage on impact and the block supplies the physics and the read.
     */
    public static FallingBlock fallingBlock(WeaponsPlugin plugin, Weapon weapon, Location at, Material material) {
        World world = Objects.requireNonNull(at.getWorld(), "falling block needs a world");
        BlockData data = material.createBlockData();
        FallingBlock block = world.spawn(at, FallingBlock.class, fb -> fb.setBlockData(data));
        block.setDropItem(false);
        block.setCancelDrop(true);
        block.setHurtEntities(false);
        block.setPersistent(false);
        tag(plugin, block, weapon);
        return block;
    }

    /**
     * Real primed TNT with real vanilla knockback and real entity damage, and no block damage —
     * {@code WeaponPropListener} empties its block list when it goes off.
     *
     * @param velocity initial throw, or null to drop it where it stands
     */
    public static TNTPrimed tnt(WeaponsPlugin plugin, Weapon weapon, Player caster, Location at,
                                 Vector velocity, int fuseTicks) {
        World world = Objects.requireNonNull(at.getWorld(), "tnt needs a world");
        TNTPrimed tnt = world.spawn(at, TNTPrimed.class, spawned -> {
            spawned.setFuseTicks(Math.max(1, fuseTicks));
            spawned.setSource(caster);
            spawned.setPersistent(false);
        });
        if (velocity != null) {
            tnt.setVelocity(velocity);
        }
        tag(plugin, tnt, weapon);
        return tnt;
    }

    /**
     * A real wind charge: vanilla's own "shove everything away from a point" object, complete with its
     * burst shape, its sound and its interaction with player velocity.
     * <p>
     * This is the direct replacement for every wind-particle-plus-{@code setVelocity} ability in the
     * roster. The block list a wind charge pops (buttons, levers, candles) is stripped by the prop
     * listener like any other explosive's.
     */
    public static WindCharge windCharge(WeaponsPlugin plugin, Weapon weapon, Player caster,
                                         Location from, Vector velocity) {
        return windCharge(plugin, weapon.id(), caster, from, velocity);
    }

    /** As {@link #windCharge(WeaponsPlugin, Weapon, Player, Location, Vector)}, for a non-weapon source. */
    public static WindCharge windCharge(WeaponsPlugin plugin, String sourceId, Player caster,
                                         Location from, Vector velocity) {
        World world = Objects.requireNonNull(from.getWorld(), "wind charge needs a world");
        WindCharge charge = world.spawn(from, WindCharge.class, spawned -> {
            spawned.setShooter(caster);
            spawned.setPersistent(false);
        });
        if (velocity != null) {
            charge.setVelocity(velocity);
        }
        tag(plugin, charge, sourceId);
        return charge;
    }

    /**
     * A real ender pearl — vanilla's own teleport object, with its arc, its travel time and its
     * blockable-by-terrain flight.
     * <p>
     * The direct replacement for a {@code teleport()} call dressed in particles: a pearl can be thrown
     * short, overshoot a ledge, or clip a wall and drop you halfway, which is what makes throwing one a
     * skill rather than a button. The 5 fall damage vanilla charges the thrower is left to the caller to
     * waive or keep — a movement stone waives it (see {@code RiftstepStone}), because the stone's whole
     * promise is mobility and a self-damaging mobility tool just reads as broken.
     */
    public static EnderPearl enderPearl(WeaponsPlugin plugin, String sourceId, Player thrower, Vector velocity) {
        // launchProjectile rather than world.spawn: the pearl teleports its *owner*, and ownership is what
        // launching establishes. A spawned pearl with setShooter after the fact is a pearl that may land with
        // nobody to move, which is an ability that silently does nothing.
        EnderPearl pearl = thrower.launchProjectile(EnderPearl.class, velocity);
        pearl.setPersistent(false);
        tag(plugin, pearl, sourceId);
        return pearl;
    }

    /**
     * A real lightning bolt that does not set the world on fire.
     * <p>
     * {@code World#strikeLightningEffect} is the cosmetic one — a flash and a thunderclap with no bolt
     * behind it — and it is what every "call lightning" ability in the roster was actually using, with a
     * hand-written {@code damage()} loop supplying the hit. This spawns the real entity instead: real
     * damage, real mob conversion (pig to zombified piglin, villager to witch), real interaction with
     * lightning rods and channeling tridents.
     * <p>
     * The fire a bolt starts where it lands is the one thing a weapon must not keep, and the Bukkit interface
     * exposes no "causes fire" flag to turn off — so the marker is applied <em>inside the spawn consumer</em>,
     * before the entity is added to the world, and {@code WeaponPropListener} refuses the resulting
     * {@link org.bukkit.event.block.BlockIgniteEvent}. Tagging after the spawn call would be a race against the
     * bolt's own first tick, which is when it lights the block.
     */
    public static LightningStrike lightning(WeaponsPlugin plugin, Weapon weapon, Player caster, Location at) {
        return lightning(plugin, weapon.id(), caster, at);
    }

    /** As {@link #lightning(WeaponsPlugin, Weapon, Player, Location)}, for a non-weapon source. */
    public static LightningStrike lightning(WeaponsPlugin plugin, String sourceId, Player caster, Location at) {
        World world = Objects.requireNonNull(at.getWorld(), "lightning needs a world");
        return world.spawn(at, LightningStrike.class, spawned -> {
            spawned.setPersistent(false);
            if (caster != null) {
                spawned.setCausingPlayer(caster);
            }
            tag(plugin, spawned, sourceId);
        });
    }

    /**
     * A real fireball — vanilla's own "burning projectile" object, with its travel, its impact and its
     * setting-things-alight already written.
     * <p>
     * The direct replacement for the flame-particle cone: a cone drawn with {@code Particle.FLAME} and
     * backed by a {@code damage()} loop stops existing the moment the particle call is deleted, whereas
     * three of these do the same job as objects that fly, can be blocked by terrain, and light what they
     * touch. Incendiary is off and the yield is zeroed — the burning comes from the fire the ability lays
     * down through {@link TempTerrain}, which is temporary, rather than from spread that is not.
     */
    public static SmallFireball fireball(WeaponsPlugin plugin, Weapon weapon, Player caster,
                                          Location from, Vector velocity) {
        World world = Objects.requireNonNull(from.getWorld(), "fireball needs a world");
        SmallFireball ball = world.spawn(from, SmallFireball.class, spawned -> {
            spawned.setShooter(caster);
            spawned.setIsIncendiary(false);
            spawned.setYield(0f);
            spawned.setPersistent(false);
        });
        if (velocity != null) {
            ball.setVelocity(velocity);
        }
        tag(plugin, ball, weapon);
        return ball;
    }

    /**
     * A real lingering cloud that holds ground for its whole duration.
     * <p>
     * The caster is set as {@code source} so kills credit the weapon, and the cloud is given a
     * reapplication delay because vanilla otherwise re-applies its effects every tick — an unbounded
     * cloud is a stun-lock, and stacking the same effect 20 times a second is how a "poison patch"
     * turns into instant death.
     *
     * @param radius     cloud radius in blocks
     * @param durationTicks how long it holds the ground
     * @param effects    what standing in it does
     */
    public static AreaEffectCloud cloud(WeaponsPlugin plugin, Weapon weapon, Player caster, Location at,
                                        double radius, int durationTicks, Particle particle, Color color,
                                        PotionEffect... effects) {
        World world = Objects.requireNonNull(at.getWorld(), "cloud needs a world");
        AreaEffectCloud cloud = world.spawn(at, AreaEffectCloud.class, spawned -> {
            spawned.setRadius((float) Math.max(0.5, radius));
            spawned.setDuration(Math.max(20, durationTicks));
            spawned.setReapplicationDelay(20);
            spawned.setSource(caster);
            spawned.setPersistent(false);
            if (particle != null) {
                spawned.setParticle(particle);
            }
            if (color != null) {
                spawned.setColor(color);
            }
            for (PotionEffect effect : effects) {
                spawned.addCustomEffect(effect, true);
            }
        });
        tag(plugin, cloud, weapon);
        return cloud;
    }

    /**
     * A real end crystal — a destructible, targetable object with a beam and a very large explosion,
     * used as a placed thing players and mobs must deal with rather than as a visual.
     * <p>
     * Given a hard lifetime because a crystal nobody shoots is a permanent fixture, and the explosion
     * that <em>does</em> come from shooting one has its blocks stripped by the prop listener.
     */
    public static EnderCrystal crystal(WeaponsPlugin plugin, Weapon weapon, Location at, int lifetimeTicks) {
        World world = Objects.requireNonNull(at.getWorld(), "crystal needs a world");
        EnderCrystal crystal = world.spawn(at, EnderCrystal.class, spawned -> {
            spawned.setShowingBottom(false);
            spawned.setPersistent(false);
        });
        tag(plugin, crystal, weapon);
        despawnAfter(plugin, crystal, lifetimeTicks);
        return crystal;
    }

    /**
     * A real item on the ground, on loan.
     * <p>
     * This is the §0.3-rule-3 primitive — "the arena supplies the item-based counterplay" — applied to a
     * weapon: an ability that means to hand someone a chorus fruit, an ender pearl or a water bucket
     * drops a real one they have to walk over and use, instead of granting an effect out of nowhere.
     * <p>
     * <b>The loan is the whole design.</b> A pickup-able item from an ability on a cooldown is otherwise
     * an infinite resource generator — "free pearls every 8 seconds" is a far worse balance problem than
     * whatever the ability was trying to do. So the stack itself is stamped with the marker key and
     * reclaimed at expiry: use it inside its window or it is gone out of your inventory. That keeps the
     * item genuinely useful and genuinely scarce, which is what the rule asks for.
     */
    public static Item offering(WeaponsPlugin plugin, Weapon weapon, Location at, ItemStack stack, int lifetimeTicks) {
        World world = Objects.requireNonNull(at.getWorld(), "offering needs a world");

        ItemStack loaned = stack.clone();
        loaned.editMeta(meta -> {
            meta.getPersistentDataContainer().set(markerKey(plugin), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, weapon.id());
            meta.lore(java.util.List.of(net.kyori.adventure.text.Component
                    .text("On loan — vanishes shortly", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true)));
        });

        Item item = world.dropItem(at, loaned);
        item.setPersistent(false);
        item.setGlowing(true);
        item.setPickupDelay(10);
        tag(plugin, item, weapon);
        despawnAfter(plugin, item, lifetimeTicks);
        reclaimAfter(plugin, at, lifetimeTicks);
        return item;
    }

    /** True for an item stack this class loaned out — see {@link #offering}. */
    public static boolean isLoaned(WeaponsPlugin plugin, ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer()
                .has(markerKey(plugin), PersistentDataType.BYTE);
    }

    /**
     * Takes loaned stacks back out of every nearby player's inventory once the window closes.
     * <p>
     * Radius-scoped rather than server-wide because this runs per offering and a full online-player scan
     * per dropped item would not stay cheap; anyone who picked the item up and then ran 48 blocks away
     * inside its lifetime has earned it.
     */
    private static void reclaimAfter(WeaponsPlugin plugin, Location origin, int ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = origin.getWorld();
                if (world == null) {
                    return;
                }
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(origin) > 48 * 48) {
                        continue;
                    }
                    ItemStack[] contents = player.getInventory().getContents();
                    for (int slot = 0; slot < contents.length; slot++) {
                        if (isLoaned(plugin, contents[slot])) {
                            player.getInventory().setItem(slot, null);
                        }
                    }
                }
            }
        }.runTaskLater(plugin, Math.max(1, ticks));
    }

    /**
     * Removes {@code entity} after {@code ticks} if it is still around.
     * <p>
     * Every prop gets one. A weapon fires hundreds of times an hour, and any object without a hard
     * lifetime is an entity leak with a chunk-load cost attached.
     */
    public static void despawnAfter(WeaponsPlugin plugin, Entity entity, int ticks) {
        if (ticks <= 0) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isValid()) {
                    entity.remove();
                }
            }
        }.runTaskLater(plugin, ticks);
    }
}
