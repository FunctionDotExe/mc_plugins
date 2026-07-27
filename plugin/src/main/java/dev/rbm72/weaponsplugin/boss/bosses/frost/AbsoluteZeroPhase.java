package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * <b>P4 — Absolute Zero.</b> The whole arena freezes in pulses; between them, only the radius of a lit
 * campfire is survivable, and she snuffs one campfire per pulse. Batch-1 §2.3: "the safe area shrinks
 * campfire by campfire" — this is the resource-scarcity endgame every earlier phase's campfire rotation
 * was rehearsing.
 * <p>
 * Objective is satisfied the instant the phase starts, exactly like every roster boss's final phase:
 * there is no next band to pin a seam against, so the only ending left is her health reaching zero.
 */
final class AbsoluteZeroPhase extends FrostPhaseMechanic {

    private int windupLeft;

    AbsoluteZeroPhase(BossInstance instance) {
        super(instance, "Absolute Zero", 0.0);
    }

    @Override
    protected void onArm() {
        windupLeft = fight.config().num("absolute-zero-first-delay-ticks", 100);
    }

    @Override
    protected boolean objectiveMet() {
        return true;
    }

    @Override
    protected boolean announcesCompletion() {
        return false;
    }

    @Override
    protected void onPulse(int intervalTicks) {
        windupLeft -= intervalTicks;
        Location centre = instance.arena().center();
        double progress = 1.0 - Math.max(0.0, windupLeft / (double) fight.config().num("absolute-zero-interval-ticks", 140));
        for (int ring = 0; ring < 2; ring++) {
            Fx.coloredRing(centre, FrostFight.FROST_BLUE, 1.2f + (float) progress, 4.0 + ring * 3.0, 24, progress * 3.0);
        }
        if (windupLeft <= 0) {
            pulse();
            windupLeft = fight.config().num("absolute-zero-interval-ticks", 140);
        }
    }

    private void pulse() {
        Location centre = instance.arena().center();
        fight.campfires().snuffOne();
        // The crack itself is a real terrain event, not a burst of particles standing in for one — see
        // IceField#surge. Everywhere it reaches keeps the "faster on ice" Chill multiplier live for
        // real, which particles alone could never do.
        fight.iceField().surge();
        Fx.burst(centre.clone().add(0, 2, 0), Particle.SNOWFLAKE, 90, 1.4);
        Fx.coloredBurst(centre.clone().add(0, 2, 0), FrostFight.PALE_ICE, 2.8f, 80, 1.5);
        Fx.sound(centre, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.6f, 0.4f);
        Fx.sound(centre, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);

        double radius = fight.config().dbl("absolute-zero-safe-radius", 4.0);
        double damage = fight.config().dbl("absolute-zero-damage", 8.0);
        double chillSpike = fight.config().dbl("absolute-zero-chill-spike", 70.0);
        for (Player player : combatants()) {
            if (fight.campfires().isNearLitCampfire(player.getLocation(), radius)) {
                continue;
            }
            player.damage(damage, instance.entity());
            fight.chill().add(player, chillSpike);
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE COLD REACHES YOU", NamedTextColor.AQUA),
                    2200L, ActionBarHub.PRIORITY_NOTICE);
        }
        instance.showTitle(
                Component.text("❄", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true),
                Component.text(fight.campfires().litCount() + "/" + fight.campfires().total() + " campfires left", NamedTextColor.GRAY));
    }

    @Override
    protected Component readoutText() {
        return Component.text(fight.campfires().litCount() + "/" + fight.campfires().total()
                + " campfires — stand in one's light", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        int total = Math.max(1, fight.campfires().total());
        return (double) fight.campfires().litCount() / total;
    }
}
