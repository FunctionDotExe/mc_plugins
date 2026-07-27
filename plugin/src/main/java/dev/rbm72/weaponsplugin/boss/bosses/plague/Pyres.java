package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;

import java.util.ArrayList;
import java.util.List;

/**
 * Real lit campfires with real, limited fuel — the only cleanse source the whole fight (batch-1 §4.3/
 * §4.4). {@code MeterConditions.inFire()} is the Infection meter's cure, and it means standing bodily in
 * the flame, which is exactly the "going somewhere specific and dangerous" the design asks for — the
 * fuel budget on top of that is what turns "walk to the fire" into "ration across the fight".
 */
final class Pyres {

    private final PlagueFight fight;
    private final List<Block> spots = new ArrayList<>();
    private final List<Double> fuelSecondsLeft = new ArrayList<>();

    Pyres(PlagueFight fight) {
        this.fight = fight;
    }

    void place() {
        if (!spots.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = Math.max(1, fight.config().num("pyre-count", 4));
        double fraction = fight.config().dbl("pyre-fraction", 0.55);
        double fuel = fight.config().dbl("pyre-fuel-seconds", 40.0);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            if (!Grief.setMechanicBlock(fight.griefContext(), block, Material.CAMPFIRE)) {
                continue;
            }
            if (block.getBlockData() instanceof Lightable lightable) {
                lightable.setLit(true);
                block.setBlockData(lightable, false);
            }
            spots.add(block);
            fuelSecondsLeft.add(fuel);
            Fx.coloredBurst(spot.clone().add(0, 0.6, 0), PlagueFight.TOXIC, 1.2f, 20, 0.4);
            Fx.sound(spot, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.6f);
        }
    }

    int activeCount() {
        int lit = 0;
        for (Block block : spots) {
            if (isLit(block)) {
                lit++;
            }
        }
        return lit;
    }

    int total() {
        return spots.size();
    }

    /** Fuel drains only while somebody is actually cleansing at it — the ration is spent, not idle upkeep. */
    void pulse(int intervalTicks) {
        double stepSeconds = intervalTicks / 20.0;
        for (int i = 0; i < spots.size(); i++) {
            Block block = spots.get(i);
            if (!isLit(block)) {
                continue;
            }
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            if (!Arena.combatants(at, 1.4).isEmpty()) {
                double left = fuelSecondsLeft.get(i) - stepSeconds;
                fuelSecondsLeft.set(i, left);
                if (left <= 0) {
                    snuff(block);
                }
            }
        }
    }

    /** P4's slow-burn: a pyre goes out on its own, independent of fuel — batch-1 §4.3's "burn out one by one". */
    void snuffOldest() {
        for (int i = 0; i < spots.size(); i++) {
            if (isLit(spots.get(i))) {
                snuff(spots.get(i));
                return;
            }
        }
    }

    private void snuff(Block block) {
        if (block.getBlockData() instanceof Lightable lightable) {
            lightable.setLit(false);
            block.setBlockData(lightable, false);
        }
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        Fx.burst(at, org.bukkit.Particle.SMOKE, 24, 0.4);
        Fx.sound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1.1f, 0.7f);
        for (var player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("A PYRE GUTTERS OUT", NamedTextColor.DARK_GREEN),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private static boolean isLit(Block block) {
        return block.getType() == Material.CAMPFIRE
                && (!(block.getBlockData() instanceof Lightable lightable) || lightable.isLit());
    }

    private Location surfaceSpot(double angleRadians, double fraction) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()));
        return spot;
    }
}
