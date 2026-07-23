package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every active cooldown across every player, weapon, and ability slot.
 * A weapon never touches an action bar, boss bar, or durability meter
 * itself — it just calls {@link #start} and this class handles the rest.
 *
 * <p>Exactly one repeating task runs per player (not per cooldown), which
 * merges every cooldown that player has active into a single action bar
 * line each tick. Separate per-cooldown tasks would race to overwrite the
 * same action bar, causing the display to flicker between abilities.
 */
public final class CooldownManager {

    /** Cooldowns at or above this length also get a boss bar, not just the action bar. */
    private static final long BOSS_BAR_THRESHOLD_MS = 8000;
    private static final long TICK_INTERVAL = 2L;
    /** How long a finished cooldown keeps showing "READY" in the merged action bar before it's dropped. */
    private static final long READY_DISPLAY_MS = 1500;

    public enum Slot {
        ABILITY1(""),
        ABILITY2(" (2)"),
        ABILITY3(" (3)"),
        ULTIMATE(" (Ultimate)");

        final String suffix;

        Slot(String suffix) {
            this.suffix = suffix;
        }
    }

    private final Plugin plugin;
    private final Map<UUID, PlayerCooldowns> active = new HashMap<>();

    public CooldownManager(Plugin plugin) {
        this.plugin = plugin;
    }

    private static final class PlayerCooldowns {
        final Map<String, Active> entries = new HashMap<>();
        BukkitTask task;
    }

    private static final class Active {
        long startMs;
        final long durationMs;
        final Slot slot;
        final Weapon weapon;
        BossBar bossBar;
        /** -1 while counting down; set to the completion time once the cooldown finishes. */
        long readyAtMs = -1;

        Active(long startMs, long durationMs, Slot slot, Weapon weapon) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.slot = slot;
            this.weapon = weapon;
        }
    }

    private static String key(String weaponId, Slot slot) {
        return weaponId + ":" + slot.name();
    }

    public boolean isOnCooldown(Player player, String weaponId, Slot slot) {
        PlayerCooldowns pc = active.get(player.getUniqueId());
        return pc != null && pc.entries.containsKey(key(weaponId, slot));
    }

    public void start(Player player, Weapon weapon, Slot slot, double durationSeconds) {
        UUID uuid = player.getUniqueId();
        long durationMs = Math.round(durationSeconds * 1000);
        Active a = new Active(System.currentTimeMillis(), durationMs, slot, weapon);

        PlayerCooldowns pc = active.computeIfAbsent(uuid, k -> new PlayerCooldowns());
        pc.entries.put(key(weapon.id(), slot), a);

        if (durationMs >= BOSS_BAR_THRESHOLD_MS) {
            a.bossBar = BossBar.bossBar(weapon.displayName().append(Component.text(slot.suffix)),
                    1.0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
            player.showBossBar(a.bossBar);
        }

        if (pc.task == null) {
            pc.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player, pc), 0L, TICK_INTERVAL);
        }
    }

    /** Shifts an already-running cooldown's start time earlier, finishing it sooner. No-op if not active. */
    public void reduce(Player player, String weaponId, Slot slot, double seconds) {
        PlayerCooldowns pc = active.get(player.getUniqueId());
        if (pc == null) {
            return;
        }
        Active a = pc.entries.get(key(weaponId, slot));
        if (a != null) {
            a.startMs -= Math.round(seconds * 1000);
        }
    }

    private void tick(Player player, PlayerCooldowns pc) {
        if (!player.isOnline()) {
            cleanup(player, pc);
            return;
        }

        long now = System.currentTimeMillis();
        List<Active> ordered = new ArrayList<>(pc.entries.values());
        ordered.sort(Comparator.comparingLong(a -> a.readyAtMs >= 0 ? Long.MIN_VALUE : a.durationMs - (now - a.startMs)));

        List<Component> parts = new ArrayList<>();
        for (Active a : ordered) {
            if (a.readyAtMs < 0) {
                long elapsed = now - a.startMs;
                double fraction = Math.min(1.0, elapsed / (double) a.durationMs);
                double remaining = Math.max(0, (a.durationMs - elapsed) / 1000.0);

                if (a.bossBar != null) {
                    a.bossBar.progress((float) (1.0 - fraction));
                }
                if (a.slot == Slot.ABILITY1) {
                    updateDurability(player, a.weapon, 1.0 - fraction);
                }

                if (elapsed >= a.durationMs) {
                    onReady(player, a);
                } else {
                    parts.add(a.weapon.displayName().append(Component.text(
                            a.slot.suffix + String.format(Locale.ROOT, " %.1fs", remaining), NamedTextColor.YELLOW)));
                    continue;
                }
            }

            if (now - a.readyAtMs >= READY_DISPLAY_MS) {
                pc.entries.remove(key(a.weapon.id(), a.slot));
            } else {
                parts.add(a.weapon.displayName().append(Component.text(a.slot.suffix + " READY", NamedTextColor.GREEN)));
            }
        }

        if (!parts.isEmpty()) {
            Component combined = parts.get(0);
            for (int i = 1; i < parts.size(); i++) {
                combined = combined.append(Component.text("  |  ", NamedTextColor.GRAY)).append(parts.get(i));
            }
            player.sendActionBar(combined);
        }

        if (pc.entries.isEmpty()) {
            pc.task.cancel();
            active.remove(player.getUniqueId());
        }
    }

    private void onReady(Player player, Active a) {
        a.readyAtMs = System.currentTimeMillis();
        if (a.bossBar != null) {
            player.hideBossBar(a.bossBar);
        }
        if (a.slot == Slot.ABILITY1) {
            updateDurability(player, a.weapon, 0.0);
        }
        Fx.sound(player, a.weapon.readySound(), 0.7f, 1.6f);
        Fx.burst(player.getLocation().add(0, 1, 0), a.weapon.rarity().particle(), 12, 0.4);
    }

    private void updateDurability(Player player, Weapon weapon, double remainingFraction) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!weapon.matches(item)) {
            return;
        }
        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        damageable.setDamage((int) Math.round(maxDurability * remainingFraction));
        item.setItemMeta(meta);
    }

    private void cleanup(Player player, PlayerCooldowns pc) {
        pc.task.cancel();
        for (Active a : pc.entries.values()) {
            if (a.bossBar != null) {
                player.hideBossBar(a.bossBar);
            }
        }
        active.remove(player.getUniqueId());
    }
}
