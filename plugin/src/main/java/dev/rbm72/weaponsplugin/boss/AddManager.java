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
    private final int copies;

    /**
     * @param copies how many of each requested add to actually spawn — 1 normally, more under the
     *        {@link dev.rbm72.weaponsplugin.boss.modifier.BossModifier#DOUBLE_ADDS} affix. Applied here,
     *        at the single choke point every add in the roster already goes through, so the affix needs
     *        no cooperation from the hundred-odd call sites that summon one.
     */
    public AddManager(int copies) {
        this.copies = Math.max(1, copies);
    }

    /**
     * Spawns the add (and any affix duplicates) and returns the <em>first</em> one, which is the one
     * callers hold on to for their own bookkeeping. Duplicates are tracked here and torn down with
     * everything else, but are deliberately anonymous: a mechanic that counts "its" add — a hostage,
     * a duel opponent, the one guard whose death opens the phase — must keep counting exactly one,
     * or the affix would turn its own objective unsatisfiable.
     */
    public LivingEntity spawn(World world, Location at, EntityType type, Consumer<LivingEntity> customize) {
        LivingEntity first = spawnOne(world, at, type, customize);
        for (int i = 1; i < copies; i++) {
            // Offset slightly so duplicates don't stack inside each other and get shoved apart by
            // vanilla entity collision the instant they appear.
            Location offset = at.clone().add((i % 2 == 0 ? 1 : -1) * 1.2, 0, (i < 2 ? 1 : -1) * 1.2);
            spawnOne(world, offset, type, customize);
        }
        return first;
    }

    private LivingEntity spawnOne(World world, Location at, EntityType type, Consumer<LivingEntity> customize) {
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
