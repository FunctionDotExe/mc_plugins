package dev.rbm72.weaponsplugin.opitem;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single owner of every player's bonus-heart tally: how many extra hearts they hold, what that does to
 * their health bar, and where it is written down.
 * <p>
 * <b>Why a tally and a modifier rather than {@code setMaxHealth}.</b> Writing the max-health attribute's
 * <em>base</em> value is the obvious implementation and the wrong one: base value is a single number with no
 * record of who set it or why, so a heart vessel, a future armour set and a boss debuff all writing it would
 * overwrite each other, and there would be no way to remove one contribution without guessing at the others.
 * An {@link AttributeModifier} under this plugin's own {@link NamespacedKey} is additive, removable by name and
 * inspectable, so {@code /hearts remove} takes back exactly what the vessels granted and nothing else.
 * <p>
 * <b>The tally is the source of truth, not the attribute.</b> Attribute modifiers applied at runtime are saved
 * into player data by the server, which means a naive "add a modifier per heart" would double up on every
 * rejoin — the modifier from last session is already there. So {@link #apply} strips every modifier carrying
 * this plugin's key before adding one sized from the stored tally, making it idempotent: run it on join, on
 * respawn, and after every change, and the health bar always matches the number on disk.
 */
public final class HeartManager {

    private static final String MODIFIER_KEY = "bonus_hearts";
    /** Health points per heart. Vanilla's own conversion; a heart is two points, and half-hearts are not offered. */
    private static final double HEALTH_PER_HEART = 2.0;

    private final WeaponsPlugin plugin;
    private final File folder;
    private final Map<UUID, Integer> hearts = new HashMap<>();

    public HeartManager(WeaponsPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "hearts");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    /** Ceiling on the tally, so a stack of vessels can't produce a health bar that doesn't fit the screen. */
    public int maxHearts() {
        return Math.max(0, plugin.getConfig().getInt("hearts.max-hearts", 20));
    }

    /** Bonus hearts this player holds, loading from disk on first access. */
    public int hearts(UUID uuid) {
        return hearts.computeIfAbsent(uuid, this::load);
    }

    /**
     * Adds (or, with a negative delta, removes) bonus hearts and re-applies the health bar.
     *
     * @return the tally actually reached, which may differ from the request if it hit 0 or {@link #maxHearts()}.
     */
    public int add(Player player, int delta) {
        return set(player, hearts(player.getUniqueId()) + delta);
    }

    /** Sets the tally outright, clamped to {@code [0, maxHearts()]}. Returns the value stored. */
    public int set(Player player, int value) {
        int clamped = Math.max(0, Math.min(maxHearts(), value));
        hearts.put(player.getUniqueId(), clamped);
        save(player.getUniqueId(), clamped);
        apply(player);
        return clamped;
    }

    /**
     * Rewrites the player's max-health modifier to match their stored tally.
     * <p>
     * Idempotent by construction — every modifier under this plugin's key is removed first — so this is safe to
     * call from a join handler, a respawn handler and a command without any of them needing to know what the
     * others did.
     */
    public void apply(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, MODIFIER_KEY);
        // Copied before iterating: removing from the live collection while walking it is a concurrent
        // modification, and the previous session's saved modifier is very often in here.
        for (AttributeModifier existing : new ArrayList<>(attribute.getModifiers())) {
            if (existing.getKey().equals(key)) {
                attribute.removeModifier(existing);
            }
        }

        int count = hearts(player.getUniqueId());
        if (count > 0) {
            attribute.addModifier(new AttributeModifier(key, count * HEALTH_PER_HEART,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }

        // Losing hearts must not leave the player above their own maximum — the client draws that as a health
        // bar that never depletes, and the server never corrects it on its own.
        double max = attribute.getValue();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    /** Drops the cached tally so a re-login reloads fresh from disk. */
    public void unload(UUID uuid) {
        hearts.remove(uuid);
    }

    /** Every online player's bar re-synced — used on {@code /bossreload}-style config changes and on enable. */
    public void applyAll(List<? extends Player> players) {
        players.forEach(this::apply);
    }

    private int load(UUID uuid) {
        File file = new File(folder, uuid + ".yml");
        if (!file.exists()) {
            return 0;
        }
        return Math.max(0, Math.min(maxHearts(), YamlConfiguration.loadConfiguration(file).getInt("hearts", 0)));
    }

    private void save(UUID uuid, int count) {
        File file = new File(folder, uuid + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("hearts", count);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save hearts for " + uuid + ": " + e.getMessage());
        }
    }
}
