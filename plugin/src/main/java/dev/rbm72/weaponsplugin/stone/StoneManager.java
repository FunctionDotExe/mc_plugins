package dev.rbm72.weaponsplugin.stone;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which movement stones each player has socketed (max {@link #MAX_SLOTS}), persists them
 * to disk, and drives their passive/active hooks. Mirrors {@link dev.rbm72.weaponsplugin.accessory.AccessoryManager}
 * but kept separate: stones socket into their own dedicated slots (see {@code StoneMenu}) rather
 * than competing with the general accessory slots, and carry no weapon-buff hooks. Equipped state
 * is stored as a list of stone ids per player under {@code <dataFolder>/stones/<uuid>.yml}; stones
 * are stateless so ids alone reconstruct the display items on demand.
 */
public final class StoneManager {

    public static final int MAX_SLOTS = 3;

    private final WeaponsPlugin plugin;
    private final StoneRegistry registry;
    private final File folder;
    private final Map<UUID, List<String>> equipped = new HashMap<>();
    /** stone id -> timestamp its personal ability next comes off cooldown, per player. */
    private final Map<UUID, Map<String, Long>> personalAbilityCooldowns = new HashMap<>();

    public StoneManager(WeaponsPlugin plugin, StoneRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.folder = new File(plugin.getDataFolder(), "stones");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    /** Equipped stone ids for a player, loading from disk on first access. Never null. */
    public List<String> equippedIds(UUID uuid) {
        return equipped.computeIfAbsent(uuid, this::load);
    }

    public List<Stone> equipped(Player player) {
        List<Stone> result = new ArrayList<>();
        for (String id : equippedIds(player.getUniqueId())) {
            registry.get(id).ifPresent(result::add);
        }
        return result;
    }

    public boolean hasEquipped(Player player, Class<? extends Stone> type) {
        for (Stone stone : equipped(player)) {
            if (type.isInstance(stone)) {
                return true;
            }
        }
        return false;
    }

    /** True if the stone could be added (there is a free slot and it isn't already equipped). */
    public boolean equip(Player player, Stone stone) {
        List<String> ids = equippedIds(player.getUniqueId());
        if (ids.size() >= MAX_SLOTS || ids.contains(stone.id())) {
            return false;
        }
        ids.add(stone.id());
        save(player.getUniqueId(), ids);
        return true;
    }

    /** Removes the stone in the given slot index, if any, and returns it. */
    public Stone unequip(Player player, int slotIndex) {
        List<String> ids = equippedIds(player.getUniqueId());
        if (slotIndex < 0 || slotIndex >= ids.size()) {
            return null;
        }
        String id = ids.remove(slotIndex);
        save(player.getUniqueId(), ids);
        return registry.get(id).orElse(null);
    }

    public void tick(Player player) {
        for (Stone stone : equipped(player)) {
            stone.onEquipTick(player);
        }
    }

    /** True if any currently-equipped stone grants a personal ability (regardless of cooldown state). */
    public boolean hasEquippedPersonalAbility(Player player) {
        for (Stone stone : equipped(player)) {
            if (stone.hasPersonalAbility()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fires the personal ability of every equipped, off-cooldown stone that has one. Returns
     * whether any fired. {@code bypass} (from {@code OpCooldownCommand}) skips the cooldown gate
     * and leaves the cooldown clock untouched, matching how weapon abilities bypass cooldowns.
     */
    public boolean triggerPersonalAbilities(Player player, boolean bypass) {
        boolean triggeredAny = false;
        for (Stone stone : equipped(player)) {
            if (!stone.hasPersonalAbility() || (!bypass && isPersonalAbilityOnCooldown(player, stone))) {
                continue;
            }
            if (!bypass) {
                startPersonalAbilityCooldown(player, stone);
            }
            stone.personalAbility(player);
            triggeredAny = true;
        }
        return triggeredAny;
    }

    /**
     * Seconds left on a stone's shared personal-ability cooldown, or 0 if it's ready or
     * {@code bypass} (from {@code OpCooldownCommand}) is active. Drives the action-bar display.
     */
    public double remainingCooldownSeconds(Player player, Stone stone, boolean bypass) {
        if (bypass) {
            return 0;
        }
        Map<String, Long> cooldowns = personalAbilityCooldowns.get(player.getUniqueId());
        Long readyAt = cooldowns == null ? null : cooldowns.get(stone.id());
        if (readyAt == null) {
            return 0;
        }
        return Math.max(0, (readyAt - System.currentTimeMillis()) / 1000.0);
    }

    private boolean isPersonalAbilityOnCooldown(Player player, Stone stone) {
        Map<String, Long> cooldowns = personalAbilityCooldowns.get(player.getUniqueId());
        Long readyAt = cooldowns == null ? null : cooldowns.get(stone.id());
        return readyAt != null && System.currentTimeMillis() < readyAt;
    }

    private void startPersonalAbilityCooldown(Player player, Stone stone) {
        long durationMs = Math.round(stone.personalAbilityCooldownSeconds() * 1000);
        personalAbilityCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(stone.id(), System.currentTimeMillis() + durationMs);
    }

    /** Drops the cached entry so a re-login reloads fresh from disk. */
    public void unload(UUID uuid) {
        equipped.remove(uuid);
        personalAbilityCooldowns.remove(uuid);
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
            plugin.getLogger().warning("Failed to save stones for " + uuid + ": " + e.getMessage());
        }
    }
}
