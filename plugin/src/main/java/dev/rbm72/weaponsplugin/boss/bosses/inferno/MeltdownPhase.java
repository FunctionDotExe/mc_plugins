package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * <b>P4 — Meltdown</b> (§1.3, &lt;20%). Almost everything goes under in one dramatic final flood, leaving
 * exactly the platform network the group built in P2/P3 (plus a thin natural floor near the frontier) —
 * "a group that built well has a comfortable arena, a group that didn't is fighting on two tiles" is
 * enforced structurally here: {@link RisingLava#floodEverything} only ever skips
 * {@code PLAYER_MADE} tiles, so every bucket spent earlier is exactly what is still standing now.
 * <p>
 * This is the roster's mandatory ungated phase (design rule: at least one phase is a pure damage race).
 * {@link #objectiveMet()} is satisfied the instant it starts, same convention as every other boss's final
 * phase — TNT clusters and the scramble stop, but rising lava, fire trails, magma hazards, burning logs,
 * Heat Aura and Cinder Nova all keep pressuring, because §0.2 rule 5 forbids a phase that goes quiet.
 */
final class MeltdownPhase extends InfernoPhaseMechanic {

    MeltdownPhase(BossInstance instance) {
        super(instance, "Meltdown", 0.0);
    }

    @Override
    protected void onArm() {
        int players = fight.playerCount();
        fight.clusters().disarm();
        fight.trails().arm(1 + players / 2);
        fight.magma().arm(Math.max(2, 1 + players / 2));

        Location centre = instance.arena().center();
        instance.showTitle(
                Component.text("🔥 MELTDOWN 🔥", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("The floor is his now — fight on ground you made", NamedTextColor.GRAY));
        Fx.coloredBurst(centre.clone().add(0, 1.5, 0), InfernoFight.DEEP_FIRE, 2.4f, 70, 3.0);
        Fx.sound(centre, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.4f);
        Fx.sound(centre, Sound.BLOCK_LAVA_AMBIENT, 1.6f, 0.4f);

        fight.lava().floodEverything(fight.config().dbl("meltdown-keep-fraction", 0.08));
    }

    @Override
    protected void onDisarm() {
        fight.trails().disarm();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        if (elapsedTicks % 30 != 0) {
            return;
        }
        Fx.burst(instance.entity().getLocation().add(0, 1, 0), Particle.LAVA, 6, 0.5);
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
    protected Component readoutText() {
        return Component.text("burn him down on your own terrain", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return 1.0;
    }
}
