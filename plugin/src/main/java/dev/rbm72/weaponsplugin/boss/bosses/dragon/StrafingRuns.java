package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P3's Strafing Run: batch-2 §4 — "it visibly lines up at one arena edge with a clear run-up ... get
 * out of the lane, or take cover behind a surviving pillar ... a lane of real fire that persists."
 * <p>
 * Owned by {@code DiveBombPhase} rather than fight-scoped, because it is P3-only — pillars, the
 * membranes and the fireball spawner all outlive a phase change; this does not.
 * <p>
 * Cover is deliberately a simple proximity check against a standing {@link PerchPillars.Pillar} rather
 * than a true line-of-sight raycast — consistent with how {@code Duel}'s Ring Sweep or Storm's flooding
 * check use flat annulus/proximity tests instead of hitbox geometry. "Are you near a pillar that's still
 * standing" is the read the design asks for ("cover is whatever pillars survived P2"), not "is the
 * pillar's exact silhouette between you and the flight path".
 */
final class StrafingRuns {

    private enum Stage { IDLE, TELEGRAPH, RUNNING }

    private final DragonFight fight;

    private Stage stage = Stage.IDLE;
    private int ticksLeft;
    private Location fromEdge;
    private Location toEdge;
    private Location lastCheckPoint;
    private final Set<UUID> hitThisRun = new HashSet<>();

    private int runsCompleted;

    StrafingRuns(DragonFight fight) {
        this.fight = fight;
        this.ticksLeft = fight.config().num("strafe-cycle-ticks", 140);
    }

    int runsCompleted() {
        return runsCompleted;
    }

    /** Flavour-only headcount from batch-2 §4.4 ("run count = 3 + 1 per 2 players") — not a gate. */
    int plannedTotalRuns() {
        int players = fight.playerCount();
        return Math.max(3, 3 + (players - 1) / 2);
    }

    boolean isMidRun() {
        return stage != Stage.IDLE;
    }

    void pulse(int intervalTicks) {
        switch (stage) {
            case IDLE -> {
                ticksLeft -= intervalTicks;
                if (ticksLeft <= 0) {
                    beginTelegraph();
                }
            }
            case TELEGRAPH -> {
                drawTelegraph();
                ticksLeft -= intervalTicks;
                if (ticksLeft <= 0) {
                    beginRun();
                }
            }
            case RUNNING -> {
                checkDamageAlongPath();
                if (fight.aerial().arrivedAtStrafeEnd(fight.config().dbl("strafe-arrival-distance", 3.0))) {
                    completeRun();
                } else {
                    ticksLeft -= intervalTicks;
                    if (ticksLeft <= 0) {
                        // Safety valve: something (arena geometry, a stuck velocity) kept it from ever
                        // reporting arrival. The run still counts as survived — nothing about a stalled
                        // dash should hold P3 open forever.
                        completeRun();
                    }
                }
            }
        }
    }

    private void beginTelegraph() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double altitude = fight.config().dbl("strafe-altitude", 5.0);
        fromEdge = edgeSpot(angle, altitude);
        toEdge = edgeSpot(angle + Math.PI, altitude);
        stage = Stage.TELEGRAPH;
        ticksLeft = Math.max(15, fight.config().num("strafe-telegraph-ticks", 50));
        hitThisRun.clear();

        fight.instance().showTitle(
                Component.text("STRAFING RUN", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("It's lining up along the wall — clear the lane or find a pillar", NamedTextColor.GRAY));
        Fx.sound(fromEdge, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.3f, 0.7f);
    }

    private void drawTelegraph() {
        if (fromEdge == null || toEdge == null) {
            return;
        }
        Telegraph.line(fromEdge, toEdge, Particle.FLAME);
        Telegraph.dangerZone(fromEdge, 2.0);
    }

    private void beginRun() {
        if (fromEdge == null || toEdge == null) {
            stage = Stage.IDLE;
            ticksLeft = fight.config().num("strafe-cycle-ticks", 140);
            return;
        }
        fight.aerial().enterStrafingRun(fromEdge, toEdge);
        lastCheckPoint = fromEdge.clone();
        stage = Stage.RUNNING;
        ticksLeft = fight.config().num("strafe-timeout-ticks", 100);
        Fx.sound(fromEdge, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 0.9f);
    }

    private void checkDamageAlongPath() {
        Location bossNow = fight.instance().entity().getLocation();
        double halfWidth = fight.config().dbl("strafe-half-width", 3.0);
        double damage = fight.config().dbl("strafe-damage", 16.0);
        double coverRadius = fight.config().dbl("strafe-cover-radius", 2.5);

        for (Player player : fight.combatants()) {
            if (hitThisRun.contains(player.getUniqueId())) {
                continue;
            }
            if (flat(player.getLocation(), bossNow) > halfWidth) {
                continue;
            }
            hitThisRun.add(player.getUniqueId());
            if (fight.pillars().hasCoverNear(player.getLocation(), coverRadius)) {
                Fx.sound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.5f);
                continue;
            }
            player.damage(damage, fight.instance().entity());
            player.setFireTicks(Math.max(player.getFireTicks(), fight.config().num("strafe-burn-ticks", 50)));
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), DragonFight.DRAGON_RED, 1.8f, 30, 0.5);
        }

        if (lastCheckPoint != null) {
            fight.fireLanes().igniteLine(lastCheckPoint, bossNow, 1.4, fight.config().num("strafe-fire-duration-ticks", 200));
        }
        lastCheckPoint = bossNow.clone();
    }

    private void completeRun() {
        runsCompleted++;
        stage = Stage.IDLE;
        ticksLeft = fight.config().num("strafe-cycle-ticks", 140);
        fight.aerial().enterCircling();
        Fx.expandingRings(fight.plugin(), fight.instance().entity().getLocation(), Particle.LAVA, 6.0, 3, 2L);
    }

    void reset() {
        stage = Stage.IDLE;
        ticksLeft = fight.config().num("strafe-cycle-ticks", 140);
        fromEdge = null;
        toEdge = null;
        hitThisRun.clear();
    }

    private Location edgeSpot(double angleRadians, double altitude) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double radius = fight.instance().arena().radius() * 0.94;
        Location spot = centre.clone().add(Math.cos(angleRadians) * radius, 0, Math.sin(angleRadians) * radius);
        double groundY = world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ());
        spot.setY(groundY + altitude);
        return spot;
    }

    private static double flat(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
