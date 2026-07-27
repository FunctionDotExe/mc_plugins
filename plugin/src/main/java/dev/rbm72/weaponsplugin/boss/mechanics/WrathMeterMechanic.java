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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Wrath: the boss is hittable at all times and hurting it is always allowed — but every point of
 * damage it takes feeds a meter, and filling that meter detonates the arena. The meter bleeds off
 * steadily, so the phase is a throttle: deal damage faster than it decays and you are building toward
 * your own wipe.
 * <p>
 * This is the one mechanic in the game where the correct play is sometimes to stop attacking, and
 * where a stronger group is at more risk than a weaker one. Every other mechanic in the roster
 * rewards dealing as much damage as fast as possible the instant a window opens; this inverts that
 * completely, and a group running on gate reflexes will detonate it repeatedly before the penny
 * drops.
 * <p>
 * There is no lockout anywhere in it. The meter is pure feedback, shown continuously on the action
 * bar so the group can see themselves approaching the cliff and choose to back off.
 */
public final class WrathMeterMechanic implements PhaseMechanic {

    private static final long NOTICE_MS = 1200;
    private static final long TICK_INTERVAL = 5L;
    private static final int BAR_SEGMENTS = 20;

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Color color;
    private final double wrathCap;
    private final double decayPerSecond;
    private final double novaDamage;
    private final double novaRadius;
    private final int calmTicksAfterNova;

    private BukkitTask task;
    private double wrath;
    private int calmTicksLeft;
    private boolean stopped;

    public WrathMeterMechanic(BossInstance instance, Color color, double wrathCap, double decayPerSecond,
                               double novaDamage, double novaRadius, int calmTicksAfterNova) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.color = color;
        this.wrathCap = Math.max(1.0, wrathCap);
        this.decayPerSecond = decayPerSecond;
        this.novaDamage = novaDamage;
        this.novaRadius = novaRadius;
        this.calmTicksAfterNova = calmTicksAfterNova;
    }

    @Override
    public void start() {
        instance.setDamageMultiplier(1.0);
        instance.showTitle(
                Component.text("THE CROWN'S WEIGHT", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every wound feeds its fury — pace yourselves", NamedTextColor.GRAY));
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

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (stopped || damageDealt <= 0) {
            return;
        }
        // Damage taken during the post-nova lull is free — the mechanic's whole shape is a rhythm of
        // building pressure and a spent window, not a permanent tax.
        if (calmTicksLeft > 0) {
            instance.recordExposure();
            return;
        }
        wrath = Math.min(wrathCap, wrath + damageDealt);
        instance.recordExposure();
        if (wrath >= wrathCap) {
            detonate();
        }
    }

    private void pulse() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        if (calmTicksLeft > 0) {
            calmTicksLeft -= TICK_INTERVAL;
            showBar(true);
            return;
        }
        wrath = Math.max(0.0, wrath - decayPerSecond * (TICK_INTERVAL / 20.0));
        showBar(false);

        if (wrath > wrathCap * 0.75) {
            Location loc = instance.entity().getLocation();
            Fx.coloredBurst(loc.clone().add(0, 1.6, 0), color, 1.4f, 14, 0.5);
            if (wrath > wrathCap * 0.9) {
                Fx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.8f, 1.5f);
            }
        }
    }

    /** Continuous readout — the mechanic is only fair if the group can watch the meter climb. */
    private void showBar(boolean spent) {
        double fraction = Math.max(0.0, Math.min(1.0, wrath / wrathCap));
        int filled = (int) Math.round(BAR_SEGMENTS * fraction);
        NamedTextColor tone = spent ? NamedTextColor.GRAY
                : fraction > 0.75 ? NamedTextColor.RED
                : fraction > 0.4 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        Component bar = Component.text(spent ? "Spent  " : "Wrath  ", NamedTextColor.DARK_GRAY)
                .append(Component.text("▮".repeat(filled), tone))
                .append(Component.text("▯".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
        for (Player player : Arena.combatants(instance.entity().getLocation(), instance.arena().radius())) {
            plugin.actionBarHub().flash(player, bar, NOTICE_MS, ActionBarHub.PRIORITY_SUSTAINED);
        }
    }

    private void detonate() {
        wrath = 0.0;
        calmTicksLeft = calmTicksAfterNova;

        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), color, 2.6f, 70, 1.0);
        Fx.flash(loc.clone().add(0, 1.2, 0), 3);
        Fx.expandingRings(plugin, loc, org.bukkit.Particle.SOUL_FIRE_FLAME, novaRadius, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
        instance.showTitle(
                Component.text("WRATH UNLEASHED", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("You pushed it too hard", NamedTextColor.GRAY));

        for (Player player : Arena.combatants(loc, novaRadius)) {
            player.damage(novaDamage, instance.entity());
        }
    }
}
