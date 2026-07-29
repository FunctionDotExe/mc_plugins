package dev.rbm72.weaponsplugin.boss.bosses.graft;

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
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Observer;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Grafted Horror's whole fight in one object: four bolted-on <b>graft modules</b>, each one a real
 * block emplacement wired to a real power source by a real, traceable, breakable redstone line
 * (batch-3 §1.2). A module only fires while its circuit is intact, so the counterplay to every attack
 * this boss has is the same physical act — walk the wire and break a segment.
 * <p>
 * <b>The one deviation from the spec's wording, and why.</b> §1.1 describes the modules as bolted onto
 * the Horror's body with the wire running down it to the floor. A block cannot be parented to a moving
 * mob, and a wire re-routed every tick to follow it would be untraceable — which would delete the
 * mechanic in order to honour its flavour text. So each module sits in a fixed floor <em>socket</em> at
 * mid-arena with a particle tether running from the Horror to it: the graft still visibly belongs to the
 * boss, and the line is a fixed path players can actually learn, walk and cut. P4's "the modules tear
 * loose" is then expressed by the tether snapping rather than by the block moving.
 * <p>
 * Cutting is a {@link BlockBreakEvent} on any dust segment or on the line's repeater. Nothing here reads
 * real redstone power — the dust is legibility, not simulation — with the deliberate exception of the
 * piston graft, whose pistons are wired to real levers so the server's own piston engine does the
 * shoving (same technique as the Void Sovereign's {@code Pistons}).
 */
final class Grafts {

    /** Grafts are added cumulatively, one per phase, and never removed (§1.3). */
    enum Kind {
        DISPENSER("Dispenser Graft", Material.DISPENSER),
        PISTON("Piston Graft", Material.PISTON),
        OBSERVER("Observer Graft", Material.OBSERVER),
        REPAIR("Repair Graft", Material.CRAFTER);

        final String label;
        final Material module;

        Kind(String label, Material module) {
            this.label = label;
            this.module = module;
        }
    }

    private static final class Graft {
        final Kind kind;
        Block socket;
        Block power;
        Block repeater;
        final List<Block> wire = new ArrayList<>();
        /** Real pistons + their levers, PISTON graft only. */
        final List<Block> pistons = new ArrayList<>();
        final List<Block> levers = new ArrayList<>();
        boolean armed;
        boolean detached;
        boolean dead;
        int severedTicks;
        int cuts;
        int cooldownTicks;
        boolean pistonsExtended;
        BlockFace facing = BlockFace.NORTH;

        Graft(Kind kind) {
            this.kind = kind;
        }

        boolean live() {
            return armed && !dead && severedTicks <= 0;
        }
    }

    private final GraftFight fight;
    private final Map<Kind, Graft> grafts = new EnumMap<>(Kind.class);
    private final Map<UUID, Location> lastSeen = new HashMap<>();

    private Handler handler;
    private int observerCooldownTicks;
    private boolean observerCutWhileWatching;
    private int repairWindowTicks;
    private int cutsInRepairWindow;

    Grafts(GraftFight fight) {
        this.fight = fight;
    }

    // ------------------------------------------------------------------ build

    /**
     * Lays one graft's circuit: the module in a socket at mid-arena, its power source out at the arena
     * edge, and a straight dust run between them with a repeater at the midpoint. Idempotent — a phase
     * re-arming a graft it already owns is a no-op, which is what lets every phase call {@code arm} for
     * the full cumulative set without tracking what the last one did.
     */
    void arm(Kind kind) {
        Graft graft = grafts.computeIfAbsent(kind, Graft::new);
        if (graft.armed) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        graft.armed = true;
        graft.cooldownTicks = fight.config().num("graft-first-delay-ticks", 60);

        int index = grafts.size() - 1;
        double angle = (Math.PI * 2 * index) / Kind.values().length
                + fight.config().dbl("graft-angle-offset-radians", 0.4);
        double socketFraction = fight.config().dbl("graft-socket-fraction", 0.45);
        double powerFraction = fight.config().dbl("graft-power-fraction", 0.95);

        Location socketSpot = surfaceSpot(angle, socketFraction);
        Location powerSpot = surfaceSpot(angle, powerFraction);
        graft.socket = world.getBlockAt(socketSpot);
        graft.power = world.getBlockAt(powerSpot);
        graft.facing = cardinalToward(socketSpot, fight.instance().arena().center());

        Grief.setMechanicBlock(fight.griefContext(), graft.power, Material.REDSTONE_BLOCK);
        placeModule(graft);
        layWire(graft, socketSpot, powerSpot);
        if (kind == Kind.PISTON) {
            raisePistons(graft);
        }

        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
        Location at = graft.socket.getLocation().add(0.5, 1.0, 0.5);
        Fx.coloredBurst(at, GraftFight.SPARK_RED, 1.8f, 34, 0.6);
        Fx.sound(at, Sound.BLOCK_ANVIL_LAND, 0.9f, 1.4f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text(kind.label.toUpperCase() + " ONLINE — follow its wire",
                            NamedTextColor.RED),
                    2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void placeModule(Graft graft) {
        Grief.setMechanicBlock(fight.griefContext(), graft.socket, graft.kind.module);
        if (graft.kind == Kind.OBSERVER && graft.socket.getBlockData() instanceof Observer data) {
            data.setFacing(graft.facing);
            graft.socket.setBlockData(data, false);
        }
    }

    /** A straight dust run, socket to power source, with one repeater in the middle to pull. */
    private void layWire(Graft graft, Location socketSpot, Location powerSpot) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int steps = (int) Math.max(2, Math.round(socketSpot.distance(powerSpot)));
        int repeaterAt = steps / 2;
        BlockFace along = cardinalToward(socketSpot, powerSpot);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double x = socketSpot.getX() + (powerSpot.getX() - socketSpot.getX()) * t;
            double z = socketSpot.getZ() + (powerSpot.getZ() - socketSpot.getZ()) * t;
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            Block block = world.getBlockAt(bx, world.getHighestBlockYAt(bx, bz) + 1, bz);
            if (i == repeaterAt) {
                if (Grief.setMechanicBlock(fight.griefContext(), block, Material.REPEATER)) {
                    if (block.getBlockData() instanceof Repeater data) {
                        data.setFacing(along);
                        data.setDelay(Math.min(4, Math.max(1, fight.config().num("graft-repeater-delay", 2))));
                        block.setBlockData(data, false);
                    }
                    graft.repeater = block;
                }
                continue;
            }
            if (Grief.setMechanicBlock(fight.griefContext(), block, Material.REDSTONE_WIRE)) {
                graft.wire.add(block);
            }
        }
    }

    /**
     * The piston graft's actual pistons: a ring of them at melee distance from the socket, each with a
     * floor lever on top. Firing flips the lever, which is what makes the shove a genuine redstone push
     * rather than a scripted {@code setVelocity} — §0.1's rule, applied to the one attack in this fight
     * that is pure displacement.
     */
    private void raisePistons(Graft graft) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = Math.max(1, fight.config().num("piston-count", 4) * Math.max(1, fight.playerCount() / 2));
        double fraction = fight.config().dbl("piston-fraction", 0.32);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block pistonBlock = world.getBlockAt(spot.getBlockX(), spot.getBlockY() - 1, spot.getBlockZ());
            BlockFace outward = cardinalToward(fight.instance().arena().center(), spot);
            if (!Grief.setMechanicBlock(fight.griefContext(), pistonBlock, Material.PISTON)) {
                continue;
            }
            if (pistonBlock.getBlockData() instanceof Piston data) {
                data.setFacing(outward);
                pistonBlock.setBlockData(data, false);
            }
            Block leverBlock = pistonBlock.getRelative(BlockFace.DOWN);
            if (Grief.setMechanicBlock(fight.griefContext(), leverBlock, Material.LEVER)
                    && leverBlock.getBlockData() instanceof Switch lever) {
                lever.setAttachedFace(FaceAttachable.AttachedFace.CEILING);
                lever.setFacing(outward);
                leverBlock.setBlockData(lever, false);
                graft.levers.add(leverBlock);
            }
            graft.pistons.add(pistonBlock);
        }
    }

    // ---------------------------------------------------------------- queries

    boolean armed(Kind kind) {
        Graft graft = grafts.get(kind);
        return graft != null && graft.armed;
    }

    boolean severed(Kind kind) {
        Graft graft = grafts.get(kind);
        return graft != null && graft.severedTicks > 0;
    }

    int cutsOf(Kind kind) {
        Graft graft = grafts.get(kind);
        return graft == null ? 0 : graft.cuts;
    }

    int liveCount() {
        int live = 0;
        for (Graft graft : grafts.values()) {
            if (graft.live()) {
                live++;
            }
        }
        return live;
    }

    int armedCount() {
        int armed = 0;
        for (Graft graft : grafts.values()) {
            if (graft.armed) {
                armed++;
            }
        }
        return armed;
    }

    /** P2's exit condition: the observer line cut while somebody was actually standing in its arc. */
    boolean observerCutWhileWatching() {
        return observerCutWhileWatching;
    }

    /** P3: the repair graft's own line is cut, so nothing else is being rebuilt right now. */
    boolean repairWindowOpen() {
        return repairWindowTicks > 0;
    }

    int cutsInRepairWindow() {
        return cutsInRepairWindow;
    }

    /** How many turrets P4 left standing — the fight's own history, as a number. */
    int liveTurrets() {
        int live = 0;
        for (Graft graft : grafts.values()) {
            if (graft.detached && !graft.dead) {
                live++;
            }
        }
        return live;
    }

    /** Total sum of every graft's cut count, as a monotonic floor-timeout progress signal. */
    int totalCuts() {
        int total = 0;
        for (Graft graft : grafts.values()) {
            total += graft.cuts;
        }
        return total;
    }

    // ----------------------------------------------------------------- pulse

    void pulse(int intervalTicks) {
        if (repairWindowTicks > 0) {
            repairWindowTicks -= intervalTicks;
            if (repairWindowTicks <= 0) {
                cutsInRepairWindow = 0;
            }
        }
        boolean accelerated = armed(Kind.REPAIR) && !severed(Kind.REPAIR) && !isDead(Kind.REPAIR);
        int repairBonus = accelerated ? fight.config().num("repair-graft-bonus-ticks", 10) : 0;

        for (Graft graft : grafts.values()) {
            if (!graft.armed || graft.dead) {
                continue;
            }
            tether(graft);
            if (graft.severedTicks > 0) {
                graft.severedTicks -= intervalTicks + (graft.kind == Kind.REPAIR ? 0 : repairBonus);
                if (graft.severedTicks <= 0) {
                    regraft(graft);
                }
                sparkCut(graft);
                continue;
            }
            graft.cooldownTicks -= intervalTicks;
            if (graft.cooldownTicks > 0) {
                continue;
            }
            fire(graft);
        }
        if (observerCooldownTicks > 0) {
            observerCooldownTicks -= intervalTicks;
        }
        trackObserverArc(intervalTicks);
    }

    private boolean isDead(Kind kind) {
        Graft graft = grafts.get(kind);
        return graft != null && graft.dead;
    }

    /** The visible "this belongs to the Horror" line — snapped for good once a module detaches in P4. */
    private void tether(Graft graft) {
        if (graft.detached || graft.socket == null) {
            return;
        }
        Fx.line(fight.instance().entity().getLocation().add(0, 1.2, 0),
                graft.socket.getLocation().add(0.5, 0.6, 0.5),
                graft.severedTicks > 0 ? Particle.SMOKE : Particle.ELECTRIC_SPARK, 14);
    }

    private void sparkCut(Graft graft) {
        for (Block block : graft.wire) {
            if (block.getType().isAir()) {
                Fx.burst(block.getLocation().add(0.5, 0.2, 0.5), Particle.SMOKE, 2, 0.1);
            }
        }
    }

    private void fire(Graft graft) {
        switch (graft.kind) {
            case DISPENSER -> {
                fireVolley(graft, fight.config().num("dispenser-volley", 2)
                        + Math.max(0, fight.playerCount() - 1));
                graft.cooldownTicks = fight.config().num("dispenser-interval-ticks", 70);
            }
            case PISTON -> {
                cyclePistons(graft);
                graft.cooldownTicks = graft.pistonsExtended
                        ? fight.config().num("piston-extended-ticks", 14)
                        : fight.config().num("piston-interval-ticks", 90);
            }
            case OBSERVER -> graft.cooldownTicks = fight.config().num("observer-idle-ticks", 40);
            case REPAIR -> {
                Location at = graft.socket.getLocation().add(0.5, 1.0, 0.5);
                Fx.coloredBurst(at, GraftFight.RUST, 1.2f, 8, 0.3);
                Fx.sound(at, Sound.BLOCK_CRAFTER_CRAFT, 0.7f, 1.3f);
                graft.cooldownTicks = fight.config().num("repair-tick-ticks", 40);
            }
        }
    }

    /** Real arrows and real fire charges out of a real dispenser — no particle stands in for a projectile. */
    private void fireVolley(Graft graft, int shots) {
        World world = fight.world();
        List<Player> targets = fight.combatants();
        if (world == null || targets.isEmpty()) {
            return;
        }
        Location muzzle = graft.socket.getLocation().add(0.5, 1.1, 0.5);
        Fx.sound(muzzle, Sound.BLOCK_DISPENSER_DISPENSE, 1.2f, 1.0f);
        boolean fireCharge = ThreadLocalRandom.current().nextDouble()
                < fight.config().dbl("dispenser-fire-charge-chance", 0.3);
        for (int i = 0; i < Math.min(shots, fight.config().num("dispenser-max-volley", 6)); i++) {
            Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
            Vector direction = target.getLocation().add(0, 1, 0).toVector()
                    .subtract(muzzle.toVector()).normalize();
            if (fireCharge) {
                SmallFireball ball = world.spawn(muzzle, SmallFireball.class);
                ball.setShooter(fight.instance().entity());
                ball.setDirection(direction);
                ball.setIsIncendiary(false);
                fight.instance().trackEntity(ball);
                continue;
            }
            Arrow arrow = world.spawnArrow(muzzle, direction,
                    (float) fight.config().dbl("dispenser-arrow-speed", 2.2f), 4.0f);
            arrow.setShooter(fight.instance().entity());
            arrow.setDamage(fight.config().dbl("dispenser-arrow-damage", 5.0));
            arrow.setPersistent(false);
            fight.instance().trackEntity(arrow);
        }
    }

    private void cyclePistons(Graft graft) {
        graft.pistonsExtended = !graft.pistonsExtended;
        for (Block lever : graft.levers) {
            if (!(lever.getBlockData() instanceof Switch data)) {
                continue;
            }
            data.setPowered(graft.pistonsExtended);
            // applyPhysics = true: the real redstone update is the whole point — the server's piston
            // engine is what displaces whoever is standing on the head's path.
            lever.setBlockData(data, true);
        }
        if (graft.pistons.isEmpty()) {
            return;
        }
        Location at = graft.pistons.get(0).getLocation().add(0.5, 1, 0.5);
        Fx.sound(at, graft.pistonsExtended ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT, 1.3f, 0.9f);
        if (!graft.pistonsExtended) {
            return;
        }
        double damage = fight.config().dbl("piston-damage", 4.0);
        for (Block piston : graft.pistons) {
            Location head = piston.getLocation().add(0.5, 1, 0.5);
            for (Player player : dev.rbm72.weaponsplugin.boss.Arena.combatants(head, 1.6)) {
                player.damage(damage, fight.instance().entity());
            }
        }
    }

    /**
     * The observer graft's rule, enforced by the block's real facing rather than a script: move inside
     * the arc it is pointed down and it fires. Standing still inside the arc is safe, which is what makes
     * "approach from behind, or don't move" a real choice instead of a dodge.
     */
    private void trackObserverArc(int intervalTicks) {
        Graft graft = grafts.get(Kind.OBSERVER);
        if (graft == null || !graft.live()) {
            lastSeen.clear();
            return;
        }
        double halfWidth = fight.config().dbl("observer-arc-degrees", 55.0);
        double range = fight.config().dbl("observer-range", 20.0);
        double moveThreshold = fight.config().dbl("observer-move-threshold", 0.6);
        boolean triggered = false;

        for (Player player : fight.combatants()) {
            Location previous = lastSeen.put(player.getUniqueId(), player.getLocation().clone());
            if (!inArc(graft, player.getLocation(), halfWidth, range)) {
                continue;
            }
            if (previous == null || previous.getWorld() == null
                    || !previous.getWorld().equals(player.getWorld())) {
                continue;
            }
            if (previous.distance(player.getLocation()) < moveThreshold) {
                continue;
            }
            triggered = true;
        }
        if (!triggered || observerCooldownTicks > 0) {
            return;
        }
        observerCooldownTicks = fight.config().num("observer-retaliation-cooldown-ticks", 60);
        flashObserver(graft);
        fireVolley(graft, fight.config().num("observer-retaliation-volley", 4));
    }

    /** True while somebody is standing in the observer's arc at all — P2's "while it is actively watching". */
    private boolean observerWatching() {
        Graft graft = grafts.get(Kind.OBSERVER);
        if (graft == null || !graft.armed) {
            return false;
        }
        double halfWidth = fight.config().dbl("observer-arc-degrees", 55.0);
        double range = fight.config().dbl("observer-range", 20.0);
        for (Player player : fight.combatants()) {
            if (inArc(graft, player.getLocation(), halfWidth, range)) {
                return true;
            }
        }
        return false;
    }

    private boolean inArc(Graft graft, Location at, double halfWidthDegrees, double range) {
        Location socket = graft.socket.getLocation().add(0.5, 0.5, 0.5);
        if (socket.getWorld() == null || !socket.getWorld().equals(at.getWorld())) {
            return false;
        }
        double dx = at.getX() - socket.getX();
        double dz = at.getZ() - socket.getZ();
        if (Math.sqrt(dx * dx + dz * dz) > range) {
            return false;
        }
        Vector facing = new Vector(graft.facing.getModX(), 0, graft.facing.getModZ());
        double bearing = Math.toDegrees(Math.atan2(dz, dx));
        double centre = Math.toDegrees(Math.atan2(facing.getZ(), facing.getX()));
        double delta = Math.abs(((bearing - centre + 540) % 360) - 180);
        return delta <= halfWidthDegrees;
    }

    private void flashObserver(Graft graft) {
        if (graft.socket.getBlockData() instanceof Observer data) {
            data.setPowered(true);
            graft.socket.setBlockData(data, false);
            fight.plugin().getServer().getScheduler().runTaskLater(fight.plugin(), () -> {
                if (graft.socket.getBlockData() instanceof Observer reset) {
                    reset.setPowered(false);
                    graft.socket.setBlockData(reset, false);
                }
            }, 6L);
        }
        Location at = graft.socket.getLocation().add(0.5, 1, 0.5);
        Fx.coloredBurst(at, GraftFight.SPARK_RED, 1.6f, 24, 0.4);
        Fx.sound(at, Sound.BLOCK_DISPENSER_FAIL, 1.3f, 0.7f);
    }

    // ------------------------------------------------------------------- cuts

    private void sever(Graft graft, Player cutter) {
        if (graft.severedTicks > 0) {
            return;
        }
        graft.cuts++;
        graft.severedTicks = fight.config().num("graft-severed-ticks",
                graft.kind == Kind.REPAIR ? 200 : 160);
        if (graft.kind == Kind.OBSERVER && observerWatching()) {
            observerCutWhileWatching = true;
        }
        if (graft.kind == Kind.REPAIR) {
            repairWindowTicks = fight.config().num("repair-window-ticks", 200);
            cutsInRepairWindow = 0;
        } else if (repairWindowTicks > 0) {
            cutsInRepairWindow++;
        }
        if (graft.kind == Kind.PISTON && graft.pistonsExtended) {
            cyclePistons(graft);
        }
        Location at = graft.socket.getLocation().add(0.5, 1, 0.5);
        Fx.burst(at, Particle.SMOKE, 26, 0.5);
        Fx.sound(at, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.4f, 0.8f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text(graft.kind.label.toUpperCase() + " CUT", NamedTextColor.GREEN),
                    2000L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        if (cutter != null) {
            Fx.sound(cutter.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 0.6f);
        }
    }

    /** It drags the severed line back and reconnects it — §1.4's "re-grafting". */
    private void regraft(Graft graft) {
        graft.severedTicks = 0;
        for (Block block : graft.wire) {
            if (block.getType().isAir()) {
                Grief.setMechanicBlock(fight.griefContext(), block, Material.REDSTONE_WIRE);
            }
        }
        if (graft.repeater != null && graft.repeater.getType().isAir()) {
            Grief.setMechanicBlock(fight.griefContext(), graft.repeater, Material.REPEATER);
        }
        if (graft.socket != null && graft.socket.getType().isAir()) {
            placeModule(graft);
        }
        Location at = graft.socket.getLocation().add(0.5, 1, 0.5);
        Fx.coloredBurst(at, GraftFight.SPARK_RED, 1.6f, 30, 0.6);
        Fx.sound(at, Sound.BLOCK_ANVIL_USE, 0.8f, 1.6f);
    }

    // ------------------------------------------------------------------- P4

    /**
     * P4 — the grafts tear loose. Modules the group never cut become independent floor turrets that keep
     * firing; every module the group did cut at least once falls dead where it stands. Turret count is
     * therefore a direct readout of how much the fight was allowed to run unopposed (§1.4).
     */
    void detachAll() {
        for (Graft graft : grafts.values()) {
            if (!graft.armed) {
                continue;
            }
            graft.detached = true;
            graft.severedTicks = 0;
            if (graft.cuts > 0) {
                graft.dead = true;
                Location at = graft.socket.getLocation().add(0.5, 1, 0.5);
                Fx.burst(at, Particle.SMOKE, 30, 0.5);
                Fx.sound(at, Sound.BLOCK_COPPER_BREAK, 1.2f, 0.6f);
                continue;
            }
            graft.cooldownTicks = fight.config().num("turret-first-delay-ticks", 40);
            Location at = graft.socket.getLocation().add(0.5, 1, 0.5);
            Fx.coloredBurst(at, GraftFight.SPARK_RED, 2.2f, 44, 0.7);
            Fx.sound(at, Sound.BLOCK_PISTON_EXTEND, 1.4f, 0.5f);
        }
    }

    /** A turret's floor wire cut in P4 kills it outright — there is nothing left to re-graft it. */
    private void killTurret(Graft graft) {
        graft.dead = true;
        Location at = graft.socket.getLocation().add(0.5, 1, 0.5);
        Fx.burst(at, Particle.LARGE_SMOKE, 34, 0.6);
        Fx.sound(at, Sound.BLOCK_COPPER_BREAK, 1.4f, 0.7f);
    }

    // -------------------------------------------------------------- lifecycle

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        for (Graft graft : grafts.values()) {
            for (Block lever : graft.levers) {
                if (lever.getBlockData() instanceof Switch data && data.isPowered()) {
                    data.setPowered(false);
                    lever.setBlockData(data, true);
                }
            }
        }
        grafts.clear();
        lastSeen.clear();
    }

    // ----------------------------------------------------------------- helpers

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

    /** A real block only faces N/S/E/W, so every placement snaps to the nearest cardinal. */
    private static BlockFace cardinalToward(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private final class Handler implements Listener {

        /**
         * The one interaction the whole boss is built on. Breaking any dust segment or the repeater kills
         * that module; nothing else about the block matters, so a player with no tools at all can still
         * do it (dust and repeaters break by hand), which is what keeps wire work available to everyone.
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            Block broken = event.getBlock();
            for (Graft graft : grafts.values()) {
                if (!graft.armed || graft.dead) {
                    continue;
                }
                boolean isLine = graft.wire.contains(broken)
                        || (graft.repeater != null && graft.repeater.equals(broken));
                if (!isLine) {
                    continue;
                }
                if (graft.detached) {
                    killTurret(graft);
                } else {
                    sever(graft, event.getPlayer());
                }
                return;
            }
        }
    }
}
