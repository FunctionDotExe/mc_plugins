package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P4's piston platform edges: real pistons, actually triggered through real redstone rather than a
 * hand-rolled shove. Each one is wired to a lever sitting on top of it; firing flips the lever and lets
 * the server's own redstone/piston engine extend the head, which is what genuinely displaces any entity
 * standing in the space it moves into (batch-1 §5.3/§5.4) — nothing here calls
 * {@code Entity#setVelocity} at all. By this phase most of the arena is gone (see {@link Rifts}), so the
 * platform network left is small and every push matters.
 */
final class Pistons {

    private static final class Line {
        Block pistonBlock;
        Block leverBlock;
        int cooldownTicks;
        boolean extended;
    }

    private final SovereignFight fight;
    private final List<Line> lines = new ArrayList<>();

    Pistons(SovereignFight fight) {
        this.fight = fight;
    }

    void build() {
        if (!lines.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = fight.config().num("piston-count", 4);
        double fraction = fight.config().dbl("piston-fraction", 0.5);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Location centre = fight.instance().arena().center();
            BlockFace outward = cardinal(spot.toVector().subtract(centre.toVector()));

            Block pistonBlock = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            Piston pistonData = (Piston) Material.PISTON.createBlockData();
            pistonData.setFacing(outward);
            Grief.setMechanicBlock(fight.griefContext(), pistonBlock, Material.PISTON);
            pistonBlock.setBlockData(pistonData, false);

            // A real floor lever on top wires the piston to real redstone: flipping it is what makes
            // the extend genuine rather than a scripted animation.
            Block leverBlock = pistonBlock.getRelative(BlockFace.UP);
            Switch leverData = (Switch) Material.LEVER.createBlockData();
            leverData.setFace(Switch.Face.FLOOR);
            leverData.setFacing(outward);
            Grief.setMechanicBlock(fight.griefContext(), leverBlock, Material.LEVER);
            leverBlock.setBlockData(leverData, false);

            Line line = new Line();
            line.pistonBlock = pistonBlock;
            line.leverBlock = leverBlock;
            line.cooldownTicks = fight.config().num("piston-first-delay-ticks", 40) + i * 20;
            lines.add(line);
        }
    }

    void pulse(int intervalTicks) {
        for (Line line : lines) {
            line.cooldownTicks -= intervalTicks;
            if (line.cooldownTicks > 0) {
                continue;
            }
            if (line.extended) {
                retract(line);
                line.cooldownTicks = fight.config().num("piston-interval-ticks", 100);
            } else {
                extend(line);
                line.cooldownTicks = fight.config().num("piston-extended-ticks", 12);
            }
        }
    }

    /** Flips the lever on: real redstone power, so the server's own piston engine does the extending. */
    private void extend(Line line) {
        line.extended = true;
        setLeverPowered(line, true);
        Fx.sound(line.pistonBlock.getLocation().add(0.5, 1, 0.5), Sound.BLOCK_PISTON_EXTEND, 1.3f, 0.9f);
    }

    private void retract(Line line) {
        line.extended = false;
        setLeverPowered(line, false);
        Fx.sound(line.pistonBlock.getLocation().add(0.5, 1, 0.5), Sound.BLOCK_PISTON_CONTRACT, 1.1f, 1.0f);
    }

    private void setLeverPowered(Line line, boolean powered) {
        if (!(line.leverBlock.getBlockData() instanceof Switch current)) {
            return;
        }
        current.setPowered(powered);
        // applyPhysics = true is the whole point here: it is what fires the real redstone update that
        // the piston listens for, exactly as if a player had flipped this lever by hand.
        line.leverBlock.setBlockData(current, true);
    }

    /** Nearest cardinal direction from the arena centre to this piston, since a real piston only faces N/S/E/W/U/D. */
    private static BlockFace cardinal(org.bukkit.util.Vector outward) {
        double ax = Math.abs(outward.getX());
        double az = Math.abs(outward.getZ());
        if (ax >= az) {
            return outward.getX() >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return outward.getZ() >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
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

    void discard() {
        lines.clear();
    }
}
