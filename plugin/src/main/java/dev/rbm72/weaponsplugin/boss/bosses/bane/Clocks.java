package dev.rbm72.weaponsplugin.boss.bosses.bane;

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

import java.util.ArrayList;
import java.util.List;

/**
 * The Threefold Bane's three redstone clocks — real loops of repeaters and note blocks at the arena
 * edge, one per head, each running on its own period (batch-3 §2.2). Everything about the fight's timing
 * is read off those blocks every pulse: the period of a head <em>is</em> the number of repeaters
 * currently sitting in its loop, so the machinery players can see and the schedule that actually fires
 * are the same object. §2.8 names that as the boss's non-negotiable implementation constraint, and
 * reading the world rather than a mirrored counter is the only way to be sure of it.
 * <p>
 * <b>Sabotage direction, and the one place this departs from the spec's wording.</b> §2.4 says "pull a
 * repeater to slow a head". In real redstone a repeater is <em>delay</em>: pulling one makes a loop
 * shorter and therefore faster. Honouring the sentence literally would mean a clock that visibly loses
 * delay and audibly slows down, which is exactly the lie §2.8 forbids. So the verb is preserved and the
 * sign is corrected — <b>add</b> a repeater to slow a head, <b>pull</b> one to speed it up. Repeaters are
 * arena-supplied (§0.3), the note block's pitch drops as the loop lengthens, and pulling one is now a
 * real and tempting mistake rather than a free win.
 */
final class Clocks {

    /** One head per clock, in the order they are built around the arena. */
    enum Head {
        LEFT("Left Head", Sound.BLOCK_NOTE_BLOCK_BASS),
        CENTRE("Centre Head", Sound.BLOCK_NOTE_BLOCK_BELL),
        RIGHT("Right Head", Sound.BLOCK_NOTE_BLOCK_PLING);

        final String label;
        final Sound note;

        Head(String label, Sound note) {
            this.label = label;
            this.note = note;
        }
    }

    private static final class Clock {
        final Head head;
        Block noteBlock;
        final List<Block> slots = new ArrayList<>();
        int ticksToFire;
        int period;
        boolean locked;
        boolean beatWarned;

        Clock(Head head) {
            this.head = head;
        }
    }

    private final BaneFight fight;
    private final HeadAttacks attacks;
    private final List<Clock> clocks = new ArrayList<>();

    private boolean built;
    private boolean convergencePending;
    private int convergences;
    private int desyncsBroken;
    private int repairCooldownTicks;
    private boolean repairing;

    Clocks(BaneFight fight) {
        this.fight = fight;
        this.attacks = new HeadAttacks(fight);
    }

    // ------------------------------------------------------------------ build

