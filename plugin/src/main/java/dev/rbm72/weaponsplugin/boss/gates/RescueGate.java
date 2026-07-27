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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hostage gate: the boss doesn't hide behind scenery, it takes one of you. A random player is
 * seized and held helpless, the boss is untouchable while it holds them, and the only way through
 * is for the rest of the group to physically go stand with the captive and hold that ground long
 * enough to break the hold. Free them and the boss is left cracked open; run the timer out and the
 * captive eats a heavy hit and it seizes someone else.
 * <p>
 * The pressure here is the inverse of {@link dev.rbm72.weaponsplugin.boss.Vulnerability}'s: that one
 * scatters you across the arena to break several points at once, this one collapses the whole group
 * onto a single square of ground while the boss's attack pool keeps firing into it. Splitting up is
 * what beats plates; bunching up is what beats this — so a boss built on it plays nothing like a
 * boss built on those, even with an identical attack list.
 * <p>
 * A solo player gets no rescuer and has to eat the fail — deliberate: the gate still resolves and
 * re-arms rather than deadlocking, it just costs them. {@link BossInstance#clampToPhaseFloor}'s
 * timeout valve covers the pathological case on a final phase.
 */
public final class RescueGate implements PhaseMechanic {

    /** How close an ally must stand to the captive to count as channelling the rescue. */
    private static final double RESCUE_RADIUS = 3.0;

    private static final long NOTICE_MS = 4000;

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Component label;
    private final Color color;
    private final Material pillarMaterial;
    private final Sound captureSound;
    private final int initialDelayTicks;
    private final int holdDurationTicks;
    private final int channelTicksNeeded;
    private final double failDamage;
    private final double exposedMultiplier;
    private final int exposedDurationTicks;
    private final int staggerTicks;
    private final int recaptureDelayTicks;

    private final List<Display> props = new ArrayList<>();
    private BukkitTask pendingTask;
    private BukkitTask holdTask;
    private Player captive;
    private boolean stopped;

    public RescueGate(BossInstance instance, Component label, Color color, Material pillarMaterial,
                       Sound captureSound, int initialDelayTicks, int holdDurationTicks,
                       int channelTicksNeeded, double failDamage, double exposedMultiplier,
                       int exposedDurationTicks, int staggerTicks, int recaptureDelayTicks) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.label = label;
        this.color = color;
        this.pillarMaterial = pillarMaterial;
        this.captureSound = captureSound;
        this.initialDelayTicks = initialDelayTicks;
        this.holdDurationTicks = holdDurationTicks;
        this.channelTicksNeeded = channelTicksNeeded;
        this.failDamage = failDamage;
        this.exposedMultiplier = exposedMultiplier;
        this.exposedDurationTicks = exposedDurationTicks;
        this.staggerTicks = staggerTicks;
        this.recaptureDelayTicks = recaptureDelayTicks;
    }

    @Override
    public void start() {
        // Hittable until the first capture actually lands. Holding the wall up with nothing visibly
        // happening reads as a bug, not a gate — same reasoning as Vulnerability's initial delay.
        instance.setDamageMultiplier(1.0);
        scheduleCapture(Math.max(1, initialDelayTicks));
    }

    @Override
    public void stop() {
        stopped = true;
        cancel(pendingTask);
        pendingTask = null;
        cancel(holdTask);
        holdTask = null;
        releaseCaptive();
        clearProps();
        instance.setDamageMultiplier(1.0);
        // A phase change can land mid-hold, while the boss is pinned invulnerable by an unresolved
        // capture. Without clearing both flags here it would carry that state into the next phase and
        // stay permanently unhittable.
        instance.setForcedInvulnerable(false);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
    }

    private void scheduleCapture(int delayTicks) {
        cancel(pendingTask);
        pendingTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingTask = null;
            capture();
        }, delayTicks);
        instance.trackTask(pendingTask);
    }

    private void capture() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        List<Player> candidates = Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
        if (candidates.isEmpty()) {
            // Nobody in the arena to seize — stay open and retry rather than sitting armored on an
            // empty room, which would just make the boss unhittable the moment anyone walked back in.
            instance.setDamageMultiplier(1.0);
            scheduleCapture(Math.max(20, recaptureDelayTicks));
            return;
        }

        Player seized = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        captive = seized;
        instance.setDamageMultiplier(0.0);
        instance.setForcedInvulnerable(true);

        Location base = seized.getLocation().clone();
        holdInPlace(seized, holdDurationTicks + 20);
        raiseProps(base, holdDurationTicks + 20);

        Fx.coloredBurst(base.clone().add(0, 1.2, 0), color, 2.0f, 50, 0.7);
        Fx.sound(base, captureSound, 1.0f, 0.5f);
        instance.showTitle(
                label.color(NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("Stand with the captive to break the hold", NamedTextColor.GRAY));
        plugin.actionBarHub().flash(seized,
                Component.text("You're held — your allies must reach you!", NamedTextColor.AQUA),
                NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);

        startHold(seized);
    }

    private void startHold(Player seized) {
        cancel(holdTask);
        holdTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;
            int channelled;

            @Override
            public void run() {
                if (stopped || !instance.entity().isValid() || !seized.isOnline() || !seized.isValid()) {
                    resolve(false);
                    return;
                }
                // Solo captive counts as their own rescuer: with nobody else in the arena there is no
                // ally who could ever reach them, and the boss stays armored between captures — so
                // without this the phase would be an unwinnable deadlock for a lone player rather than
                // a hard fight. In a group the captive alone never counts; someone has to come.
                boolean alone = Arena.combatants(instance.entity().getLocation(), instance.arena().radius())
                        .stream().noneMatch(p -> !p.equals(seized));
                boolean rescuerPresent = alone
                        || Arena.combatants(seized.getLocation(), RESCUE_RADIUS).stream()
                                .anyMatch(p -> !p.equals(seized));
                if (rescuerPresent) {
                    channelled++;
                    if (ticks % 5 == 0) {
                        Fx.coloredBurst(seized.getLocation().add(0, 1, 0),
                                Color.fromRGB(255, 200, 120), 0.8f, 8, 0.2);
                    }
                }
                if (ticks % 10 == 0) {
                    showProgress(seized, channelled);
                }
                if (channelled >= channelTicksNeeded) {
                    resolve(true);
                    return;
                }
                if (ticks >= holdDurationTicks) {
                    resolve(false);
                    return;
                }
                ticks++;
            }

            private void resolve(boolean rescued) {
                cancel(holdTask);
                holdTask = null;
                if (stopped) {
                    return;
                }
                if (rescued) {
                    onRescued();
                } else {
                    onFailed(seized);
                }
            }
        }, 0L, 1L);
        instance.trackTask(holdTask);
    }

    /** Progress readout for the rescuers, so "keep standing here" has a visible finish line. */
    private void showProgress(Player seized, int channelled) {
        int segments = 10;
        int filled = Math.min(segments, (int) Math.floor(segments * channelled / (double) channelTicksNeeded));
        Component bar = Component.text("Breaking the hold ", NamedTextColor.AQUA)
                .append(Component.text("▰".repeat(filled), NamedTextColor.WHITE))
                .append(Component.text("▱".repeat(segments - filled), NamedTextColor.DARK_GRAY));
        for (Player player : Arena.combatants(seized.getLocation(), RESCUE_RADIUS + 6.0)) {
            plugin.actionBarHub().flash(player, bar, 600, ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void onRescued() {
        releaseCaptive();
        clearProps();
        instance.setForcedInvulnerable(false);
        instance.recordExposure();
        instance.setDamageMultiplier(exposedMultiplier);
        instance.stagger(staggerTicks);

        Location loc = instance.entity().getLocation();
        instance.entity().setGlowing(true);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), color, 2.2f, 50, 0.8);
        Fx.flash(loc.clone().add(0, 1.2, 0), 2);
        Fx.sound(loc, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
        instance.showTitle(
                Component.text("HOLD BROKEN", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("It is left wide open", NamedTextColor.GRAY));

        cancel(pendingTask);
        pendingTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingTask = null;
            if (stopped || !instance.entity().isValid()) {
                return;
            }
            instance.entity().setGlowing(false);
            scheduleCapture(Math.max(1, recaptureDelayTicks));
        }, exposedDurationTicks);
        instance.trackTask(pendingTask);
    }

    private void onFailed(Player seized) {
        releaseCaptive();
        clearProps();
        // Stays armored through the gap to the next capture. Opening back up on a failed rescue would
        // make abandoning the captive strictly better than saving them — free damage plus no walk —
        // which is the exact opposite of what the gate is asking for. Failing costs the captive a heavy
        // hit and buys nothing; the boss simply seizes someone else. forcedInvulnerable still lifts so
        // the flag can't leak past a phase change (see stop()).
        instance.setForcedInvulnerable(false);
        instance.setDamageMultiplier(0.0);
        if (seized.isOnline() && seized.isValid()) {
            seized.damage(failDamage, instance.entity());
            Fx.coloredBurst(seized.getLocation().add(0, 1, 0), color, 2.0f, 40, 0.6);
            Fx.sound(seized.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
        }
        scheduleCapture(Math.max(1, recaptureDelayTicks));
    }

    /**
     * Pins the captive without teleport-locking them: crippling slowness plus a jump-boost level high
     * enough to zero out jumping entirely, the same trick the Frost Queen's Frozen Heart used. Leaves
     * them able to look around and call for help, which a hard freeze would not.
     */
    private void holdInPlace(Player player, int durationTicks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 6));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, 6));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationTicks, 128));
    }

    private void releaseCaptive() {
        if (captive == null) {
            return;
        }
        if (captive.isOnline()) {
            captive.removePotionEffect(PotionEffectType.SLOWNESS);
            captive.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            captive.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
        captive = null;
    }

    /**
     * A ring of glowing pillars marking the captive from across the arena — displays, not real blocks,
     * so the rescue point is unmissable without the gate griefing the floor every cycle.
     */
    private void raiseProps(Location base, int durationTicks) {
        clearProps();
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 * i;
            Location spot = base.clone().add(Math.cos(angle) * 1.6, 0, Math.sin(angle) * 1.6);
            Display pillar = Fx.glowPillar(plugin, spot, pillarMaterial, 0.4f, 2.4f, durationTicks);
            if (pillar != null) {
                props.add(pillar);
                instance.trackEntity(pillar);
            }
        }
    }

    private void clearProps() {
        for (Display prop : props) {
            if (prop.isValid()) {
                prop.remove();
            }
        }
        props.clear();
    }

    private static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
