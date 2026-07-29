package dev.rbm72.weaponsplugin.accessory;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.items.Weapon;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which accessories each player has equipped (max {@link #MAX_SLOTS}),
 * persists them to disk, and answers the aggregate-buff questions the rest of
 * the plugin asks: how much to scale a weapon's damage / cooldown, and which
 * on-hit hooks to fire. Equipped state is stored as a list of accessory ids
 * per player under {@code <dataFolder>/accessories/<uuid>.yml}; accessories are
 * stateless so ids alone reconstruct the display items on demand.
 */
public final class AccessoryManager {

    public static final int MAX_SLOTS = 5;
    /** Cooldowns can never be reduced past this fraction of their base, no matter how many accessories stack. */
    private static final double MIN_COOLDOWN_MULTIPLIER = 0.25;

    private final WeaponsPlugin plugin;
    private final AccessoryRegistry registry;
    private final File folder;
    private final Map<UUID, List<String>> equipped = new HashMap<>();
    /** accessory id -> timestamp its personal ability next comes off cooldown, per player. */
    private final Map<UUID, Map<String, Long>> personalAbilityCooldowns = new HashMap<>();

    public AccessoryManager(WeaponsPlugin plugin, AccessoryRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.folder = new File(plugin.getDataFolder(), "accessories");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    /** Equipped accessory ids for a player, loading from disk on first access. Never null. */
    public List<String> equippedIds(UUID uuid) {
        return equipped.computeIfAbsent(uuid, this::load);
    }

    public List<Accessory> equipped(Player player) {
        List<Accessory> result = new ArrayList<>();
        for (String id : equippedIds(player.getUniqueId())) {
            registry.get(id).ifPresent(result::add);
        }
        return result;
    }

    /**
     * True if anything the player has equipped negates knockback. The one-question form of
     * {@link #equipped}, for the callers that only need the answer — the knockback/flinch listener, and
     * the handful of boss effects that shove a player without going through a damage event at all
     * (those bypass {@code EntityKnockbackEvent}, so nothing else would ever ask).
     */
    public boolean negatesKnockback(Player player) {
        for (Accessory accessory : equipped(player)) {
            if (accessory.negatesKnockback()) {
                return true;
            }
        }
        return false;
    }

    /** True if anything the player has equipped strips the hurt tilt off recurring damage. */
    public boolean negatesTickFlinch(Player player) {
        for (Accessory accessory : equipped(player)) {
            if (accessory.negatesTickFlinch()) {
                return true;
            }
        }
        return false;
    }

    /** True if the accessory could be added (there is a free slot and it isn't already equipped). */
    public boolean equip(Player player, Accessory accessory) {
        List<String> ids = equippedIds(player.getUniqueId());
        if (ids.size() >= MAX_SLOTS || ids.contains(accessory.id())) {
            return false;
        }
        ids.add(accessory.id());
        save(player.getUniqueId(), ids);
        return true;
    }

    /** Removes the accessory in the given slot index, if any, and returns it. */
    public Accessory unequip(Player player, int slotIndex) {
        List<String> ids = equippedIds(player.getUniqueId());
        if (slotIndex < 0 || slotIndex >= ids.size()) {
            return null;
        }
        String id = ids.remove(slotIndex);
        save(player.getUniqueId(), ids);
        return registry.get(id).orElse(null);
    }

    public double damageMultiplier(Player player, Weapon weapon) {
        double multiplier = 1.0;
        for (Accessory accessory : equipped(player)) {
            multiplier *= accessory.damageMultiplier(weapon);
        }
        return multiplier;
    }

    public double cooldownMultiplier(Player player, Weapon weapon) {
        double multiplier = 1.0;
        for (Accessory accessory : equipped(player)) {
            multiplier *= accessory.cooldownMultiplier(weapon);
        }
        return Math.max(MIN_COOLDOWN_MULTIPLIER, multiplier);
    }

    public void tick(Player player) {
        for (Accessory accessory : equipped(player)) {
            accessory.onEquipTick(player);
        }
    }

    public void onWeaponHit(Player player, Weapon weapon, LivingEntity victim, EntityDamageByEntityEvent event) {
        for (Accessory accessory : equipped(player)) {
            accessory.onWeaponHit(player, weapon, victim, event);
        }
    }

    public void onAbilityCast(Player player, Weapon weapon, Slot slot) {
        for (Accessory accessory : equipped(player)) {
            accessory.onAbilityCast(player, weapon, slot);
        }
    }

    /** True if any currently-equipped accessory grants a personal ability (regardless of cooldown state). */
    public boolean hasEquippedPersonalAbility(Player player) {
        for (Accessory accessory : equipped(player)) {
            if (accessory.hasPersonalAbility()) {
                return true;
            }
        }
        return false;
    }

    /** Fires the personal ability of every equipped, off-cooldown accessory that has one. Returns whether any fired. */
    public boolean triggerPersonalAbilities(Player player) {
        boolean triggeredAny = false;
        for (Accessory accessory : equipped(player)) {
            if (!accessory.hasPersonalAbility() || isPersonalAbilityOnCooldown(player, accessory)) {
                continue;
            }
            startPersonalAbilityCooldown(player, accessory);
            accessory.personalAbility(player);
            triggeredAny = true;
        }
        return triggeredAny;
    }

    private boolean isPersonalAbilityOnCooldown(Player player, Accessory accessory) {
        Map<String, Long> cooldowns = personalAbilityCooldowns.get(player.getUniqueId());
        Long readyAt = cooldowns == null ? null : cooldowns.get(accessory.id());
        return readyAt != null && System.currentTimeMillis() < readyAt;
    }

    private void startPersonalAbilityCooldown(Player player, Accessory accessory) {
        long durationMs = Math.round(accessory.personalAbilityCooldownSeconds() * 1000);
        personalAbilityCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(accessory.id(), System.currentTimeMillis() + durationMs);
    }

    /** Drops the cached entry so a re-login reloads fresh from disk. */
    public void unload(UUID uuid) {
        equipped.remove(uuid);
    }

    private List<String> load(UUID uuid) {
        File file = new File(folder, uuid + ".yml");
        List<String> ids = new ArrayList<>();
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String id : config.getStringList("equipped")) {
                if (registry.get(id).isPresent() && !ids.contains(id) && ids.size() < MAX_SLOTS) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private void save(UUID uuid, List<String> ids) {
        File file = new File(folder, uuid + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("equipped", ids);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save accessories for " + uuid + ": " + e.getMessage());
        }
    }
}
