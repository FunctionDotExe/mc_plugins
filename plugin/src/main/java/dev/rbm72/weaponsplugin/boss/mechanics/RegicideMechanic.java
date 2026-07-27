package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Regicide: the boss cannot be killed on its throne. Damage lands normally right down to a single
 * point of health, and there it stops — while it stands on the dais it simply will not die, and it
 * knows it, so it fights to stay there. The killing blow only counts once it has been driven off.
 * <p>
 * Health stops being the win condition. A group that has spent the whole fight optimising damage
 * arrives here, burns it to 1 HP in seconds, and then has to work out that the answer is knockback,
 * positioning and pressure rather than more damage. The dais is drawn on the ground the entire time
 * and the action bar says plainly what is happening, so the puzzle is in solving it, not in guessing
 * what the rules are.
 * <p>
 * Nothing here is a gate: the boss is never immune, never shielded, and never asks for a chore. It is
 * simply standing in the wrong place to die.
 */
public final class RegicideMechanic implements PhaseMechanic {

    private static final long NOTICE_MS = 1500;
    private static final long TICK_INTERVAL = 5L;

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Color color;
    private final double throneRadius;
    private final double reclaimPullStrength;

    private BukkitTask task;
    private boolean warned;
    private boolean stopped;

    public RegicideMechanic(BossInstance instance, Color color, double throneRadius,
                             double reclaimPullStrength) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.color = color;
        this.throneRadius = throneRadius;
        this.reclaimPullStrength = reclaimPullStrength;
    }

    @Override
    public void start() {
        instance.setDamageMultiplier(1.0);
        instance.showTitle(
                Component.text("HE WILL NOT DIE HERE", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Drive him from the throne", NamedTextColor.GRAY));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, TICK_INTERVAL, TICK_INTERVAL);
        instance.trackTask(task);
    }

    @Override
    public void stop() {
        stopped = true;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        instance.setDamageMultiplier(1.0);
    }

    /**
     * Damage is never blocked — it is only prevented from being lethal while he holds the dais. Capping
     * at "leave one point" rather than zeroing the hit keeps the fight feeling responsive: numbers keep
     * coming off, the health bar keeps moving, and the wall is specifically and legibly the last point.
     */
    @Override
    public double filterDamage(Player attacker, double damage) {
        if (!onThrone()) {
            return damage;
        }
        double survivable = Math.max(0.0, instance.entity().getHealth() - 1.0);
        if (damage > survivable) {
            plugin.actionBarHub().flash(attacker,
                    Component.text("He cannot fall while he holds the throne", NamedTextColor.RED),
                    NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
            return survivable;
        }
        return damage;
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (damageDealt > 0 && !onThrone()) {
            // Hurting him off the dais is the phase's actual objective, so it clears the phase floor
            // and lets the finishing blow land.
            instance.recordExposure();
        }
    }

    private void pulse() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        Location throne = instance.arena().center();
        World world = throne.getWorld();
        if (world == null) {
            return;
        }
        Fx.coloredRing(throne, color, 1.4f, throneRadius, 32, 0);

        boolean on = onThrone();
        if (on != !warned) {
            // Announce each crossing once rather than every tick.
            warned = on;
            Location loc = instance.entity().getLocation();
            if (on) {
                Fx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
            } else {
                Fx.coloredBurst(loc.clone().add(0, 1.2, 0), color, 2.0f, 40, 0.7);
                Fx.sound(loc, Sound.ITEM_SHIELD_BREAK, 1.2f, 1.0f);
                for (Player player : Arena.combatants(loc, instance.arena().radius())) {
                    plugin.actionBarHub().flash(player,
                            Component.text("He is off the throne — end it now!", NamedTextColor.GREEN),
                            NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
                }
            }
        }

        // He fights to get back. A slow, readable pull rather than a teleport, so players can hold him
        // out with knockback and body-blocking instead of watching the mechanic undo itself instantly.
        if (!on && reclaimPullStrength > 0) {
            Location loc = instance.entity().getLocation();
            var toThrone = throne.toVector().subtract(loc.toVector()).setY(0);
            if (toThrone.lengthSquared() > 1.0E-6) {
                instance.entity().setVelocity(instance.entity().getVelocity()
                        .add(toThrone.normalize().multiply(reclaimPullStrength)));
            }
        }
    }

    private boolean onThrone() {
        Location throne = instance.arena().center();
        Location loc = instance.entity().getLocation();
        if (throne.getWorld() == null || loc.getWorld() == null || !throne.getWorld().equals(loc.getWorld())) {
            return false;
        }
        double dx = loc.getX() - throne.getX();
        double dz = loc.getZ() - throne.getZ();
        return dx * dx + dz * dz <= throneRadius * throneRadius;
    }
}
