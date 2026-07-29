package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P3's Solar Charge: real beacons planted around the arena, each throwing a real vanilla beam straight
 * up. Batch-2 §2.3 is explicit that this is <b>never a damage gate</b> — {@code SolarChargePhase}
 * overrides {@code filterDamage} to leave every hit untouched while this runs, and nothing in this
 * class ever touches {@code setDamageMultiplier}/{@code setForcedInvulnerable}. What it threatens is
 * <em>progress</em>: a charge that completes calls {@link Joints#repairAll()} and heals the Colossus a
 * real chunk of the health the group just spent two phases grinding down.
 * <p>
 * <b>Beam-obstruction detection needs no custom physics.</b> A vanilla beacon's beam is blocked by
 * standing any solid block anywhere in the column above it — that rule already exists in the client and
 * the server rendering, so the counterplay ("place any block over the beam") requires nothing from this
 * class beyond noticing whether such a block is there. It is, functionally, "is this beacon still the
 * highest solid block in its own column" — cheap enough to poll every pulse without touching a block
 * physics/light-update event at all.
 */
final class Beacons {

    private final ColossusFight fight;
    private final List<Block> pedestals = new ArrayList<>();
    private int chargeTicksLeft;
    private int interruptsSoFar;
    private boolean running;
    /** Set once the phase (or the fight) tears this down, so a pending {@link #scheduleNextCycle} task
     *  from just before the transition can never re-arm beacons into a phase that no longer wants them. */
    private boolean discarded;

    Beacons(ColossusFight fight) {
        this.fight = fight;
    }

    void beginCycle() {
        discarded = false;
        World world = fight.world();
        if (world == null) {
            return;
        }
        clearBlocks();
        boolean solo = fight.solo();
        int count = fight.config().num("beacon-count", solo ? 2 : 4);
        double fraction = fight.config().dbl("beacon-fraction", 0.7);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block pedestal = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            Grief.setMechanicBlock(fight.griefContext(), pedestal, Material.IRON_BLOCK);
            Block beacon = pedestal.getRelative(0, 1, 0);
            Grief.setMechanicBlock(fight.griefContext(), beacon, Material.BEACON);
            pedestals.add(beacon);
            Fx.coloredBurst(beacon.getLocation().add(0.5, 1, 0.5), ColossusFight.SOLAR_GOLD, 2.0f, 40, 0.6);
            Fx.sound(beacon.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.0f);
        }
        chargeTicksLeft = fight.config().num("beacon-charge-ticks", 220);
        running = true;
        fight.instance().showTitle(
                Component.text("SOLAR CHARGE", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Block the beams — any block, over any beacon", NamedTextColor.GRAY));
    }

    void pulse(int intervalTicks) {
        if (!running) {
            return;
        }
        int blockedCount = 0;
        for (Block beacon : pedestals) {
            if (isBlocked(beacon)) {
                blockedCount++;
            } else {
                Fx.line(beacon.getLocation().add(0.5, 1, 0.5), beacon.getLocation().add(0.5, 40, 0.5),
                        Particle.END_ROD, 10);
            }
        }
        if (blockedCount >= pedestals.size() && !pedestals.isEmpty()) {
            interrupt();
            return;
        }
        chargeTicksLeft -= intervalTicks;
        if (chargeTicksLeft <= 0) {
            complete();
        }
    }

    private void interrupt() {
        running = false;
        interruptsSoFar++;
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("CHARGE INTERRUPTED", NamedTextColor.GREEN), 2200L, ActionBarHub.PRIORITY_NOTICE);
        }
        Location centre = fight.instance().arena().center();
        Fx.coloredBurst(centre.clone().add(0, 1.5, 0), ColossusFight.RADIANT_WHITE, 2.4f, 60, 1.0);
        Fx.sound(centre, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 1.2f);
        clearBlocks();
        scheduleNextCycle();
    }

    private void complete() {
        running = false;
        Location centre = fight.instance().arena().center();
        fight.instance().showTitle(
                Component.text("CHARGE COMPLETE", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("It has repaired itself", NamedTextColor.GRAY));
        Fx.coloredBurst(centre.clone().add(0, 1.5, 0), ColossusFight.CORE_RED, 3.0f, 90, 1.4);
        Fx.sound(centre, Sound.ENTITY_WITHER_SPAWN, 1.3f, 1.1f);
        fight.joints().repairAll();
        clearBlocks();
        scheduleNextCycle();
    }

    private void scheduleNextCycle() {
        int cooldown = fight.config().num("beacon-cycle-cooldown-ticks", 60);
        fight.instance().trackTask(fight.plugin().getServer().getScheduler().runTaskLater(fight.plugin(), () -> {
            if (!discarded && fight.instance().entity().isValid()) {
                beginCycle();
            }
        }, cooldown));
    }

    private boolean isBlocked(Block beacon) {
        World world = beacon.getWorld();
        if (world == null) {
            return false;
        }
        int cap = fight.config().num("beacon-obstruction-scan-height", 48);
        for (int y = beacon.getY() + 1; y < Math.min(world.getMaxHeight(), beacon.getY() + 1 + cap); y++) {
            Material type = world.getBlockAt(beacon.getX(), y, beacon.getZ()).getType();
            if (type.isSolid()) {
                return true;
            }
        }
        return false;
    }

    int interruptsSoFar() {
        return interruptsSoFar;
    }

    boolean anyLive() {
        return !pedestals.isEmpty();
    }

    int blockedCount() {
        int count = 0;
        for (Block beacon : pedestals) {
            if (isBlocked(beacon)) {
                count++;
            }
        }
        return count;
    }

    int total() {
        return pedestals.size();
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

    private void clearBlocks() {
        for (Block beacon : pedestals) {
            if (beacon.getType() == Material.BEACON) {
                Grief.setMechanicBlock(fight.griefContext(), beacon, Material.AIR);
            }
            Block pedestal = beacon.getRelative(0, -1, 0);
            if (pedestal.getType() == Material.IRON_BLOCK) {
                Grief.setMechanicBlock(fight.griefContext(), pedestal, Material.AIR);
            }
        }
        pedestals.clear();
    }

    void discardAll() {
        running = false;
        discarded = true;
        clearBlocks();
    }
}
