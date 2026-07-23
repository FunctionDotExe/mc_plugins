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
        World world = loc.getWorld();
        LivingEntity mob = (LivingEntity) world.spawnEntity(loc, EntityType.ZOMBIE);
        mob.setPersistent(true);
        mob.setSilent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCollidable(false);
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
