package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
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
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A collapsing star drops into the arena, hauls everyone toward it, and detonates if it is still
 * there when it finishes falling in on itself. Killing it is the only way to stop that.
 * <p>
 * It is a pure damage race, which is exactly why it belongs as an occasional event rather than a
 * phase: there is no positioning trick, no order to read and nothing clever to do — just "everything
 * you have, at that, right now". A fight full of thinking mechanics needs one beat that asks the
 * simplest possible question, and the answer doubles as an honest measure of whether the group's
 * damage is keeping up with the boss it has chosen to fight.
 * <p>
 * The pull is what stops it being a free chore. Everyone gets dragged toward the thing they are
 * trying to kill, so the closer the group is to failing the race, the worse their position is when
 * it goes off. The boss keeps fighting throughout.
 */
public final class CollapsingOrbEvent extends ScriptedEvent {

    private static final Color ORB_COLOR = Color.fromRGB(180, 90, 255);

    private final int windowTicks;
    private final double orbHealth;
    private final double pullPerTick;
    private final double detonationDamage;
    private final double detonationRadius;
    private final double placementFraction;
    private final double orbHeight;

    private ArenaTotem orb;
    private Location orbAt;
    private boolean destroyed;

    public CollapsingOrbEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        super(plugin, bossId, triggers);
        this.windowTicks = configInt("orb-window-ticks", 220);
        this.orbHealth = configDouble("orb-health", 70.0);
        this.pullPerTick = configDouble("orb-pull-per-tick", 0.045);
        this.detonationDamage = configDouble("orb-detonation-damage", 38.0);
        this.detonationRadius = configDouble("orb-detonation-radius", 22.0);
        this.placementFraction = configDouble("orb-placement-fraction", 0.4);
        this.orbHeight = configDouble("orb-height", 2.6);
    }

    @Override
    public String id() {
        return "collapsing_orb";
    }

    @Override
    public boolean blocksCombat() {
        return false;
    }

    @Override
    protected int durationTicks() {
        return windowTicks;
    }

    @Override
    protected boolean begin(BossInstance instance) {
        if (combatants(instance).isEmpty()) {
            return false;
        }
        destroyed = false;
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        orbAt = surfaceSpot(instance, angle, placementFraction).add(0, orbHeight, 0);

        orb = ArenaTotem.spawn(plugin, instance, orbAt, Material.NETHER_STAR,
                Component.text("Collapsing Star", NamedTextColor.LIGHT_PURPLE),
                orbHealth, windowTicks + 40,
                broken -> destroy(instance),
                expired -> {
                },
                false);
        if (orb == null) {
            return false;
        }

        Fx.coloredBurst(orbAt, ORB_COLOR, 2.6f, 60, 1.0);
        Fx.sound(orbAt, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.2f);
        instance.showTitle(
                Component.text("STAR COLLAPSE", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Kill it before it finishes falling in", NamedTextColor.GRAY));
        return true;
    }

    @Override
    protected void tick(BossInstance instance, int ticks) {
        if (destroyed || orb == null || !orb.isValid()) {
            return;
        }
        double collapse = ticks / (double) windowTicks;

        Fx.coloredRing(orbAt, ORB_COLOR, 1.6f, 4.0 * (1.0 - collapse) + 0.8, 24, ticks * 0.22);
        if (ticks % 4 == 0) {
            Fx.burst(orbAt, Particle.PORTAL, 10, 1.2 * (1.0 - collapse) + 0.2);
            Fx.coloredRing(orbAt.clone().subtract(0, orbHeight - 0.2, 0), ORB_COLOR, 1.2f,
                    detonationRadius, 34, -ticks * 0.03);
        }
        if (ticks % 20 == 0) {
            Fx.sound(orbAt, Sound.BLOCK_BEACON_AMBIENT, 1.2f, 0.6f + 1.2f * (float) collapse);
        }

        // Everyone gets hauled in, harder the closer it is to going off.
        double strength = pullPerTick * (0.5 + collapse);
        for (Player player : combatants(instance)) {
            Vector toward = orbAt.toVector().subtract(player.getLocation().toVector()).setY(0);
            if (toward.lengthSquared() < 0.01) {
                continue;
            }
            player.setVelocity(player.getVelocity().add(toward.normalize().multiply(strength)));
        }

        if (ticks % 4 == 0) {
            showBars(instance, ticks, collapse);
        }
    }

    private void showBars(BossInstance instance, int ticks, double collapse) {
        int secondsLeft = Math.max(0, (windowTicks - ticks) / 20);
        Component text = Component.text("STAR COLLAPSE  ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("kill it", NamedTextColor.WHITE))
                .append(Component.text("   " + secondsLeft + "s to detonation",
                        collapse > 0.7 ? NamedTextColor.RED : NamedTextColor.GRAY));
        instance.mechanicBar().updateShared(MechanicBar.Owner.EVENT, instance.barViewers(), text, 1.0 - collapse,
                collapse > 0.7 ? BossBar.Color.RED : BossBar.Color.PURPLE);
    }

    private void destroy(BossInstance instance) {
        if (destroyed || isResolved()) {
            return;
        }
        destroyed = true;
        Fx.coloredBurst(orbAt, ORB_COLOR, 2.8f, 80, 1.2);
        Fx.flash(orbAt, 2);
        Fx.sound(orbAt, Sound.ENTITY_ENDER_DRAGON_HURT, 1.6f, 1.2f);
        instance.recordExposure();
        instance.showTitle(
                Component.text("STAR EXTINGUISHED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It never got to fall", NamedTextColor.GRAY));
        // The orb is dead; there is nothing left for the timer to do.
        finish(instance);
    }

    @Override
    protected void expire(BossInstance instance) {
        if (destroyed) {
            return;
        }
        Location ground = orbAt.clone().subtract(0, orbHeight - 0.2, 0);
        Fx.expandingRings(plugin, ground, Particle.PORTAL, detonationRadius, 6, 2L);
        Fx.coloredBurst(orbAt, ORB_COLOR, 3.2f, 120, 1.8);
        Fx.flash(orbAt, 4);
        Fx.sound(orbAt, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.4f);
        instance.showTitle(
                Component.text("IT COLLAPSED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Not enough damage, not fast enough", NamedTextColor.GRAY));

        for (Player player : combatants(instance)) {
            if (flatDistance(player.getLocation(), ground) <= detonationRadius) {
                hurt(instance, player, detonationDamage);
            }
        }
    }

    @Override
    protected void onCleanup(BossInstance instance) {
        if (orb != null) {
            orb.discard();
            orb = null;
        }
    }
}
