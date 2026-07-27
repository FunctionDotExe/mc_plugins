package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Beacon: the boss hurls a marker to a random spot in the arena and gives the group a handful of
 * seconds to reach it. Anyone not standing near it when the timer hits zero takes a crippling hit.
 * <p>
 * The value of this one is its bluntness. It has a single unambiguous instruction, a visible pillar of
 * light saying exactly where to be, a countdown everybody can read, and a consequence severe enough
 * that ignoring it is never correct. That combination is why "get to the thing" mechanics survive in
 * every raid game: they cost the player nothing to understand and everything to fumble, and they cut
 * straight through whatever else is happening — you can be mid-combo on the boss and it does not
 * matter, you drop everything and move.
 * <p>
 * It also does something no phase in this plugin currently does: it briefly pulls the entire group
 * <em>off</em> the boss and to a specific spot, which repositions the fight and breaks up whatever
 * comfortable stack the group had settled into.
 */
public final class BeaconEvent extends BossEvent {

    private static final Color BEACON_GOLD = Color.fromRGB(255, 225, 120);
    private static final long NOTICE_MS = 900;

    private final double[] triggers;
    private final int windowTicks;
    private final double safeRadius;
    private final double failDamage;
    private final double placementFraction;

    private BukkitTask task;
    private Display marker;
    private Runnable onComplete;
    private boolean resolved;

    public BeaconEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        super(plugin, bossId);
        this.triggers = triggers.clone();
        this.windowTicks = configInt("beacon-window-ticks", 100);
        this.safeRadius = configDouble("beacon-safe-radius", 4.0);
        this.failDamage = configDouble("beacon-fail-damage", 30.0);
        this.placementFraction = configDouble("beacon-placement-fraction", 0.55);
    }

    /**
     * Five seconds of "drop everything and run" is only a real decision if the fight is still happening
     * around you.
     */
    @Override
    public boolean blocksCombat() {
        return false;
    }

    @Override
    public String id() {
        return "beacon";
    }

    @Override
    public double[] triggerFractions() {
        return triggers.clone();
    }

    @Override
    public void run(BossInstance instance, Runnable onComplete) {
        this.onComplete = onComplete;
        this.resolved = false;

        Location spot = pickSpot(instance);
        if (spot == null) {
            finish(instance);
            return;
        }

        marker = Fx.glowPillar(plugin, spot, Material.SEA_LANTERN, 0.8f, 6.0f, windowTicks + 20);
        if (marker != null) {
            instance.trackEntity(marker);
        }
        Fx.coloredBurst(spot.clone().add(0, 1, 0), BEACON_GOLD, 2.4f, 60, 0.8);
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.3f, 0.6f);
        Fx.sound(spot, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 1.0f);
        instance.showTitle(
                Component.text("BEACON", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("Get to the light", NamedTextColor.GRAY));

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;

            @Override
            public void run() {
                if (resolved) {
                    return;
                }
                if (!instance.entity().isValid()) {
                    finish(instance);
                    return;
                }
                Fx.coloredRing(spot, BEACON_GOLD, 1.5f, safeRadius, 30, ticks * 0.15);
                int secondsLeft = Math.max(0, (windowTicks - ticks) / 20);
                if (ticks % 4 == 0) {
                    countdown(instance, spot, secondsLeft);
                }
                // A rising chime each second — the countdown has to be audible, since players will be
                // looking at the boss and not at their action bar when it fires.
                if (ticks % 20 == 0 && ticks < windowTicks) {
                    Fx.sound(spot, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f,
                            0.8f + 0.6f * (ticks / (float) Math.max(1, windowTicks)));
                }
                ticks++;
                if (ticks >= windowTicks) {
                    resolve(instance, spot);
                }
            }
        }, 1L, 1L);
        instance.trackTask(task);
    }

    private void countdown(BossInstance instance, Location spot, int secondsLeft) {
        double safeSq = safeRadius * safeRadius;
        for (Player player : Arena.combatants(instance.entity().getLocation(), instance.arena().radius() + 12)) {
            boolean safe = player.getWorld().equals(spot.getWorld())
                    && player.getLocation().distanceSquared(spot) <= safeSq;
            Component text = safe
                    ? Component.text("In the light — hold here  " + secondsLeft + "s", NamedTextColor.GREEN)
                    : Component.text("GET TO THE BEACON  " + secondsLeft + "s", NamedTextColor.RED);
            plugin.actionBarHub().flash(player, text, NOTICE_MS, ActionBarHub.PRIORITY_SUSTAINED);
        }
    }

    private void resolve(BossInstance instance, Location spot) {
        if (resolved) {
            return;
        }
        resolved = true;
        double safeSq = safeRadius * safeRadius;
        Fx.coloredBurst(spot.clone().add(0, 1, 0), BEACON_GOLD, 2.6f, 70, 1.0);
        Fx.sound(spot, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.8f);

        for (Player player : Arena.combatants(instance.entity().getLocation(), instance.arena().radius() + 12)) {
            boolean safe = player.getWorld().equals(spot.getWorld())
                    && player.getLocation().distanceSquared(spot) <= safeSq;
            if (safe) {
                continue;
            }
            player.damage(failDamage, instance.entity());
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), BEACON_GOLD, 1.6f, 30, 0.5);
            Fx.sound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
            plugin.actionBarHub().flash(player,
                    Component.text("You were not in the light", NamedTextColor.RED),
                    2000, ActionBarHub.PRIORITY_NOTICE);
        }
        finish(instance);
    }

    /**
     * A spot on open ground away from the boss, so the beacon actually drags the group somewhere
     * rather than landing under their feet. Anchored to the arena's fixed centre, not the boss, since
     * the boss is usually pressed up against a wall by its own leash.
     */
    private Location pickSpot(BossInstance instance) {
        Location center = instance.arena().center();
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        double dist = instance.arena().radius() * placementFraction;
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        Location spot = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }

    private void finish(BossInstance instance) {
        cleanup(instance);
        Runnable resume = onComplete;
        onComplete = null;
        if (resume != null) {
            resume.run();
        }
    }

    @Override
    public void cleanup(BossInstance instance) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        if (marker != null && marker.isValid()) {
            marker.remove();
        }
        marker = null;
    }
}
