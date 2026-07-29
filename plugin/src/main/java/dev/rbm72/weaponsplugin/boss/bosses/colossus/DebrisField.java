package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * P4's ambient hazard: "real blocks shed off it as falling debris, littering the arena with cover and
 * obstruction" (batch-2 §2.3). Unlike {@link Pillars} this isn't a dodge-this lane — it's scenery the
 * fight authors as it goes, scattered near wherever the Colossus currently is, there for players to
 * duck behind during its last arm sweeps. "Debris scales with fight length" (§2.4): the interval is
 * fixed, so a longer P4 simply sheds more of it.
 */
final class DebrisField {

    private static final Material[] MATERIALS = {
            Material.IRON_BLOCK, Material.COBBLESTONE, Material.ANDESITE, Material.GOLD_BLOCK
    };

    private final ColossusFight fight;
    private int countdown;

    DebrisField(ColossusFight fight) {
        this.fight = fight;
    }

    void reset() {
        countdown = fight.config().num("debris-first-delay-ticks", 40);
    }

    void pulse(int intervalTicks) {
        countdown -= intervalTicks;
        if (countdown > 0) {
            return;
        }
        countdown = fight.config().num("debris-interval-ticks", 70);
        shed();
    }

    private void shed() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location origin = fight.instance().entity().getLocation();
        int pieces = fight.config().num("debris-pieces-per-shed", 2);
        double spread = fight.config().dbl("debris-spread-radius", 5.0);
        double damage = fight.config().dbl("debris-damage", 8.0);
        double impactRadius = fight.config().dbl("debris-impact-radius", 1.4);
        double heightAbove = fight.config().dbl("debris-drop-height", 6.0);

        for (int i = 0; i < pieces; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(1.5, spread);
            Location cell = origin.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            if (!withinArena(cell)) {
                continue;
            }
            cell.setY(world.getHighestBlockYAt(cell.getBlockX(), cell.getBlockZ()));
            Material material = MATERIALS[ThreadLocalRandom.current().nextInt(MATERIALS.length)];
            Grief.dropAsBlock(fight.griefContext(), cell, material, heightAbove, damage, impactRadius, landed ->
                    Fx.burst(landed.clone().add(0.5, 0.4, 0.5), Particle.CLOUD, 10, 0.4));
        }
        Fx.blockBurst(origin.clone().add(0, 1.5, 0), Material.IRON_BLOCK, 16, 0.6);
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
