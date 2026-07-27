package dev.rbm72.weaponsplugin.boss.gates;

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
 * Reaction gate: the boss is sealed behind a ward that never breaks from grinding on it. Instead the
 * ward drops on a rhythm — a short, loudly telegraphed opening — and landing a single hit inside that
 * window shatters it outright and staggers the boss wide open. Miss the window and it seals again and
 * everyone in the arena takes a punish pulse for standing around waiting.
 * <p>
 * Where {@link dev.rbm72.weaponsplugin.boss.Vulnerability} asks for sustained work spread over the
 * arena and {@link RescueGate} asks the group to collapse onto one spot, this asks only for timing:
 * be looking, be in range, swing on the cue. It rewards attention rather than positioning or damage,
 * which makes it the right archetype for a final phase — the fight ends on reads, not on a DPS check.
 * <p>
 * Detection runs through {@link #onBossDamaged}, so any hit that actually lands counts — melee,
 * projectile, or ability. The window itself is enforced by the damage multiplier, so a hit thrown
 * outside it is simply blocked and deflected as usual.
 */
public final class PunishWindowGate implements PhaseMechanic {

    private static final long NOTICE_MS = 1500;

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Component label;
    private final Color color;
    private final Sound openSound;
    private final int initialDelayTicks;
    private final int sealedTicks;
    private final int telegraphTicks;
    private final int windowTicks;
    private final double exposedMultiplier;
    private final int exposedDurationTicks;
    private final int staggerTicks;
    private final double missPulseDamage;

    private BukkitTask pendingTask;
    private BukkitTask telegraphTask;
    private boolean windowOpen;
    private boolean stopped;

    public PunishWindowGate(BossInstance instance, Component label, Color color, Sound openSound,
                             int initialDelayTicks, int sealedTicks, int telegraphTicks, int windowTicks,
                             double exposedMultiplier, int exposedDurationTicks, int staggerTicks,
                             double missPulseDamage) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.label = label;
        this.color = color;
        this.openSound = openSound;
        this.initialDelayTicks = initialDelayTicks;
        this.sealedTicks = sealedTicks;
        this.telegraphTicks = telegraphTicks;
        this.windowTicks = windowTicks;
        this.exposedMultiplier = exposedMultiplier;
        this.exposedDurationTicks = exposedDurationTicks;
        this.staggerTicks = staggerTicks;
        this.missPulseDamage = missPulseDamage;
    }

    @Override
    public void start() {
        // Open for the grace beat after the phase transition, then the ward slams shut and the rhythm
        // begins — same "don't gate an arena that hasn't been told yet" rule the other gates follow.
        instance.setDamageMultiplier(1.0);
        schedule(this::seal, Math.max(1, initialDelayTicks));
    }

    @Override
    public void stop() {
        stopped = true;
        cancel(pendingTask);
        pendingTask = null;
        cancel(telegraphTask);
        telegraphTask = null;
        windowOpen = false;
        instance.setDamageMultiplier(1.0);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
    }

    private void seal() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        windowOpen = false;
        instance.setDamageMultiplier(0.0);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
        announce(label.color(NamedTextColor.GRAY)
                .append(Component.text("  —  sealed. Strike the moment it drops.", NamedTextColor.DARK_GRAY)));
        // Sealed stretch, then a visible wind-up, then the opening itself.
        schedule(this::telegraph, Math.max(1, sealedTicks));
    }

    /** The tell. Tightening rings and a rising pitch so the opening is readable without a UI element. */
    private void telegraph() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        int total = Math.max(1, telegraphTicks);
        cancel(telegraphTask);
        telegraphTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;

            @Override
            public void run() {
                if (stopped || !instance.entity().isValid()) {
                    cancel(telegraphTask);
                    telegraphTask = null;
                    return;
                }
                double progress = ticks / (double) total;
                Location loc = instance.entity().getLocation();
                Fx.coloredRing(loc, color, 1.2f, 4.0 * (1.0 - progress) + 0.6, 22, progress * Math.PI);
                if (ticks % 4 == 0) {
                    Fx.sound(loc, Sound.BLOCK_NOTE_BLOCK_BIT, 0.9f, 0.6f + 1.2f * (float) progress);
                }
                ticks++;
                if (ticks >= total) {
                    cancel(telegraphTask);
                    telegraphTask = null;
                    openWindow();
                }
            }
        }, 0L, 1L);
        instance.trackTask(telegraphTask);
    }

    private void openWindow() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        windowOpen = true;
        // The window IS the damage: any hit landed here is already multiplied, and the first one to
        // land shatters the ward outright in onBossDamaged.
        instance.setDamageMultiplier(exposedMultiplier);
        instance.entity().setGlowing(true);

        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), color, 2.0f, 45, 0.7);
        Fx.flash(loc.clone().add(0, 1.2, 0), 1);
        Fx.sound(loc, openSound, 1.3f, 1.4f);
        instance.showTitle(
                Component.text("THE WARD DROPS", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("Strike now", NamedTextColor.GRAY));

        schedule(this::missed, Math.max(1, windowTicks));
    }

    @Override
    public void onBossDamaged(org.bukkit.entity.Player attacker, double damageDealt) {
        if (stopped || !windowOpen || damageDealt <= 0.0) {
            return;
        }
        windowOpen = false;
        cancel(pendingTask);
        pendingTask = null;

        instance.recordExposure();
        instance.stagger(staggerTicks);

        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), color, 2.4f, 60, 0.9);
        Fx.flash(loc.clone().add(0, 1.2, 0), 2);
        Fx.sound(loc, Sound.ITEM_SHIELD_BREAK, 1.3f, 1.1f);
        instance.showTitle(
                Component.text("WARD SHATTERED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Punish it", NamedTextColor.GRAY));

        // Struck cleanly: the opening stretches into a real punish window before the ward reforms.
        schedule(this::seal, Math.max(1, exposedDurationTicks));
    }

    /** Window closed untouched — the ward holds and everyone still standing around pays for the beat. */
    private void missed() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        windowOpen = false;
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), color, 1.8f, 40, 0.7);
        Fx.sound(loc, Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.5f);
        for (Player player : Arena.combatants(loc, instance.arena().radius())) {
            player.damage(missPulseDamage, instance.entity());
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 1.2f, 16, 0.3);
        }
        seal();
    }

    private void announce(Component message) {
        for (Player player : Arena.combatants(instance.entity().getLocation(), instance.arena().radius())) {
            plugin.actionBarHub().flash(player, message, NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void schedule(Runnable action, int delayTicks) {
        cancel(pendingTask);
        pendingTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingTask = null;
            action.run();
        }, delayTicks);
        instance.trackTask(pendingTask);
    }

    private static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
