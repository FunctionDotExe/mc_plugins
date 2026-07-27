package dev.rbm72.weaponsplugin.ridable;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks which saddle each player has equipped (max {@link #MAX_SLOTS}, i.e. exactly one),
 * persists that choice to disk, and owns the runtime state of who's currently riding what: a tamed
 * mount is tagged in its own {@link PersistentDataContainer} (owner + saddle id) so mount state
 * survives chunk unload/reload without a separate live-entity cache. Equipped-saddle state is stored
 * per player under {@code <dataFolder>/ridables/<uuid>.yml}; saddles are stateless so an id alone
 * reconstructs the display item on demand.
 */
public final class RidableManager {

    public static final int MAX_SLOTS = 1;

    private static final String OWNER_KEY = "ridable_owner";

    private final WeaponsPlugin plugin;
    private final RidableRegistry registry;
    private final File folder;
    private final Map<UUID, String> equipped = new HashMap<>();
    /** player id -> timestamp their mount's active ability next comes off cooldown. */
    private final Map<UUID, Long> abilityCooldowns = new HashMap<>();

    public RidableManager(WeaponsPlugin plugin, RidableRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.folder = new File(plugin.getDataFolder(), "ridables");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    /** The equipped saddle id for a player, loading from disk on first access. Null if none equipped. */
    public String equippedId(UUID uuid) {
        return equipped.computeIfAbsent(uuid, this::load);
    }

    public Optional<Ridable> equipped(Player player) {
        String id = equippedId(player.getUniqueId());
        return id == null ? Optional.empty() : registry.get(id);
    }

    /** True if the saddle could be equipped (the single slot is free). */
    public boolean equip(Player player, Ridable ridable) {
        if (equippedId(player.getUniqueId()) != null) {
            return false;
        }
        equipped.put(player.getUniqueId(), ridable.id());
        save(player.getUniqueId(), ridable.id());
        return true;
    }

    /** Clears the equipped saddle, if any, and returns it. */
    public Ridable unequip(Player player) {
        String id = equipped.remove(player.getUniqueId());
        save(player.getUniqueId(), null);
        return id == null ? null : registry.get(id).orElse(null);
    }

    /** Tames and mounts the given entity for the player, tagging it so future lookups recognize it as their mount. */
    public void mount(Player player, LivingEntity entity, Ridable ridable) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(ownerKey(), PersistentDataType.STRING, player.getUniqueId().toString());
        data.set(Ridable.idKey(plugin), PersistentDataType.STRING, ridable.id());
        if (entity instanceof org.bukkit.entity.Mob mob) {
            // Flyers (Ender Dragon especially) drive their wing-flap/flight animation off the same
            // internal controller AI ticks — killing AI there freezes them mid-pose. Target-null +
            // the permanent EntityTargetEvent cancel already make them harmless without it; our
            // per-tick teleport in RidableMovementTask dominates position regardless of AI state.
            // Ground mounts don't have that animation coupling, so AI stays off to stop them
            // wandering off under their own steam while idly mounted.
            if (!ridable.flies()) {
                mob.setAI(false);
            }
            mob.setTarget(null);
        }
        if (ridable.flies()) {
            entity.setGravity(false);
        }
        entity.addPassenger(player);
    }

    /** Untames whatever the player is riding, if it's a tracked mount, and clears its tags. */
    public void dismount(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity mount) {
            dismount(player, mount);
        }
    }

    /**
     * Stops actively steering the given entity for the player, if it's tracked as their mount.
     * The tame/passive tag is permanent once set (see {@link #mount}) — like a real horse, a mob
     * that's ever been tamed never goes hostile again, it just stops being actively ridden.
     */
    public void dismount(Player player, LivingEntity mount) {
        if (isTrackedMount(mount, player)) {
            releaseControl(mount);
        }
    }

    /** True if the given entity is currently tagged as the given player's mount. */
    public boolean isTrackedMount(LivingEntity entity, Player player) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String owner = data.get(ownerKey(), PersistentDataType.STRING);
        return owner != null && owner.equals(player.getUniqueId().toString());
    }

    /** True if the given entity has ever been tamed by anyone (permanent, even after dismounting). */
    public boolean isAnyonesMount(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(ownerKey(), PersistentDataType.STRING);
    }

    /** True if the given entity was tamed by someone other than the given player. */
    public boolean isTamedByOther(LivingEntity entity, Player player) {
        return isAnyonesMount(entity) && !isTrackedMount(entity, player);
    }

    /** The entity the player is currently riding, if it's a tracked mount. */
    public Optional<LivingEntity> mountedEntity(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity mount && isTrackedMount(mount, player)) {
            return Optional.of(mount);
        }
        return Optional.empty();
    }

    /** The saddle definition for whatever the player is currently riding, if it's a tracked mount. */
    public Optional<Ridable> mountedRidable(Player player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity mount) || !isTrackedMount(mount, player)) {
            return Optional.empty();
        }
        String id = mount.getPersistentDataContainer().get(Ridable.idKey(plugin), PersistentDataType.STRING);
        return id == null ? Optional.empty() : registry.get(id);
    }

    /** Fires the rider's mounted saddle ability if they're riding a tracked mount and it's off cooldown. */
    public boolean triggerAbility(Player player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity mount) || !isTrackedMount(mount, player)) {
            return false;
        }
        Optional<Ridable> ridable = mountedRidable(player);
        if (ridable.isEmpty() || isAbilityOnCooldown(player)) {
            return false;
        }
        startAbilityCooldown(player, ridable.get());
        ridable.get().ability(player, mount);
        return true;
    }

    /**
     * Restores normal idle AI (movement/animation) but deliberately leaves the owner/id PDC tags in
     * place — that's what keeps {@code onTarget} permanently cancelling aggro for this entity from
     * here on, even though it's no longer being actively steered.
     */
    private void releaseControl(LivingEntity mount) {
        if (mount instanceof org.bukkit.entity.Mob mob) {
            mob.setTarget(null);
            mob.setAI(true);
        }
        mount.setGravity(true);
    }

    /** Seconds left on the rider's mount-ability cooldown, or 0 if it's ready. Drives the action-bar display. */
    public double remainingAbilityCooldownSeconds(Player player) {
        Long readyAt = abilityCooldowns.get(player.getUniqueId());
        if (readyAt == null) {
            return 0;
        }
        return Math.max(0, (readyAt - System.currentTimeMillis()) / 1000.0);
    }

    private boolean isAbilityOnCooldown(Player player) {
        Long readyAt = abilityCooldowns.get(player.getUniqueId());
        return readyAt != null && System.currentTimeMillis() < readyAt;
    }

    private void startAbilityCooldown(Player player, Ridable ridable) {
        long durationMs = Math.round(ridable.abilityCooldownSeconds() * 1000);
        abilityCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
    }

    private NamespacedKey ownerKey() {
        return new NamespacedKey(plugin, OWNER_KEY);
    }

    /** Drops the cached entry so a re-login reloads fresh from disk. */
    public void unload(UUID uuid) {
        equipped.remove(uuid);
        abilityCooldowns.remove(uuid);
    }

    private String load(UUID uuid) {
        File file = new File(folder, uuid + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String id = config.getString("equipped");
            if (id != null && registry.get(id).isPresent()) {
                return id;
            }
        }
        return null;
    }

    private void save(UUID uuid, String id) {
        File file = new File(folder, uuid + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("equipped", id);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save ridables for " + uuid + ": " + e.getMessage());
        }
    }
}
