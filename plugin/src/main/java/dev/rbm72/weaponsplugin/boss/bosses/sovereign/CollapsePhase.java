package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * <b>P2 — Collapse.</b> Void Rifts start opening — real floor deletion, permanent for the rest of the
 * fight (see {@link Rifts}) — and a Singularity periodically drags the group toward the centre before
 * bursting. Batch-1 §5.3: "the group has to consciously preserve a fighting platform".
 * <p>
 * Exit requires one full Singularity cycle completed on top of the health threshold.
 */
final class CollapsePhase extends SovereignPhaseMechanic {

    private int riftCountdown;
    private int singularityCountdown;
    private int pullTicksLeft;
    private int cyclesSurvived;

    CollapsePhase(BossInstance instance, double exitFraction) {
        super(instance, "Collapse", exitFraction);
    }

    @Override
    protected void onArm() {
        riftCountdown = fight.config().num("rift-first-delay-ticks", 60);
        singularityCountdown = fight.config().num("singularity-interval-ticks", 260);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        riftCountdown -= intervalTicks;
        if (riftCountdown <= 0 && fight.rifts().opened() < fight.rifts().targetCount() * 3) {
            riftCountdown = fight.config().num("rift-interval-ticks", 140);
            double radius = fight.config().dbl("rift-radius", 2.5)
                    + Math.min(3.0, fight.rifts().opened() * 0.2);
            fight.rifts().open(fight.rifts().randomSpot(), radius);
        }

        if (pullTicksLeft > 0) {
            pull(intervalTicks);
            return;
        }
        singularityCountdown -= intervalTicks;
        if (singularityCountdown <= 0) {
            pullTicksLeft = fight.config().num("singularity-pull-ticks", 60);
            instance.showTitle(
                    Component.text("SINGULARITY", NamedTextColor.DARK_PURPLE),
                    Component.text("Run, or anchor behind cover", NamedTextColor.GRAY));
        }
    }

    private void pull(int intervalTicks) {
        Location centre = instance.arena().center();
        double pullStrength = fight.config().dbl("singularity-pull-strength", 0.12);
        for (Player player : fight.combatants()) {
            Vector inward = centre.toVector().subtract(player.getLocation().toVector()).setY(0);
            if (inward.lengthSquared() > 0.01) {
                player.setVelocity(player.getVelocity().add(inward.normalize().multiply(pullStrength)));
            }
        }
        Fx.coloredRing(centre, SovereignFight.VOID_BLACK, 1.6f, 6.0, 24, elapsedTicks * 0.1);
        pullTicksLeft -= intervalTicks;
        if (pullTicksLeft <= 0) {
            resolveSingularity(centre);
        }
    }

    private void resolveSingularity(Location centre) {
        double damage = fight.config().dbl("singularity-burst-damage", 12.0);
        double radius = fight.config().dbl("singularity-burst-radius", 5.0);
        Fx.coloredBurst(centre.clone().add(0, 1, 0), SovereignFight.VOID_BLACK, 3.0f, 80, 1.2);
        Fx.burst(centre.clone().add(0, 1, 0), Particle.SQUID_INK, 60, 1.0);
        Fx.sound(centre, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.5f);
        for (Player player : Arena.combatants(centre, radius)) {
            player.damage(damage, instance.entity());
        }
        singularityCountdown = fight.config().num("singularity-interval-ticks", 260);
        cyclesSurvived++;
    }

    @Override
    protected boolean objectiveMet() {
        return cyclesSurvived >= 1;
    }

    @Override
    protected int progressSignal() {
        return cyclesSurvived;
    }

    @Override
    protected Component readoutText() {
        return cyclesSurvived >= 1
                ? Component.text("the pull has passed, for now", NamedTextColor.GREEN)
                : Component.text(fight.rifts().opened() + " rifts open — survive the pull", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, cyclesSurvived);
    }
}
