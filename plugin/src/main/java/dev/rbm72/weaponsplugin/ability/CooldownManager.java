package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
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
 * <p>Exactly one repeating task runs per player (not per cooldown), advancing
 * every one of that player's cooldowns together: boss bars, the durability
 * meter, the ready chime, and retiring finished entries.
 *
 * <p>It does not write to the action bar. It implements
 * {@link ActionBarHub.Source} and hands its timers to the hub instead, which
 * merges them with whatever the player's stones and mount want to show. Before
 * that, every one of those systems ran its own timer writing the same single
 * line, and having a weapon cooldown and a movement ability running at once
 * made the bar strobe between them.
 */
public final class CooldownManager implements ActionBarHub.Source {

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

        if (durationMs >= BOSS_BAR_THRESHOLD_MS && !inBossFight(player)) {
            a.bossBar = BossBar.bossBar(weapon.displayName().append(Component.text(slot.suffix)),
                    1.0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
            player.showBossBar(a.bossBar);
        }

        if (pc.task == null) {
            pc.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player, pc), 0L, TICK_INTERVAL);
        }
    }

    /**
     * True while this player is inside a live boss fight, which already owns two boss-bar slots (the
     * boss's health and the current mechanic's readout).
     * <p>
     * A long cooldown normally earns its own boss bar because a 40-75 second timer is genuinely hard
     * to track on a shared action-bar line. In a boss fight that trade inverts: Minecraft draws each
     * boss bar's name directly above it at a fixed stride, so a fourth or fifth bar starts writing its
     * name over the bar above — and the thing being obscured is the mechanic readout the player has
     * to act on right now. The timer is not lost, only demoted: {@link #segments} still publishes
     * every cooldown to the action bar, which is where it is read from during a fight anyway.
     * <p>
     * Fails open. This is cosmetic prioritisation, and a missing or partially-initialised boss
     * manager during startup or shutdown must never stop a cooldown from being registered.
     */
    private boolean inBossFight(Player player) {
        try {
            if (plugin instanceof dev.rbm72.weaponsplugin.WeaponsPlugin wp && wp.bossManager() != null) {
                return wp.bossManager().isInFight(player);
            }
        } catch (Exception ignored) {
            // Cosmetic only — never let this decide whether a cooldown starts.
        }
        return false;
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

    /**
     * Advances this player's cooldowns: boss bar progress, the durability meter, firing the ready
     * chime as each one lands, and dropping entries once their READY display has been up long
     * enough. Renders nothing — {@link #segments} handles what the player actually sees.
     */
    private void tick(Player player, PlayerCooldowns pc) {
        if (!player.isOnline()) {
            cleanup(player, pc);
            return;
        }

        long now = System.currentTimeMillis();
        // Resolved once per tick rather than per entry: a player with three cooldowns running should
        // not cost three arena scans.
        boolean inFight = inBossFight(player);

        for (Active a : new ArrayList<>(pc.entries.values())) {
            // Walked into a fight mid-cooldown: give the bar slot back to the boss. One-way on
            // purpose — restoring it on the way out would make the HUD flicker at the arena edge,
            // and the action-bar timer covers it either way.
            if (inFight && a.bossBar != null) {
                player.hideBossBar(a.bossBar);
                a.bossBar = null;
            }

            if (a.readyAtMs < 0) {
                long elapsed = now - a.startMs;
                double fraction = Math.min(1.0, elapsed / (double) a.durationMs);

                if (a.bossBar != null) {
                    a.bossBar.progress((float) (1.0 - fraction));
                }
                if (a.slot == Slot.ABILITY1) {
                    updateDurability(player, a.weapon, 1.0 - fraction);
                }

                if (elapsed < a.durationMs) {
                    continue;
                }
                onReady(player, a);
            }

            if (now - a.readyAtMs >= READY_DISPLAY_MS) {
                pc.entries.remove(key(a.weapon.id(), a.slot));
            }
        }

        if (pc.entries.isEmpty()) {
            pc.task.cancel();
            active.remove(player.getUniqueId());
        }
    }

    /**
     * This player's cooldown timers for the merged action bar, soonest-to-finish first with anything
     * already READY pinned in front. Read-only: {@link #tick} owns every state change, so the hub
     * polling this can never advance a timer or double-fire a ready chime.
     */
    @Override
    public List<Component> segments(Player player) {
        PlayerCooldowns pc = active.get(player.getUniqueId());
        if (pc == null || pc.entries.isEmpty()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        List<Active> ordered = new ArrayList<>(pc.entries.values());
        ordered.sort(Comparator.comparingLong(a -> a.readyAtMs >= 0 ? Long.MIN_VALUE : a.durationMs - (now - a.startMs)));

        List<Component> parts = new ArrayList<>(ordered.size());
        for (Active a : ordered) {
            if (a.readyAtMs >= 0) {
                parts.add(a.weapon.displayName().append(Component.text(a.slot.suffix + " READY", NamedTextColor.GREEN)));
                continue;
            }
            double remaining = Math.max(0, (a.durationMs - (now - a.startMs)) / 1000.0);
            parts.add(a.weapon.displayName().append(Component.text(
                    a.slot.suffix + String.format(Locale.ROOT, " %.1fs", remaining), NamedTextColor.YELLOW)));
        }
        return parts;
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
