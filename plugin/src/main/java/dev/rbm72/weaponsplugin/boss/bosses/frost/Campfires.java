package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The four campfires the whole fight is fought around. Real, lit {@link Material#CAMPFIRE} blocks —
 * the Chill meter's cure ({@code MeterConditions.nearLitCampfire}) reads the world directly rather than
 * trusting a registry, so a snuffed fire stops curing the instant it goes out and a relit one starts
 * again the instant vanilla's own flint-and-steel interaction lights it back up. Nothing here listens
 * for that relight: it is already a real block a real tool lights, so vanilla does the whole job.
 * <p>
 * Placed once, at fight start, before P3 or P4 ever needs them — the design is explicit that the
 * rotation should be taught long before it becomes mandatory.
 */
final class Campfires {

    private final FrostFight fight;
    private final List<Block> spots = new ArrayList<>();

    Campfires(FrostFight fight) {
        this.fight = fight;
    }

    /** Idempotent: only the first call actually places anything. */
    void place() {
        if (!spots.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = Math.max(1, fight.config().num("campfire-count", 4));
        double fraction = fight.config().dbl("campfire-fraction", 0.55);
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
            Fx.coloredBurst(spot.clone().add(0, 0.6, 0), FrostFight.FROST_BLUE, 1.2f, 20, 0.4);
            Fx.sound(spot, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.9f);
        }
    }

    /** How many of the placed campfires are lit right now — the resource P4 spends down one at a time. */
    int litCount() {
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

    /**
     * Snuffs one lit campfire — P4's "the safe area shrinks campfire by campfire". Picks randomly among
     * the still-lit ones so the group can never predict which corner goes dark next.
     */
    void snuffOne() {
        List<Block> lit = new ArrayList<>();
        for (Block block : spots) {
            if (isLit(block)) {
                lit.add(block);
            }
        }
        if (lit.isEmpty()) {
            return;
        }
        Block chosen = lit.get(ThreadLocalRandom.current().nextInt(lit.size()));
        BlockData data = chosen.getBlockData();
        if (data instanceof Lightable lightable) {
            lightable.setLit(false);
            chosen.setBlockData(lightable, false);
        }
        Location at = chosen.getLocation().add(0.5, 0.5, 0.5);
        Fx.burst(at, org.bukkit.Particle.SMOKE, 30, 0.4);
        Fx.sound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.7f);
        for (var player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("A CAMPFIRE GOES OUT", NamedTextColor.AQUA),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    /** Whether {@code loc} sits within a lit campfire's radius — P4's pulse survival check. */
    boolean isNearLitCampfire(Location loc, double radius) {
        for (Block block : spots) {
            if (!isLit(block)) {
                continue;
            }
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            if (at.getWorld() != null && at.getWorld().equals(loc.getWorld())
                    && at.distanceSquared(loc) <= radius * radius) {
                return true;
            }
        }
        return false;
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
