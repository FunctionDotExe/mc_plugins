package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

/**
 * <b>P2 — Deny the Perch.</b> Batch-2 §4.3: it starts using perches to heal in earnest, and the group's
 * own grief is the counterplay. Every cycle it visibly banks toward one named, standing pillar; reach
 * it and break it before the dragon arrives and the landing never happens.
 * <p>
 * Exit: 76–50% HP, and the perch denied three times <em>in a row</em>. Deliberately a streak rather
 * than a running total — a group that denies two, then lets one heal through, then denies one more has
 * not proven it can shut the mechanic down reliably, and letting that count the same as three straight
 * denials would let a sloppy group coast through on the health band alone. A landing that is only
 * <em>attempted</em> against no standing pillar at all (every one already broken) still counts as a
 * denial — there is nothing left to fail at reaching.
 * <p>
 * Solo (§4.5): nothing about the race itself is softened — the pillar count, the telegraph length and
 * the heal are all unchanged — but with only one player, denial is a straight foot-race against the
 * dragon's approach rather than a coordination problem, which is exactly the "genuinely predictive
 * play" the design calls for either way.
 */
public final class DenyThePerchPhase extends DragonPhaseMechanic {

    private enum Stage { IDLE, APPROACHING }

    private static final int DENIALS_REQUIRED = 3;

    private Stage stage = Stage.IDLE;
    private int ticksLeft;
    private PerchPillars.Pillar targetPillar;
    private PerchPillars.Pillar lastTargeted;

    private int consecutiveDenials;
    private int totalDenials;

    public DenyThePerchPhase(BossInstance instance, double exitFraction) {
        super(instance, "Deny the Perch", exitFraction);
    }

    @Override
    protected void onArm() {
        // A grounded window's countdown lives on the previous phase's mechanic instance, which is gone
        // the moment a phase change happens — force a clean, known aerial state on entry rather than
        // risk inheriting a state with no timer left to end it.
        fight.aerial().enterCircling();
        fight.pillars().build();
        fight.fireballs().restart(fight.config().num("perch-fireball-interval-ticks", 75));
        stage = Stage.IDLE;
        ticksLeft = fight.config().num("perch-first-approach-delay-ticks", 100);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        switch (stage) {
            case IDLE -> {
                ticksLeft -= intervalTicks;
                if (ticksLeft <= 0 && !isGrounded()) {
                    beginApproach();
                }
            }
            case APPROACHING -> {
                drawTelegraph();
                if (!fight.pillars().isStanding(targetPillar)) {
                    denyLanding();
                    return;
                }
                if (fight.aerial().arrivedAtPerch(fight.config().dbl("perch-arrival-distance", 2.6))) {
                    succeedLanding();
                    return;
                }
                ticksLeft -= intervalTicks;
                if (ticksLeft <= 0) {
                    // Stalled approach (arena geometry, an obstruction) — resolve as a landing rather
                    // than hang the cycle forever with the pillar still standing.
                    succeedLanding();
                }
            }
        }
    }

    private void beginApproach() {
        targetPillar = fight.pillars().pickTarget(lastTargeted);
        if (targetPillar == null) {
            // Every pillar already gone: there is nothing left to land on, which is itself a denial.
            registerDenial();
            stage = Stage.IDLE;
            ticksLeft = fight.config().num("perch-cycle-ticks", 220);
            return;
        }
        lastTargeted = targetPillar;
        fight.aerial().enterPerchApproach(targetPillar.top);
        stage = Stage.APPROACHING;
        ticksLeft = fight.config().num("perch-approach-timeout-ticks", 260);
        instance.showTitle(
                Component.text("IT'S BANKING TOWARD A PILLAR", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("Reach it and break it before it lands", NamedTextColor.GRAY));
        Fx.sound(targetPillar.top, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.1f, 0.8f);
    }

    private void drawTelegraph() {
        if (targetPillar == null) {
            return;
        }
        Telegraph.line(instance.entity().getLocation(), targetPillar.top, Particle.FLAME);
        Telegraph.dangerZone(targetPillar.top, 2.2);
    }

    private void denyLanding() {
        registerDenial();
        stage = Stage.IDLE;
        ticksLeft = fight.config().num("perch-cycle-ticks", 220);
        fight.aerial().enterCircling();
        instance.showTitle(
                Component.text("PERCH DENIED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text(consecutiveDenials + "/" + DENIALS_REQUIRED + " in a row", NamedTextColor.GRAY));
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.3f);
    }

    private void registerDenial() {
        consecutiveDenials++;
        totalDenials++;
    }

    private void succeedLanding() {
        consecutiveDenials = 0;
        stage = Stage.IDLE;
        ticksLeft = fight.config().num("perch-cycle-ticks", 220);
        heal();
        instance.showTitle(
                Component.text("IT LANDS AND HEALS", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Denial streak reset — catch the next one", NamedTextColor.GRAY));
        enterGroundedWindow(fight.config().num("perch-grounded-window-ticks", 110));
    }

    private void heal() {
        AttributeInstance maxAttr = instance.entity().getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : instance.entity().getHealth();
        double healFraction = fight.config().dbl("perch-heal-fraction", 0.07);
        double newHealth = Math.min(max, instance.entity().getHealth() + max * healFraction);
        instance.entity().setHealth(newHealth);
        Fx.coloredBurst(instance.entity().getLocation().add(0, 1.4, 0), DragonFight.EMBER, 2.2f, 50, 0.8);
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.6f);
    }

    @Override
    protected boolean objectiveMet() {
        return consecutiveDenials >= DENIALS_REQUIRED;
    }

    @Override
    protected int progressSignal() {
        return totalDenials;
    }

    @Override
    protected Component readoutText() {
        return Component.text("perches denied " + Math.min(consecutiveDenials, DENIALS_REQUIRED) + "/" + DENIALS_REQUIRED + " in a row",
                objectiveMet() ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.text("   pillars " + fight.pillars().standingCount() + "/" + fight.pillars().all().size() + " standing",
                        NamedTextColor.GRAY));
    }

    @Override
    protected double readoutProgress() {
        return (double) Math.min(consecutiveDenials, DENIALS_REQUIRED) / DENIALS_REQUIRED;
    }
}
