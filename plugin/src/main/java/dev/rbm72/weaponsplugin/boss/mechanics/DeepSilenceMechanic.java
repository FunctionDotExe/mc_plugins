package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The lights go out and the fight stops making noise. What is left is a single pulse of light on the
 * floor, expanding outward, and the only information anyone gets about what is coming.
 * <p>
 * Every other telegraph in this plugin is doubled — a sound <em>and</em> a visual — precisely because
 * players are looking at the boss rather than at the UI. This phase removes half of that on purpose,
 * once, at the very end of the roster. It is the payoff for a whole game's worth of learning to
 * listen: strip the audio channel and a group that had been coasting on sound cues suddenly has to
 * watch the ground.
 * <p>
 * Deliberately not blinding: darkness dims the world without taking sight away, so the pulse is
 * always readable and the mechanic is hard rather than unfair. It fires no sounds of its own, which
 * is the entire conceit — every other mechanic in the fight is shouting, and this one is not.
 */
public final class DeepSilenceMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color color;
    private final int pulseIntervalTicks;
    private final int telegraphTicks;
    private final double safeRadius;
    private final double damage;

    private int pulseCountdown;
    private Location pulseCentre;
    private int telegraphLeft;
    private int survived;
    private int hitBy;

    public DeepSilenceMechanic(BossInstance instance, String label, Color color, int pulseIntervalTicks,
                                int telegraphTicks, double safeRadius, double damage) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.pulseIntervalTicks = Math.max(60, pulseIntervalTicks);
        this.telegraphTicks = Math.max(20, telegraphTicks);
        this.safeRadius = Math.max(3.0, safeRadius);
        this.damage = damage;
    }

    @Override
    protected void onStart() {
        pulseCountdown = pulseIntervalTicks;
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true),
                Component.text("No sound will warn you. Watch the floor.", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        for (Player player : combatants()) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    @Override
    protected void tick() {
        // Refreshed rather than applied once, so a player who dies and returns is silenced too.
        if (elapsedTicks % 20 == 0) {
            for (Player player : combatants()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false));
            }
        }

        if (telegraphLeft > 0) {
            telegraphLeft -= TICK_INTERVAL;
            drawPulse();
            if (telegraphLeft <= 0) {
                resolvePulse();
            }
            showBars();
            return;
        }

        pulseCountdown -= TICK_INTERVAL;
        if (pulseCountdown <= 0) {
            beginPulse();
        }
        showBars();
    }

    private void beginPulse() {
        pulseCountdown = pulseIntervalTicks;
        telegraphLeft = telegraphTicks;
        pulseCentre = instance.entity().getLocation().clone();
        // No sound. That is the mechanic.
    }

    private void drawPulse() {
        if (pulseCentre == null) {
            return;
        }
        double progress = 1.0 - Math.max(0.0, telegraphLeft / (double) telegraphTicks);
        double radius = safeRadius * progress;
        Fx.coloredRing(pulseCentre, color, 1.8f, Math.max(0.5, radius), 30, elapsedTicks * 0.1);
        if (elapsedTicks % 4 == 0) {
            Fx.coloredRing(pulseCentre.clone().add(0, 0.8, 0), color, 1.2f,
                    Math.max(0.5, radius * 0.7), 20, -elapsedTicks * 0.12);
        }
    }

    private void resolvePulse() {
        if (pulseCentre == null) {
            return;
        }
        Fx.coloredBurst(pulseCentre.clone().add(0, 1.0, 0), color, 2.6f, 60, safeRadius * 0.35);
        Fx.burst(pulseCentre.clone().add(0, 1.0, 0), Particle.SONIC_BOOM, 2, 0.2);

        boolean anyoneClear = false;
        for (Player player : combatants()) {
            if (flatDistance(player.getLocation(), pulseCentre) > safeRadius) {
                anyoneClear = true;
                continue;
            }
            hurt(player, damage);
            if (player.isValid() && !player.isDead()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 1.6f, 20, 0.4);
            }
        }
        if (anyoneClear) {
            survived++;
            // Reading a silent telegraph and clearing it is exactly what this phase is asking for.
            instance.recordExposure();
        } else {
            hitBy++;
        }
        pulseCentre = null;
    }

    private void showBars() {
        int total = survived + hitBy;
        double clean = total == 0 ? 1.0 : survived / (double) total;
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean inPulse = pulseCentre != null
                    && flatDistance(viewer.getLocation(), pulseCentre) <= safeRadius;
            Component text = Component.text(label + "  ", NamedTextColor.DARK_GRAY)
                    .append(telegraphLeft > 0
                            ? (inPulse
                                    ? Component.text("GET OUT OF THE LIGHT", NamedTextColor.RED)
                                    : Component.text("clear of it", NamedTextColor.GREEN))
                            : Component.text("silence", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, clean,
                    telegraphLeft > 0 && inPulse ? BossBar.Color.RED : BossBar.Color.WHITE);
        });
    }
}