    void build() {
        if (built) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        built = true;
        int slots = Math.max(3, fight.config().num("clock-slots", 6));
        int startRepeaters = Math.max(1, fight.config().num("clock-start-repeaters", 2));
        double fraction = fight.config().dbl("clock-placement-fraction", 0.9);

        for (int i = 0; i < Head.values().length; i++) {
            Head head = Head.values()[i];
            Clock clock = new Clock(head);
            double angle = (Math.PI * 2 * i) / Head.values().length;
            Location spot = surfaceSpot(angle, fraction);

            // The loop runs tangentially so it reads as a line of machinery rather than a spoke pointing
            // at the boss, and so a player standing at one clock is never standing in another's firing lane.
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
            // A redstone block caps the run so the loop reads as powered machinery, not a row of parts.
            Block cap = world.getBlockAt(
                    noteBlock.getX() + along.getModX() * (slots + 1),
                    noteBlock.getY(),
                    noteBlock.getZ() + along.getModZ() * (slots + 1));
            Grief.setMechanicBlock(fight.griefContext(), cap, Material.REDSTONE_BLOCK);

            clock.period = periodOf(clock);
            // Deliberately staggered starts: three clocks that began together would converge on beat one
            // and teach nothing about drift.
            clock.ticksToFire = clock.period / (i + 1);
            clocks.add(clock);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), BaneFight.SOUL_BLUE, 1.6f, 26, 0.5);
            Fx.sound(spot, head.note, 1.2f, 1.0f);
        }
    }

    // ---------------------------------------------------------------- ticking

    void pulse(int intervalTicks) {
        if (clocks.isEmpty()) {
            return;
        }
        int warnWindow = fight.config().num("convergence-warn-ticks", 30);
        List<Clock> firing = new ArrayList<>();
        int near = 0;

        for (Clock clock : clocks) {
            if (!clock.locked) {
                clock.period = periodOf(clock);
            }
            clock.ticksToFire -= intervalTicks;
            if (clock.ticksToFire <= 0) {
                firing.add(clock);
            } else if (clock.ticksToFire <= warnWindow) {
                near++;
                if (!clock.beatWarned) {
                    clock.beatWarned = true;
                    beatWarning(clock);
                }
            }
            drawClock(clock);
        }

        if (!firing.isEmpty()) {
            resolveFiring(firing, near);
            return;
        }
        if (!convergencePending && near >= 2) {
            convergencePending = true;
            announceConvergence();
        } else if (convergencePending && near < 2) {
            // The alignment came apart before it landed — that is a desync, and it is P3's exit condition.
            convergencePending = false;
            desyncsBroken++;
            announceDesync();
        }
        pulseRepair(intervalTicks);
    }

    private void resolveFiring(List<Clock> firing, int near) {
        if (firing.size() >= 2) {
            convergences++;
            convergencePending = false;
            attacks.convergence(firing.size());
        } else {
            fireHead(firing.get(0));
            if (convergencePending && near < 2) {
                convergencePending = false;
                desyncsBroken++;
                announceDesync();
            }
        }
        for (Clock clock : firing) {
            clock.ticksToFire = Math.max(20, clock.period);
            clock.beatWarned = false;
            Fx.sound(clock.noteBlock.getLocation(), clock.head.note, 1.6f, pitchOf(clock));
        }
    }

    private void fireHead(Clock clock) {
        switch (clock.head) {
            case LEFT -> attacks.skullVolley();
            case CENTRE -> attacks.decayNova();
            case RIGHT -> attacks.tripleGaze();
        }
    }

    /** A beat's warning, played on that head's own note block — this fight's real telegraph is audio. */
    private void beatWarning(Clock clock) {
        Fx.sound(clock.noteBlock.getLocation(), clock.head.note, 0.8f, pitchOf(clock) * 0.75f);
        Fx.coloredBurst(clock.noteBlock.getLocation().add(0.5, 1.2, 0.5), BaneFight.SOUL_BLUE, 1.0f, 8, 0.2);
    }

    private void announceConvergence() {
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("CONVERGENCE — get behind cover", NamedTextColor.RED),
                    2000L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        Fx.sound(fight.instance().arena().center(), Sound.ENTITY_WITHER_AMBIENT, 1.6f, 0.5f);
    }

    private void announceDesync() {
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("DESYNCED", NamedTextColor.GREEN),
                    1600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    // ---------------------------------------------------------------- repair

    /**
     * P3's tug-of-war: the Bane reaches out and pulls the group's added repeaters back out, speeding its
     * own clocks up again. Only ever removes repeaters beyond the loop's original count, so it can never
     * grind a clock below the tempo it started the fight at.
     */
    private void pulseRepair(int intervalTicks) {
        if (!repairing) {
            return;
        }
        repairCooldownTicks -= intervalTicks;
        if (repairCooldownTicks > 0) {
            return;
        }
        repairCooldownTicks = fight.config().num("clock-repair-interval-ticks", 240);
        int startRepeaters = Math.max(1, fight.config().num("clock-start-repeaters", 2));
        for (Clock clock : clocks) {
            if (repeaterCount(clock) <= startRepeaters) {
                continue;
            }
            for (int i = clock.slots.size() - 1; i >= 0; i--) {
                Block block = clock.slots.get(i);
                if (block.getType() != Material.REPEATER) {
                    continue;
                }
                Grief.setMechanicBlock(fight.griefContext(), block, Material.REDSTONE_WIRE);
                Fx.burst(block.getLocation().add(0.5, 0.4, 0.5), Particle.SMOKE, 16, 0.3);
                Fx.sound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.2f, 0.6f);
                for (Player player : fight.combatants()) {
                    fight.plugin().actionBarHub().flash(player,
                            Component.text("IT PULLED A REPEATER — " + clock.head.label + " speeds up",
                                    NamedTextColor.RED),
                            2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
                }
                return;
            }
        }
    }

    void startRepairing() {
        repairing = true;
        repairCooldownTicks = fight.config().num("clock-repair-first-delay-ticks", 200);
    }

    /**
     * P4: the machinery is destroyed and every clock locks at whatever period the group left it on — the
     * finale is literally the tempo they engineered (§2.3).
     */
    void lockAll() {
        repairing = false;
        for (Clock clock : clocks) {
            // No separate stored period: pulse() stops recomputing period once locked, so the value
            // already in clock.period *is* the locked tempo.
            clock.locked = true;
            for (Block block : clock.slots) {
                Fx.burst(block.getLocation().add(0.5, 0.4, 0.5), Particle.SMOKE, 6, 0.2);
            }
        }
    }

    // ---------------------------------------------------------------- queries

    int convergences() {
        return convergences;
    }

    int desyncsBroken() {
        return desyncsBroken;
    }

    boolean convergencePending() {
        return convergencePending;
    }

    /** True once any head is running slower than the tempo it started on — P2's exit condition. */
    boolean anySlowed() {
        int startRepeaters = Math.max(1, fight.config().num("clock-start-repeaters", 2));
        for (Clock clock : clocks) {
            if (repeaterCount(clock) > startRepeaters) {
                return true;
            }
        }
        return false;
    }

    /** Total repeaters across all three loops — a monotonic-ish readout of how much engineering happened. */
    int totalRepeaters() {
        int total = 0;
        for (Clock clock : clocks) {
            total += repeaterCount(clock);
        }
        return total;
    }

    /** Slowest and fastest current periods, in seconds, for the mechanic bar readout. */
    double slowestSeconds() {
        double slowest = 0;
        for (Clock clock : clocks) {
            slowest = Math.max(slowest, clock.period / 20.0);
        }
        return slowest;
    }

    double fastestSeconds() {
        double fastest = Double.MAX_VALUE;
        for (Clock clock : clocks) {
            fastest = Math.min(fastest, clock.period / 20.0);
        }
        return fastest == Double.MAX_VALUE ? 0 : fastest;
    }

    // ----------------------------------------------------------------- helpers

    private int periodOf(Clock clock) {
        int base = fight.config().num("clock-base-ticks", 50)
                + clock.head.ordinal() * fight.config().num("clock-head-offset-ticks", 14);
        return base + repeaterCount(clock) * fight.config().num("clock-ticks-per-repeater", 22);
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

    /** Lower pitch the longer the loop: a slowed clock sounds slower even on a single strike. */
    private float pitchOf(Clock clock) {
        float pitch = 1.4f - 0.12f * repeaterCount(clock);
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    private void drawClock(Clock clock) {
        if (clock.noteBlock == null) {
            return;
        }
        double fill = clock.period <= 0 ? 0 : 1.0 - Math.max(0, clock.ticksToFire) / (double) clock.period;
        Fx.coloredBurst(clock.noteBlock.getLocation().add(0.5, 1.2 + fill, 0.5),
                BaneFight.SOUL_BLUE, 0.9f, 2, 0.1);
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
