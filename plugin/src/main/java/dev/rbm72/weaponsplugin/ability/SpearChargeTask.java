package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.SpearWeapon;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tells a player holding a spear charge why it is doing nothing yet.
 * <p>
 * The spear's charge attack is gated on speed by vanilla — {@code KineticWeapon.damageConditions} is
 * {@code ofRelativeSpeed(175, 4.6)} — so a pike right-clicked standing still produces the stance, no hit, no
 * damage event, and therefore no ability1. That is correct behaviour and a completely silent one: the weapon
 * is indistinguishable from a broken one until the player happens to sprint at something. Since ability1 is
 * hit-gated by design (a miss costs nothing), the cue is what makes the design legible instead of cruel.
 * <p>
 * Three states, all read off vanilla's own numbers via {@link SpearWeapon#chargeArmTicks()} and
 * {@link SpearWeapon#chargeMinSpeed()}: winding up, too slow, armed. Nothing here writes velocity or damage —
 * it only reports.
 * <p>
 * Speed comes from the player's own position delta rather than {@code getVelocity()}, which for a
 * player-controlled entity is the server's last-known guess and reads zero through most of a sprint. The
 * previous location lives in a {@link WeakHashMap} keyed by the {@link Player} so a quit drops the entry with
 * the entity, and one full stop clears it anyway.
 */
public final class SpearChargeTask extends BukkitRunnable {

    /** Sweep cadence. Fast enough that the cue tracks a sprint, slow enough to stay off the per-tick budget. */
    private static final long PERIOD_TICKS = 2L;

    /** How long each notice owns the action bar — just over one period, so it reads as continuous, not blinking. */
    private static final long NOTICE_MS = 400;

    private final WeaponsPlugin plugin;
    private final WeaponRegistry registry;
    private final Map<Player, Location> lastSeen = new WeakHashMap<>();

    public SpearChargeTask(WeaponsPlugin plugin, WeaponRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void start(Plugin owner) {
        runTaskTimer(owner, PERIOD_TICKS, PERIOD_TICKS);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            Weapon weapon = registry.identify(player.getInventory().getItemInMainHand()).orElse(null);
            if (!(weapon instanceof SpearWeapon spear)
                    || !player.hasActiveItem() || player.getActiveItemHand() != EquipmentSlot.HAND) {
                lastSeen.remove(player);
                continue;
            }

            Location previous = lastSeen.put(player, player.getLocation());
            if (previous == null || !previous.getWorld().equals(player.getWorld())) {
                continue;
            }

            // Horizontal only: falling is not closing speed, and vanilla's charge does not count it either.
            double perTick = Math.hypot(player.getLocation().getX() - previous.getX(),
                    player.getLocation().getZ() - previous.getZ()) / PERIOD_TICKS;
            double blocksPerSecond = perTick * 20.0;

            plugin.actionBarHub().flash(player, notice(spear, player, blocksPerSecond),
                    NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private Component notice(SpearWeapon spear, Player player, double blocksPerSecond) {
        if (player.getActiveItemUsedTime() < spear.chargeArmTicks()) {
            return Component.text("Levelling the " + spear.displayNameText() + "…", NamedTextColor.GRAY);
        }
        if (blocksPerSecond < spear.chargeMinSpeed()) {
            return Component.text("Too slow — sprint into them", NamedTextColor.RED);
        }
        return Component.text("Charge set — " + spear.ability1Name(), NamedTextColor.GREEN);
    }
}
