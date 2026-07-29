package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The directional pull field at arena centre — P2's one-shot Whirlpool and P3's permanent one, per
 * batch-2 §3.4 ("P2 once, P3 permanent"). Both are the same field; only {@link #startOneShot} vs
 * {@link #makePermanent} differ in whether it ever turns back off.
 * <p>
 * P3's exit condition ("three Whirlpools broken by grounding on soul-sand columns") is tracked here by
 * watching for a player who was inside the pull radius on the previous pulse and is outside it on this
 * one, while standing near a live soul-sand column — the physical signature of "rode a bubble column
 * out of the current", the only way batch-2 §3.3 says the pull is supposed to be beaten.
 */
final class Whirlpool {

    private final LeviathanFight fight;

    private boolean active;
    private boolean permanent;
    private int elapsedTicks;
    private int durationTicks;
    private boolean p2Completed;
    private int breakCount;

    private final Map<UUID, Boolean> insidePull = new HashMap<>();

    Whirlpool(LeviathanFight fight) {
        this.fight = fight;
    }

    boolean p2Completed() {
        return p2Completed;
    }

    int breakCount() {
        return breakCount;
    }

    /** P2's single dramatic whirlpool — runs for a fixed duration, then collapses on its own. */
    void startOneShot() {
        if (active) {
            return;
        }
        active = true;
        permanent = false;
        elapsedTicks = 0;
        durationTicks = Math.max(60, fight.config().num("whirlpool-oneshot-duration-ticks", 200));
        announce();
    }

    /** P3: the pull never turns off again for the rest of the phase. */
    void makePermanent() {
        active = true;
        permanent = true;
        announce();
    }

    private void announce() {
        Location centre = fight.instance().arena().center();
        Fx.coloredRing(centre, LeviathanFight.DEEP_TEAL, 1.8f, radius(), 60, 0);
        Fx.sound(centre, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.3f, 0.4f);
        fight.instance().showTitle(
                Component.text("WHIRLPOOL", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text(permanent ? "It never stops pulling — hold your ground or ride it out"
                        : "Swim clear, or ride a soul-sand column", NamedTextColor.GRAY));
    }

    private double radius() {
        return fight.instance().arena().radius() * fight.config().dbl("whirlpool-radius-fraction", 0.7);
    }

    private double eyeRadius() {
        return fight.config().dbl("whirlpool-eye-radius", 4.0);
    }

    void pulse(int intervalTicks) {
        if (!active) {
            return;
        }
        elapsedTicks += intervalTicks;
        Location centre = fight.instance().arena().center();
        double radius = radius();
        double eyeRadius = eyeRadius();

        renderSwirl(centre, radius);
        applyPull(centre, radius, eyeRadius);

        if (!permanent && elapsedTicks >= durationTicks) {
            collapse(centre);
        }
    }

    private void renderSwirl(Location centre, double radius) {
        double angle = elapsedTicks * 0.15;
        double r = radius * (0.5 + 0.5 * Math.abs(Math.cos(elapsedTicks * 0.03)));
        Fx.ring(centre, Particle.BUBBLE, r, 40, angle);
        Fx.ring(centre.clone().add(0, 1.5, 0), Particle.SPLASH, r * 0.7, 30, angle + 0.7);
    }

    private void applyPull(Location centre, double radius, double eyeRadius) {
        double pullStrength = fight.config().dbl("whirlpool-pull-strength", 0.16);
        double eyeDamage = fight.config().dbl("whirlpool-eye-damage-per-second", 4.0);
        double soulSandGroundingRadius = fight.config().dbl("whirlpool-grounding-radius", 3.0);

        Set<UUID> herePlayers = new HashSet<>();
        int inEye = 0;
        for (Player player : fight.combatants()) {
            UUID id = player.getUniqueId();
            herePlayers.add(id);
            double dist = flatDistance(player.getLocation(), centre);
            boolean isInside = dist <= radius;

            boolean wasInside = insidePull.getOrDefault(id, false);
            if (permanent && wasInside && !isInside
                    && fight.columns().isNearSoulSandColumn(player.getLocation(), soulSandGroundingRadius)) {
                registerBreak(player);
            }
            insidePull.put(id, isInside);

            if (!isInside || dist < 1.0e-3) {
                continue;
            }
            Vector toCentre = centre.toVector().subtract(player.getLocation().toVector()).setY(0).normalize();
            player.setVelocity(player.getVelocity().add(toCentre.multiply(pullStrength)));
            if (dist <= eyeRadius) {
                inEye++;
            }
        }
        insidePull.keySet().retainAll(herePlayers);

        if (elapsedTicks % 20 != 0 || inEye == 0) {
            return;
        }
        // "More bodies = more collisions" (batch-2 §3.4) — a small per-player bonus once two or more
        // are caught in the eye at once, rather than simulating real entity collision physics.
        double collisionBonus = inEye >= 2 ? fight.config().dbl("whirlpool-collision-bonus", 2.0) : 0.0;
        for (Player player : fight.combatants()) {
            if (flatDistance(player.getLocation(), centre) <= eyeRadius) {
                player.damage(eyeDamage + collisionBonus, fight.instance().entity());
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), LeviathanFight.DEEP_TEAL, 1.4f, 16, 0.4);
            }
        }
    }

    private void registerBreak(Player player) {
        breakCount++;
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), LeviathanFight.PALE, 2.0f, 40, 0.6);
        Fx.sound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.4f, 1.2f);
        int required = breaksRequired();
        for (Player viewer : fight.combatants()) {
            fight.plugin().actionBarHub().flash(viewer,
                    Component.text("WHIRLPOOL BROKEN " + Math.min(breakCount, required) + "/" + required,
                            NamedTextColor.AQUA),
                    2400L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    int breaksRequired() {
        return Math.max(1, fight.config().num("whirlpool-breaks-required", 3));
    }

    private void collapse(Location centre) {
        active = false;
        p2Completed = true;
        insidePull.clear();
        Fx.coloredBurst(centre.clone().add(0, 1, 0), LeviathanFight.PALE, 2.4f, 60, 0.9);
        Fx.burst(centre.clone().add(0, 1, 0), Particle.SPLASH, 40, 1.2);
        Fx.sound(centre, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
    }

    void discardAll() {
        active = false;
        permanent = false;
        insidePull.clear();
    }

    private static double flatDistance(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
