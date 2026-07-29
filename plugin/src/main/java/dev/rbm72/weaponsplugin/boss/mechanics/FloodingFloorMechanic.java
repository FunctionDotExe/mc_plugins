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
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The water comes up. Not as blocks — as a line across the arena that keeps climbing, drowning
 * everything under it, until it peaks and drains away to start again.
 * <p>
 * What this really is, mechanically, is a countdown that costs you the floor. Every second the set of
 * places you are allowed to stand gets smaller and higher, so a group that fights from a comfortable
 * flat patch has to keep abandoning it, and the arena's terrain suddenly matters — the one hill
 * nobody looked at becomes the most valuable ground in the fight. The boss is fully hittable
 * throughout and keeps attacking, so the climb is something you do <em>while</em> fighting, not
 * instead of it.
 * <p>
 * It drains completely at the peak rather than staying up, which keeps it a rhythm rather than a
 * slow squeeze toward an unwinnable end state. Solo works unchanged: high ground is high ground.
 */
public final class FloodingFloorMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 4L;
    private static final int SURFACE_RING_POINTS = 30;

    private final String label;
    private final Color color;
    private final double risePerSecond;
    private final double maxRise;
    private final int holdAtPeakTicks;
    private final int drainedPauseTicks;
    private final double drownDamagePerSecond;
    private final boolean blindWhenSubmerged;

    private double baseY;
    private double level;
    private int peakHoldLeft;
    private int drainedPauseLeft;
    private boolean draining;

    public FloodingFloorMechanic(BossInstance instance, String label, Color color, double risePerSecond,
                                  double maxRise, int holdAtPeakTicks, int drainedPauseTicks,
                                  double drownDamagePerSecond, boolean blindWhenSubmerged) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.risePerSecond = Math.max(0.05, risePerSecond);
        this.maxRise = Math.max(1.0, maxRise);
        this.holdAtPeakTicks = Math.max(0, holdAtPeakTicks);
        this.drainedPauseTicks = Math.max(0, drainedPauseTicks);
        this.drownDamagePerSecond = drownDamagePerSecond;
        this.blindWhenSubmerged = blindWhenSubmerged;
    }

    @Override
    protected void onStart() {
        Location centre = instance.arena().center();
        World world = centre.getWorld();
        baseY = world != null
                ? world.getHighestBlockYAt(centre.getBlockX(), centre.getBlockZ()) + 1
                : centre.getY();
        level = baseY;
        instance.showTitle(
                Component.text(label, NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("It is rising — get high, and stay fighting", NamedTextColor.GRAY));
        Fx.sound(centre, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.6f);
    }

    @Override
    protected void tick() {
        double step = TICK_INTERVAL / 20.0;

        if (drainedPauseLeft > 0) {
            drainedPauseLeft -= TICK_INTERVAL;
            showBars(0.0);
            return;
        }

        if (draining) {
            level = Math.max(baseY, level - risePerSecond * 2.5 * step);
            if (level <= baseY + 0.01) {
                draining = false;
                drainedPauseLeft = drainedPauseTicks;
                Fx.sound(instance.arena().center(), Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.2f);
            }
        } else if (peakHoldLeft > 0) {
            peakHoldLeft -= TICK_INTERVAL;
            if (peakHoldLeft <= 0) {
                draining = true;
            }
        } else {
            level += risePerSecond * step;
            if (level >= baseY + maxRise) {
                level = baseY + maxRise;
                peakHoldLeft = holdAtPeakTicks;
                Fx.sound(instance.arena().center(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 0.7f);
                instance.showTitle(
                        Component.text("HIGH WATER", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                        Component.text("Hold what ground is left", NamedTextColor.GRAY));
            }
        }

        drawSurface();
        boolean anyoneDry = false;
        for (Player player : combatants()) {
            if (player.getLocation().getY() >= level) {
                anyoneDry = true;
                continue;
            }
            if (elapsedTicks % 20 == 0) {
                tickHurt(player, drownDamagePerSecond);
            }
            if (player.isValid() && !player.isDead()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false));
                if (blindWhenSubmerged) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false));
                }
                if (elapsedTicks % 8 == 0) {
                    Fx.burst(player.getLocation().add(0, 0.4, 0), Particle.BUBBLE, 6, 0.4);
                }
            }
        }

        if (anyoneDry && elapsedTicks % 20 == 0) {
            // Staying above the water while continuing the fight is what this phase asks for.
            instance.recordExposure();
        }
        showBars((level - baseY) / maxRise);
    }

    /** Draws the waterline as concentric rings at the current level — cheap, and unmissable. */
    private void drawSurface() {
        if (elapsedTicks % 4 != 0) {
            return;
        }
        Location centre = instance.arena().center().clone();
        centre.setY(level);
        double arenaRadius = instance.arena().radius();
        for (int band = 1; band <= 3; band++) {
            double radius = arenaRadius * band / 3.5;
            Fx.coloredRing(centre, color, 1.2f, radius, SURFACE_RING_POINTS, elapsedTicks * 0.03 * band);
        }
    }

    private void showBars(double fill) {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean submerged = viewer.getLocation().getY() < level;
            double headroom = viewer.getLocation().getY() - level;
            Component text = Component.text(label + "  ", NamedTextColor.BLUE)
                    .append(submerged
                            ? Component.text("UNDER — climb", NamedTextColor.RED)
                            : Component.text(String.format(java.util.Locale.ROOT, "%.1f blocks of headroom", headroom),
                                    NamedTextColor.GREEN))
                    .append(Component.text(draining ? "   draining" : peakHoldLeft > 0 ? "   at peak" : "   rising",
                            NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, Math.max(0.0, Math.min(1.0, fill)),
                    submerged ? BossBar.Color.RED : BossBar.Color.BLUE);
        });
    }
}
