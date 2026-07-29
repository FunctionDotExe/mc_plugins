package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rods charge up around the arena on a fixed clock. Each rod that has somebody standing under it when
 * it fires earths itself through that player for a small, survivable jolt; each rod that does not
 * dumps its whole charge into the room instead.
 * <p>
 * The verb here is <em>splitting up under pressure</em>. It is the exact opposite ask from a hostage
 * rescue, and unlike a weak-point ring it never pauses the fight to make it: the rods fire whether or
 * not anyone is ready, the boss keeps attacking throughout, and the group has to decide every cycle
 * how many people to peel off the boss and how much unrouted lightning it is willing to eat. A group
 * that ignores the rods entirely takes a punishing but non-lethal hit each cycle — legible, severe,
 * and never a lockout.
 * <p>
 * The rod count follows the number of players present (never more than there are people, never fewer
 * than one), so it is always exactly solvable by full participation and never impossible solo.
 */
public final class GroundingRodsMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color color;
    private final Material rodMaterial;
    private final int maxRods;
    private final double groundingRadius;
    private final int chargeTicks;
    private final double groundedDamage;
    private final double unroutedDamage;
    private final double placementFraction;

    private final List<Rod> rods = new ArrayList<>();
    private int chargeLeft;

    private static final class Rod {
        final Location at;
        Display prop;
        boolean grounded;

        Rod(Location at) {
            this.at = at;
        }
    }

    public GroundingRodsMechanic(BossInstance instance, String label, Color color, Material rodMaterial,
                                  int maxRods, double groundingRadius, int chargeTicks,
                                  double groundedDamage, double unroutedDamage, double placementFraction) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.rodMaterial = rodMaterial;
        this.maxRods = Math.max(1, maxRods);
        this.groundingRadius = groundingRadius;
        this.chargeTicks = Math.max(40, chargeTicks);
        this.groundedDamage = groundedDamage;
        this.unroutedDamage = unroutedDamage;
        this.placementFraction = placementFraction;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("Spread across the rods — every one left empty discharges into the room", NamedTextColor.GRAY));
        raise();
    }

    @Override
    protected void onStop() {
        clearRods();
    }

    @Override
    protected void tick() {
        if (rods.isEmpty()) {
            raise();
            return;
        }

        chargeLeft -= TICK_INTERVAL;
        double charge = 1.0 - Math.max(0.0, chargeLeft / (double) chargeTicks);

        int covered = 0;
        for (Rod rod : rods) {
            rod.grounded = !Arena.combatants(rod.at, groundingRadius).isEmpty();
            if (rod.grounded) {
                covered++;
            }
            if (elapsedTicks % 4 == 0) {
                Fx.coloredRing(rod.at, rod.grounded ? Color.fromRGB(120, 255, 140) : color, 1.3f,
                        groundingRadius, 18, elapsedTicks * 0.09);
            }
            if (charge > 0.6 && elapsedTicks % 6 == 0) {
                Fx.burst(rod.at.clone().add(0, 2.4, 0), Particle.ELECTRIC_SPARK, (int) (4 + 8 * charge), 0.35);
            }
        }

        showBars(covered, charge);

        if (chargeLeft <= 0) {
            discharge();
            raise();
        }
    }

    private void showBars(int covered, double charge) {
        Component text = Component.text(label + "  ", NamedTextColor.YELLOW)
                .append(Component.text(covered + " / " + rods.size() + " earthed",
                        covered >= rods.size() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("   " + Math.max(0, chargeLeft / 20) + "s to discharge", NamedTextColor.GRAY));
        BossBar.Color barColor = covered >= rods.size() ? BossBar.Color.GREEN
                : charge > 0.7 ? BossBar.Color.RED : BossBar.Color.YELLOW;
        instance.mechanicBar().updateShared(instance.barViewers(), text, charge, barColor);
    }

    private void discharge() {
        int unrouted = 0;
        for (Rod rod : rods) {
            Fx.coloredBurst(rod.at.clone().add(0, 1.2, 0), color, 2.0f, 30, 0.6);
            if (rod.grounded) {
                Fx.sound(rod.at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
                for (Player player : Arena.combatants(rod.at, groundingRadius)) {
                    hurt(player, groundedDamage);
                }
            } else {
                unrouted++;
                Fx.sound(rod.at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.3f, 0.7f);
            }
        }

        if (unrouted == 0) {
            instance.recordExposure();
            instance.showTitle(
                    Component.text("FULLY EARTHED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                    Component.text("The storm had nowhere to go", NamedTextColor.GRAY));
            return;
        }

        double damage = unroutedDamage * unrouted;
        Location bossAt = instance.entity().getLocation();
        Fx.expandingRings(plugin, bossAt, Particle.ELECTRIC_SPARK,
                Math.min(16.0, instance.arena().radius() * 0.7), 4, 2L);
        Fx.sound(bossAt, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.6f, 0.6f);
        for (Player player : combatants()) {
            hurt(player, damage);
        }
        instance.showTitle(
                Component.text(unrouted + " ROD" + (unrouted > 1 ? "S" : "") + " UNEARTHED",
                        NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("The charge went through you instead", NamedTextColor.GRAY));
    }

    /**
     * Rebuilds the rod set for the next cycle. Anchored to the arena's fixed centre rather than the
     * boss — the boss spends most of a fight pressed against the wall, and rods placed relative to it
     * would routinely land outside the reachable floor.
     */
    private void raise() {
        clearRods();
        int count = Math.max(1, Math.min(maxRods, combatants().size()));
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + 2 * Math.PI * i / count;
            Location spot = surfaceSpot(angle, placementFraction);
            Rod rod = new Rod(spot);
            rod.prop = Fx.glowPillar(plugin, spot, rodMaterial, 0.35f, 3.2f, chargeTicks + 20);
            if (rod.prop != null) {
                instance.trackEntity(rod.prop);
            }
            rods.add(rod);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), color, 1.4f, 18, 0.4);
        }
        chargeLeft = chargeTicks;
        Fx.sound(instance.arena().center(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.3f);
    }

    private void clearRods() {
        for (Rod rod : rods) {
            if (rod.prop != null && rod.prop.isValid()) {
                rod.prop.remove();
            }
        }
        rods.clear();
    }
}
