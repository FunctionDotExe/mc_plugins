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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The floor is ice, and it is breaking. A handful of floes hold, they shrink the entire time, and
 * when the cycle ends everything that is not a floe gives way under whoever is standing on it.
 * <p>
 * What makes this different from every "get to the safe spot" mechanic is that the safe spot is
 * shrinking <em>while</em> you use it. There is no moment where the group can settle: a floe that was
 * comfortable ten seconds ago is now standing room for two, so people have to keep redistributing
 * across the arena and keep re-deciding who gets which floe, all without ever stopping the fight. The
 * boss is fully hittable throughout and keeps novaing into the shrinking ground.
 * <p>
 * Solo: at least one floe always exists and is always reachable, so a lone player simply plants
 * themselves on it — survivable, and it costs them the mobility they would rather spend on the boss.
 */
public final class ShrinkingFloesMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color color;
    private final int floeCount;
    private final double startRadius;
    private final double endRadius;
    private final int cycleTicks;
    private final double plungeDamage;
    private final int plungeFreezeTicks;
    private final double placementFraction;

    private final List<Location> floes = new ArrayList<>();
    private int cycleLeft;

    public ShrinkingFloesMechanic(BossInstance instance, String label, Color color, int floeCount,
                                   double startRadius, double endRadius, int cycleTicks,
                                   double plungeDamage, int plungeFreezeTicks, double placementFraction) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.floeCount = Math.max(1, floeCount);
        this.startRadius = Math.max(2.0, startRadius);
        this.endRadius = Math.max(1.0, Math.min(endRadius, startRadius));
        this.cycleTicks = Math.max(60, cycleTicks);
        this.plungeDamage = plungeDamage;
        this.plungeFreezeTicks = plungeFreezeTicks;
        this.placementFraction = placementFraction;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("The floor is going — hold the ice that still holds", NamedTextColor.GRAY));
        reform();
    }

    @Override
    protected void onStop() {
        floes.clear();
    }

    /** Radius the floes are at right now — the whole tension of the mechanic in one number. */
    private double currentRadius() {
        double progress = 1.0 - Math.max(0.0, Math.min(1.0, cycleLeft / (double) cycleTicks));
        return startRadius + (endRadius - startRadius) * progress;
    }

    @Override
    protected void tick() {
        if (floes.isEmpty()) {
            reform();
            return;
        }
        cycleLeft -= TICK_INTERVAL;
        double radius = currentRadius();

        for (Location floe : floes) {
            Fx.coloredRing(floe, color, 1.4f, radius, 26, elapsedTicks * 0.04);
            if (elapsedTicks % 8 == 0) {
                Fx.coloredRing(floe.clone().add(0, 0.4, 0), color, 1.0f, radius * 0.5, 12, -elapsedTicks * 0.06);
            }
        }

        boolean anyoneSafe = false;
        for (Player player : combatants()) {
            boolean safe = onFloe(player, radius);
            anyoneSafe |= safe;
            if (!safe && elapsedTicks % 10 == 0) {
                Fx.burst(player.getLocation(), Particle.SNOWFLAKE, 4, 0.3);
            }
        }
        showBars(radius);

        if (cycleLeft <= 0) {
            collapse(radius, anyoneSafe);
            reform();
        }
    }

    private void showBars(double radius) {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean safe = onFloe(viewer, radius);
            double progress = Math.max(0.0, Math.min(1.0, cycleLeft / (double) cycleTicks));
            Component text = Component.text(label + "  ", NamedTextColor.AQUA)
                    .append(safe
                            ? Component.text("on solid ice", NamedTextColor.GREEN)
                            : Component.text("THE ICE UNDER YOU IS BREAKING", NamedTextColor.RED))
                    .append(Component.text("   " + Math.max(0, cycleLeft / 20) + "s", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, progress, safe ? BossBar.Color.BLUE : BossBar.Color.RED);
        });
    }

    private boolean onFloe(Player player, double radius) {
        Location at = player.getLocation();
        for (Location floe : floes) {
            if (flatDistance(at, floe) <= radius) {
                return true;
            }
        }
        return false;
    }

    private void collapse(double radius, boolean anyoneSafe) {
        if (anyoneSafe) {
            // Somebody actually played the floes — that is the engagement the phase floor asks about.
            instance.recordExposure();
        }
        for (Location floe : floes) {
            Fx.coloredBurst(floe.clone().add(0, 0.6, 0), color, 2.0f, 30, 0.6);
        }
        Fx.sound(instance.arena().center(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);

        for (Player player : combatants()) {
            if (onFloe(player, radius)) {
                continue;
            }
            hurt(player, plungeDamage);
            if (player.isValid() && !player.isDead()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, plungeFreezeTicks, 3));
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, plungeFreezeTicks, 1));
                Fx.burst(player.getLocation(), Particle.SNOWFLAKE, 30, 0.6);
                Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.8f);
            }
        }
    }

    /** New floes somewhere else entirely, so the group never gets to memorise one patch of floor. */
    private void reform() {
        floes.clear();
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < floeCount; i++) {
            double angle = startAngle + 2 * Math.PI * i / floeCount;
            double spread = placementFraction * ThreadLocalRandom.current().nextDouble(0.6, 1.0);
            floes.add(surfaceSpot(angle, spread));
        }
        cycleLeft = cycleTicks;
        Fx.sound(instance.arena().center(), Sound.BLOCK_GLASS_PLACE, 1.2f, 0.7f);
    }
}
