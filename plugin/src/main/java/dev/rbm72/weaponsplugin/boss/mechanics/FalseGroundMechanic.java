package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.Arena;
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
 * Some of the floor is a lie. It looks exactly like the rest of it until the moment it stops being
 * there, and whoever was standing on it goes through.
 * <p>
 * The reason this is not just another "don't stand in the circle" mechanic is the information delay.
 * A false patch is <em>faintly</em> marked the whole time — a shimmer a player can spot if they are
 * looking down, which nobody does mid-fight — and only becomes obvious in the second before it drops.
 * So it rewards a completely different thing from every other floor mechanic in the roster: paying
 * attention to the arena early, while nothing is happening, instead of reacting fast when it does.
 * <p>
 * Patches re-roll after every collapse, so the map cannot be memorised, and a player who has fallen
 * through once knows exactly what the shimmer looks like from then on. That is the intended teaching
 * curve: one cheap mistake, then it is a skill.
 */
public final class FalseGroundMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color shimmerColor;
    private final int patchCount;
    private final double radius;
    private final int cycleTicks;
    private final int revealTicks;
    private final double plungeDamage;
    private final int disorientTicks;
    private final double placementFraction;

    private final List<Location> patches = new ArrayList<>();
    private int cycleLeft;

    public FalseGroundMechanic(BossInstance instance, String label, Color shimmerColor, int patchCount,
                                double radius, int cycleTicks, int revealTicks, double plungeDamage,
                                int disorientTicks, double placementFraction) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.shimmerColor = shimmerColor;
        this.patchCount = Math.max(1, patchCount);
        this.radius = Math.max(1.5, radius);
        this.cycleTicks = Math.max(80, cycleTicks);
        this.revealTicks = Math.max(20, Math.min(revealTicks, cycleTicks - 20));
        this.plungeDamage = plungeDamage;
        this.disorientTicks = disorientTicks;
        this.placementFraction = placementFraction;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Not all of this floor is real — watch where you plant your feet", NamedTextColor.GRAY));
        reroll();
    }

    @Override
    protected void onStop() {
        patches.clear();
    }

    @Override
    protected void tick() {
        if (patches.isEmpty()) {
            reroll();
            return;
        }
        cycleLeft -= TICK_INTERVAL;
        boolean revealing = cycleLeft <= revealTicks;

        for (Location patch : patches) {
            if (revealing) {
                double urgency = 1.0 - Math.max(0.0, cycleLeft / (double) revealTicks);
                Fx.coloredRing(patch, shimmerColor, (float) (1.2 + urgency * 1.4), radius, 22,
                        elapsedTicks * 0.18);
                if (elapsedTicks % 6 == 0) {
                    Fx.burst(patch.clone().add(0, 0.3, 0), Particle.PORTAL, 8, radius * 0.4);
                    Fx.sound(patch, Sound.BLOCK_POINTED_DRIPSTONE_HIT, 0.8f, 0.5f + (float) urgency);
                }
            } else if (elapsedTicks % 14 == 0) {
                // The tell: quiet, sparse, and only visible to someone actually looking at the ground.
                Fx.coloredRing(patch, shimmerColor, 0.7f, radius, 8, elapsedTicks * 0.02);
            }
        }

        showBars(revealing);
        if (cycleLeft <= 0) {
            collapse();
            reroll();
        }
    }

    private void showBars(boolean revealing) {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean standingOnLie = onFalseGround(viewer);
            double progress = Math.max(0.0, Math.min(1.0, cycleLeft / (double) cycleTicks));
            Component text = Component.text(label + "  ", NamedTextColor.DARK_PURPLE)
                    .append(standingOnLie && revealing
                            ? Component.text("THE GROUND UNDER YOU IS GOING", NamedTextColor.RED)
                            : revealing
                                    ? Component.text("it is showing itself — look down", NamedTextColor.GOLD)
                                    : Component.text("somewhere here is false", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, progress,
                    standingOnLie && revealing ? BossBar.Color.RED : BossBar.Color.PURPLE);
        });
    }

    private boolean onFalseGround(Player player) {
        for (Location patch : patches) {
            if (flatDistance(player.getLocation(), patch) <= radius) {
                return true;
            }
        }
        return false;
    }

    private void collapse() {
        boolean anyoneClear = false;
        for (Player player : combatants()) {
            if (!onFalseGround(player)) {
                anyoneClear = true;
            }
        }
        if (anyoneClear) {
            // Reading the floor and being off it is the thing this phase is asking for.
            instance.recordExposure();
        }

        for (Location patch : patches) {
            Fx.coloredBurst(patch.clone().add(0, 0.5, 0), shimmerColor, 2.4f, 40, radius * 0.5);
            Fx.burst(patch.clone().add(0, 0.5, 0), Particle.PORTAL, 30, radius * 0.5);
            Fx.sound(patch, Sound.BLOCK_PORTAL_TRIGGER, 1.2f, 0.6f);

            for (Player player : Arena.combatants(patch, radius)) {
                hurt(player, plungeDamage);
                if (player.isValid() && !player.isDead()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, disorientTicks, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, disorientTicks, 2));
                    Fx.sound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
                }
            }
        }
    }

    private void reroll() {
        patches.clear();
        for (int i = 0; i < patchCount; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double spread = ThreadLocalRandom.current().nextDouble(0.1, placementFraction);
            patches.add(surfaceSpot(angle, spread));
        }
        cycleLeft = cycleTicks;
    }
}
