package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P1's ground hazard: "real falling sand/gravel pillars it collapses onto the group" (batch-2 §2.2).
 * On an interval it picks one or more lanes radiating out from wherever it is currently standing and
 * topples a line of real sand/gravel down them, cell by cell — real {@code FallingBlock} physics via
 * {@link Grief#dropAsBlock}, the same primitive the roster already uses for anvil/avalanche barrages.
 * <p>
 * Lanes are anchored to the Colossus's own live position rather than the arena's fixed centre, on
 * purpose: the flavour is it punching the ground where it stands, not a scripted arena-wide event. Each
 * cell is still clamped inside the arena radius (Pitfall 6) before it is allowed to fall, so a lane
 * rolled while the Colossus is pressed against the wall by its own leash simply runs shorter instead of
 * dropping blocks outside the fight.
 */
final class Pillars {

    private final ColossusFight fight;
    private int countdown;

    Pillars(ColossusFight fight) {
        this.fight = fight;
    }

    /** Arms the first interval. Call once from the owning phase's {@code onArm}. */
    void reset() {
        countdown = fight.config().num("pillar-first-delay-ticks", 80);
    }

    void pulse(int intervalTicks) {
        countdown -= intervalTicks;
        if (countdown > 0) {
            return;
        }
        countdown = fight.config().num("pillar-interval-ticks", 130);
        trigger();
    }

    private void trigger() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int lanes = laneCount();
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < lanes; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / lanes;
            collapseLane(angle);
        }
    }

    /** "pillar count scales" (batch-2 §2.4) — one lane solo, more as the ground crew grows. */
    private int laneCount() {
        int base = fight.config().num("pillar-lane-count-base", 1);
        int playersPerExtraLane = fight.config().num("pillar-lane-players-per-extra", 2);
        return base + (fight.playerCount() - 1) / Math.max(1, playersPerExtraLane);
    }

    private void collapseLane(double angle) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location origin = fight.instance().entity().getLocation();
        Vector direction = new Vector(Math.cos(angle), 0, Math.sin(angle));
        int length = fight.config().num("pillar-lane-length", 5);

        List<Location> cells = new ArrayList<>();
        for (int i = 2; i <= length + 1; i++) {
            Location cell = origin.clone().add(direction.clone().multiply(i));
            if (!withinArena(cell)) {
                break;
            }
            cell.setY(world.getHighestBlockYAt(cell.getBlockX(), cell.getBlockZ()));
            cells.add(cell);
        }
        if (cells.isEmpty()) {
            return;
        }

        for (Location cell : cells) {
            Fx.coloredRing(cell.clone().add(0.5, 0.2, 0.5), ColossusFight.SOLAR_GOLD, 1.1f, 1.0, 12, 0);
        }
        Fx.sound(origin, org.bukkit.Sound.ENTITY_IRON_GOLEM_ATTACK, 1.3f, 0.6f);

        int telegraphTicks = fight.config().num("pillar-telegraph-ticks", 32);
        fight.instance().trackTask(fight.plugin().getServer().getScheduler().runTaskLater(fight.plugin(),
                () -> dropSequence(cells, 0), telegraphTicks));
    }

    private void dropSequence(List<Location> cells, int index) {
        if (index >= cells.size() || !fight.instance().entity().isValid()) {
            return;
        }
        Location cell = cells.get(index);
        Material material = ThreadLocalRandom.current().nextBoolean() ? Material.SAND : Material.GRAVEL;
        double damage = fight.config().dbl("pillar-damage", 13.0);
        double radius = fight.config().dbl("pillar-impact-radius", 1.7);
        double heightAbove = fight.config().dbl("pillar-drop-height", 9.0);
        Grief.dropAsBlock(fight.griefContext(), cell, material, heightAbove, damage, radius, landed ->
                Fx.burst(landed.clone().add(0.5, 0.3, 0.5), Particle.CLOUD, 12, 0.4));

        int stagger = fight.config().num("pillar-stagger-ticks", 4);
        fight.instance().trackTask(fight.plugin().getServer().getScheduler().runTaskLater(fight.plugin(),
                () -> dropSequence(cells, index + 1), stagger));
    }

    private boolean withinArena(Location cell) {
        Location centre = fight.instance().arena().center();
        if (cell.getWorld() == null || !cell.getWorld().equals(centre.getWorld())) {
            return false;
        }
        double dx = cell.getX() - centre.getX();
        double dz = cell.getZ() - centre.getZ();
        double radius = fight.instance().arena().radius();
        return dx * dx + dz * dz <= radius * radius;
    }
}
