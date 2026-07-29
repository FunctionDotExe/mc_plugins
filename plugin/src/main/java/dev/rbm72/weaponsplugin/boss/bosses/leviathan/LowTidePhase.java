package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * <b>P4 — Low Tide.</b> Not a {@link LeviathanPhaseMechanic} — deliberately. Design rule #2 (every boss
 * gets one genuinely ungated phase, a pure damage race) and batch-2's own description of this phase
 * ("Punishes: nothing new — this phase is deliberately a release") point at the same answer: P1-P3 each
 * carry a real non-health exit condition, so P4 is where the roster's mandatory ungated phase belongs.
 * {@link dev.rbm72.weaponsplugin.boss.bosses.TideLeviathan} therefore wires it with the plain five-
 * argument {@code BossPhase} constructor — no {@code mechanicFactory}, so the boss is fully hittable
 * from the instant it enters this band.
 * <p>
 * "Beaching" (batch-2 §3.4) still has to happen as a physical, visible event, though, so this class is
 * the static helper {@link dev.rbm72.weaponsplugin.boss.bosses.TideLeviathan}'s {@code onEnter} hook
 * calls: it drains the arena in one dramatic sweep (driven by its own short repeating task, since there
 * is no running {@link LeviathanPhaseMechanic} left to piggyback a pulse on), leaves a scattering of
 * residual puddles behind (the "leftover water pockets" flavour line), and slows/enlarges the beached
 * Leviathan so the grounded brawl reads as a wounded animal rather than the same boss with the water
 * gone.
 */
public final class LowTidePhase {

    private LowTidePhase() {
    }

    public static void beach(BossInstance instance) {
        LeviathanFight fight = LeviathanFight.of(instance);
        Location at = instance.entity().getLocation();

        instance.empower(fight.config().dbl("low-tide-scale-multiplier", 1.15),
                fight.config().dbl("low-tide-speed-multiplier", 0.7));

        Fx.coloredBurst(at.clone().add(0, 1.4, 0), LeviathanFight.PALE, 2.6f, 70, 1.0);
        Fx.burst(at.clone().add(0, 1.2, 0), Particle.SPLASH, 60, 1.4);
        Fx.sound(at, Sound.ENTITY_GENERIC_BIG_FALL, 1.2f, 0.6f);
        Fx.sound(at, Sound.ENTITY_ELDER_GUARDIAN_FLOP, 1.4f, 0.7f);
        instance.showTitle(
                Component.text("LOW TIDE", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("The water is gone — it's beached. Finish it.", NamedTextColor.GRAY));

        fight.water().beginDrain();
        int budget = fight.config().num("water-drain-blocks-per-pulse", 1800);
        var task = instance.plugin().getServer().getScheduler().runTaskTimer(instance.plugin(), () -> {
            if (fight.water().drainComplete()) {
                return;
            }
            fight.water().pulse(budget);
        }, 5L, 5L);
        instance.trackTask(task);
    }
}
