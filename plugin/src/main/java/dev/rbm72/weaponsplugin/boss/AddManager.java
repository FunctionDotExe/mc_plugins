package dev.rbm72.weaponsplugin.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tracks every add a boss instance summons so they can all be force-removed
 * on phase change or fight end — a boss must never leave a stray mob behind.
 */
public final class AddManager {

    private final Set<java.util.UUID> tracked = new HashSet<>();

    public LivingEntity spawn(World world, Location at, EntityType type, Consumer<LivingEntity> customize) {
        LivingEntity entity = (LivingEntity) world.spawnEntity(at, type);
        customize.accept(entity);
        tracked.add(entity.getUniqueId());
        return entity;
    }

    /** True if {@code id} is one of this instance's own summoned adds — used to exclude them from the boss's own AoE damage. */
    public boolean isTracked(java.util.UUID id) {
        return tracked.contains(id);
    }

    public int aliveCount() {
        int count = 0;
        for (java.util.UUID id : tracked) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                count++;
            }
        }
        return count;
    }

    public void despawnAll() {
        for (java.util.UUID id : tracked) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        tracked.clear();
    }
}
