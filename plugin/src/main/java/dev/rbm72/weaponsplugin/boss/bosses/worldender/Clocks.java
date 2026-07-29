package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>P7's Convergence channel</b> — Threefold Bane's tempo (batch-5 §3, P7), used as a scheduler for
 * three of this fight's own earlier verbs rather than for the Bane's head attacks: three real redstone
 * loops, each on its own period, each re-triggering one of ice, charge or lava on its own beat. A live
 * loop's period is the number of repeaters actually sitting in it — same corrected sabotage direction as
 * the Bane's own clocks: <b>adding</b> a repeater is delay and slows a head, <b>pulling</b> one speeds it
 * up. Two or three heads firing the same tick is a convergence, punished harder than any one head alone.
 */
final class Clocks {

    private enum Head { ICE, CHARGE, LAVA }

    private static final class Clock {
        final Head head;
        Block noteBlock;
        final List<Block> slots = new ArrayList<>();
        int ticksToFire;
        int period;

        Clock(Head head) {
            this.head = head;
        }
    }

    private final WorldenderFight fight;
    private final List<Clock> clocks = new ArrayList<>();
    private boolean built;
    private int convergences;

    Clocks(WorldenderFight fight) {
        this.fight = fight;
    }

    void build() {
        if (built) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        built = true;
        int slots = Math.max(3, fight.config().num("convergence-clock-slots", 5));
        int startRepeaters = Math.max(1, fight.config().num("convergence-clock-start-repeaters", 2));
        double fraction = fight.config().dbl("convergence-clock-placement-fraction", 0.9);

        Head[] heads = Head.values();
        for (int i = 0; i < heads.length; i++) {
            Clock clock = new Clock(heads[i]);
            double angle = (Math.PI * 2 * i) / heads.length;
            Location spot = surfaceSpot(angle, fraction);
            BlockFace along = tangent(angle);

            Block noteBlock = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            Grief.setMechanicBlock(fight.griefContext(), noteBlock, Material.NOTE_BLOCK);
            clock.noteBlock = noteBlock;

            for (int slot = 0; slot < slots; slot++) {
                Block block = world.getBlockAt(
                        noteBlock.getX() + along.getModX() * (slot + 1),
                        noteBlock.getY(),
                        noteBlock.getZ() + along.getModZ() * (slot + 1));
                Material material = slot < startRepeaters ? Material.REPEATER : Material.REDSTONE_WIRE;
                if (!Grief.setMechanicBlock(fight.griefContext(), block, material)) {
                    continue;
                }
                if (material == Material.REPEATER && block.getBlockData() instanceof Repeater data) {
                    data.setFacing(along);
                    block.setBlockData(data, false);
                }
                clock.slots.add(block);
            }
            Block cap = world.getBlockAt(
                    noteBlock.getX() + along.getModX() * (slots + 1),
                    noteBlock.getY(),
                    noteBlock.getZ() + along.getModZ() * (slots + 1));
            Grief.setMechanicBlock(fight.griefContext(), cap, Material.REDSTONE_BLOCK);

            clock.period = periodOf(clock);
            clock.ticksToFire = clock.period / (i + 1);
            clocks.add(clock);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), WorldenderFight.UNMAKING, 1.6f, 26, 0.5);
        }
    }

    void pulse(int intervalTicks) {
        if (clocks.isEmpty()) {
            return;
        }
        List<Clock> firing = new ArrayList<>();
        for (Clock clock : clocks) {
            clock.period = periodOf(clock);
            clock.ticksToFire -= intervalTicks;
            if (clock.ticksToFire <= 0) {
                firing.add(clock);
            }
            drawClock(clock);
        }
        if (firing.isEmpty()) {
            return;
        }
        if (firing.size() >= 2) {
            convergences++;
        }
        for (Clock clock : firing) {
            fireHead(clock.head);
            clock.ticksToFire = Math.max(20, clock.period);
            Fx.sound(clock.noteBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.5f, pitchOf(clock));
        }
    }

    private void fireHead(Head head) {
        switch (head) {
            case ICE -> fireIce();
            case CHARGE -> fireCharge();
            case LAVA -> fireLava();
        }
    }

    private void fireIce() {
        List<Player> combatants = fight.combatants();
        if (combatants.isEmpty()) {
            return;
        }
        Player target = combatants.get(ThreadLocalRandom.current().nextInt(combatants.size()));
        Location at = target.getLocation();
        Grief.spread(fight.griefContext(), at, Material.PACKED_ICE, fight.config().dbl("convergence-ice-radius", 4.0));
        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 1));
        Fx.coloredBurst(at.clone().add(0, 1, 0), WorldenderFight.FROST, 1.8f, 30, 0.6);
        Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.7f);
    }

    private void fireCharge() {
        List<Player> combatants = fight.combatants();
        if (combatants.isEmpty()) {
            return;
        }
        Player origin = combatants.get(ThreadLocalRandom.current().nextInt(combatants.size()));
        Location at = origin.getLocation();
        double radius = fight.config().dbl("convergence-charge-radius", 5.0);
        double damage = fight.config().dbl("convergence-charge-damage", 7.0);
        origin.damage(damage, fight.instance().entity());
        for (Player nearby : dev.rbm72.weaponsplugin.boss.Arena.combatants(at, radius)) {
            if (nearby.equals(origin)) {
                continue;
            }
            nearby.damage(damage * 0.6, fight.instance().entity());
            Fx.line(at.clone().add(0, 1, 0), nearby.getLocation().clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 12);
        }
        Fx.coloredBurst(at.clone().add(0, 1, 0), WorldenderFight.STORM, 1.8f, 24, 0.5);
        Fx.sound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.3f, 1.1f);
    }

    private void fireLava() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        List<Player> combatants = fight.combatants();
        Location at = combatants.isEmpty()
                ? surfaceSpot(ThreadLocalRandom.current().nextDouble(0, Math.PI * 2), 0.6)
                : combatants.get(ThreadLocalRandom.current().nextInt(combatants.size())).getLocation();
        Block ground = world.getBlockAt(at.getBlockX(), at.getBlockY() - 1, at.getBlockZ());
        Material original = ground.getType();
        if (Grief.setBlock(fight.griefContext(), ground, Material.LAVA)) {
            Fx.coloredBurst(at.clone(), WorldenderFight.INFERNO, 1.8f, 24, 0.5);
            Fx.sound(at, Sound.BLOCK_LAVA_POP, 1.3f, 0.8f);
            for (Player nearby : dev.rbm72.weaponsplugin.boss.Arena.combatants(at, 2.5)) {
                nearby.damage(fight.config().dbl("convergence-lava-damage", 8.0), fight.instance().entity());
            }
            var revertTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (ground.getType() == Material.LAVA) {
                        Grief.setMechanicBlock(fight.griefContext(), ground, original.isAir() ? Material.AIR : original);
                    }
                }
            }.runTaskLater(fight.plugin(), fight.config().num("convergence-lava-revert-ticks", 60));
            fight.instance().trackTask(revertTask);
        }
    }

    int convergences() {
        return convergences;
    }

    Component readout() {
        return Component.text("three clocks running — " + convergences + " convergences", NamedTextColor.RED);
    }

    private int periodOf(Clock clock) {
        int base = fight.config().num("convergence-clock-base-ticks", 60)
                + clock.head.ordinal() * fight.config().num("convergence-clock-head-offset-ticks", 16);
        return base + repeaterCount(clock) * fight.config().num("convergence-clock-ticks-per-repeater", 20);
    }

    private int repeaterCount(Clock clock) {
        int count = 0;
        for (Block block : clock.slots) {
            if (block.getType() == Material.REPEATER) {
                count++;
            }
        }
        return count;
    }

    private float pitchOf(Clock clock) {
        float pitch = 1.4f - 0.12f * repeaterCount(clock);
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    private void drawClock(Clock clock) {
        if (clock.noteBlock == null) {
            return;
        }
        double fill = clock.period <= 0 ? 0 : 1.0 - Math.max(0, clock.ticksToFire) / (double) clock.period;
        Fx.coloredBurst(clock.noteBlock.getLocation().add(0.5, 1.2 + fill, 0.5), WorldenderFight.UNMAKING, 0.9f, 2, 0.1);
    }

    void discardAll() {
        clocks.clear();
        built = false;
    }

    private BlockFace tangent(double angleRadians) {
        double tangentAngle = angleRadians + Math.PI / 2;
        double dx = Math.cos(tangentAngle);
        double dz = Math.sin(tangentAngle);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private Location surfaceSpot(double angleRadians, double fraction) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }
}
