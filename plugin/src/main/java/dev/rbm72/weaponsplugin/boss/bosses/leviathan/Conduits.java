package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The fight's spine: real conduits, arena-supplied and player-placed on real pedestals, that grant
 * water breathing in a radius — and the Leviathan's own answer to them, Conduit Smash.
 * <p>
 * <b>Placement.</b> Deliberately polling rather than a {@code PlayerInteractEvent} listener: a player
 * carrying a real {@code CONDUIT} item (dropped by {@link LeviathanSupplies}) who walks within range of
 * an empty pedestal has it consumed and placed on the very next pulse. This keeps the whole interaction
 * inside the same tick-based shape every other mechanic in this package uses, with nothing extra to
 * unregister on teardown.
 * <p>
 * <b>Conduit Smash.</b> He does not chase a location through the framework's own pathfinding — {@code
 * BossInstance.tick()} re-issues {@code Pathfinder.moveTo(currentTarget, ...)} every server tick,
 * which would fight any attempt to steer him toward a conduit instead. {@link BossInstance#stagger}
 * already suppresses that call entirely for its duration (it returns before the movement branch even
 * runs) — exactly the exclusive-control window an ordinary {@code BossAttack} gets for free from {@code
 * attackInProgress}. The charge borrows that same lever: a long, ordinary-behaviour warning (he is
 * still fighting normally, satisfying batch-1 §0.2's "boss stays live"), then a short staggered dash
 * where this class walks him there itself.
 */
final class Conduits {

    private static final double PLACE_RADIUS = 2.2;

    private final LeviathanFight fight;
    private final List<Slot> slots = new ArrayList<>();

    private SmashState smash;

    private static final class Slot {
        Location pedestalCentre;
        Block conduitBlock;
        boolean active;
        double usageScore;
    }

    private enum SmashPhase { WARN, CHARGE }

    private static final class SmashState {
        Slot target;
        SmashPhase phase;
        int ticksLeft;
        int totalChargeTicks;
        double damageTaken;
    }

    Conduits(LeviathanFight fight) {
        this.fight = fight;
    }

    // ------------------------------------------------------------------- setup

    /**
     * One prismarine pedestal per slot, evenly spaced. Solo gets one conduit; a full group gets up to
     * three (batch-2 §3.5) — never rebuilt once placed, since the pedestals themselves are permanent
     * furniture for the rest of the fight.
     */
    void buildPedestals() {
        if (!slots.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = slotCount();
        double fraction = fight.config().dbl("conduit-fraction", 0.55);
        int floorY = fight.water().floorY();
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location centre = surfaceSpot(angle, fraction, floorY);
            Slot slot = new Slot();
            slot.pedestalCentre = centre;
            slot.conduitBlock = world.getBlockAt(centre.getBlockX(), floorY, centre.getBlockZ());
            buildPlatform(world, centre, floorY);
            slots.add(slot);
            Fx.coloredBurst(centre.clone().add(0.5, 1, 0.5), LeviathanFight.PALE, 1.4f, 24, 0.5);
        }
    }

    private int slotCount() {
        int players = fight.playerCount();
        if (players <= 1) {
            return 1;
        }
        return players == 2 ? 2 : 3;
    }

    private void buildPlatform(World world, Location centre, int floorY) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block base = world.getBlockAt(centre.getBlockX() + x, floorY - 1, centre.getBlockZ() + z);
                Grief.setMechanicBlock(fight.griefContext(), base, Material.PRISMARINE_BRICKS);
            }
        }
    }

    private Location surfaceSpot(double angleRadians, double fraction, int y) {
        Location centre = fight.instance().arena().center();
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(y);
        return spot;
    }

    // -------------------------------------------------------------------- state

    int activeCount() {
        int count = 0;
        for (Slot slot : slots) {
            if (slot.active) {
                count++;
            }
        }
        return count;
    }

    int inactiveSlotCount() {
        return slots.size() - activeCount();
    }

    /** Used by {@link Air} — real proximity to a currently-lit conduit grants breathing. */
    boolean isNearActiveConduit(Location loc) {
        double radius = fight.config().dbl("conduit-breath-radius", 6.0);
        for (Slot slot : slots) {
            if (slot.active && flatDistance(loc, slot.pedestalCentre) <= radius) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------------- pulse

    void pulse(int intervalTicks) {
        handlePlacement();
        trackUsage();
        tickSmash(intervalTicks);
    }

    private void handlePlacement() {
        for (Player player : fight.combatants()) {
            for (Slot slot : slots) {
                if (slot.active || flatDistance(player.getLocation(), slot.pedestalCentre) > PLACE_RADIUS) {
                    continue;
                }
                if (consumeConduitItem(player)) {
                    place(slot);
                    fight.instance().recordProgress();
                }
                break;
            }
        }
    }

    private boolean consumeConduitItem(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        if (main.getType() == Material.CONDUIT) {
            main.setAmount(main.getAmount() - 1);
            return true;
        }
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() == Material.CONDUIT) {
            off.setAmount(off.getAmount() - 1);
            return true;
        }
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && stack.getType() == Material.CONDUIT) {
                stack.setAmount(stack.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private void place(Slot slot) {
        slot.active = true;
        slot.usageScore = 0;
        Grief.setMechanicBlock(fight.griefContext(), slot.conduitBlock, Material.CONDUIT);
        Location at = slot.pedestalCentre;
        Fx.coloredBurst(at.clone().add(0.5, 1, 0.5), LeviathanFight.TEAL, 2.0f, 50, 0.7);
        Fx.sound(at, Sound.BLOCK_CONDUIT_ACTIVATE, 1.2f, 1.0f);
        Fx.sound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
    }

    /** Nearby-combatant seconds accumulated per slot — feeds "he prioritises the most-used conduit". */
    private void trackUsage() {
        for (Slot slot : slots) {
            if (!slot.active) {
                continue;
            }
            double radius = fight.config().dbl("conduit-breath-radius", 6.0);
            for (Player player : fight.combatants()) {
                if (flatDistance(player.getLocation(), slot.pedestalCentre) <= radius) {
                    slot.usageScore += 1.0;
                }
            }
        }
    }

    // ---------------------------------------------------------------- damage relay

    void onBossDamaged(double damageDealt) {
        if (smash != null && smash.phase == SmashPhase.CHARGE) {
            smash.damageTaken += damageDealt;
        }
    }

    // ---------------------------------------------------------------- conduit smash

    /** P2+ calls this on their own cooldown; a no-op while a smash is already running. */
    void trySmash() {
        if (smash != null) {
            return;
        }
        Slot target = mostUsedActiveSlot();
        if (target == null) {
            return;
        }
        smash = new SmashState();
        smash.target = target;
        smash.phase = SmashPhase.WARN;
        smash.ticksLeft = Math.max(20, fight.config().num("conduit-smash-warn-ticks", 70));

        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("HE TURNS TOWARD A CONDUIT", NamedTextColor.RED),
                    2400L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        Fx.sound(fight.instance().entity().getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 0.5f);
    }

    private Slot mostUsedActiveSlot() {
        Slot best = null;
        for (Slot slot : slots) {
            if (!slot.active) {
                continue;
            }
            if (best == null || slot.usageScore > best.usageScore) {
                best = slot;
            }
        }
        return best;
    }

    private void tickSmash(int intervalTicks) {
        if (smash == null) {
            return;
        }
        if (!smash.target.active) {
            // Destroyed or otherwise gone since the smash was called — nothing left to charge.
            smash = null;
            return;
        }
        smash.ticksLeft -= intervalTicks;
        Location bossLoc = fight.instance().entity().getLocation();
        Location conduitLoc = smash.target.pedestalCentre.clone().add(0.5, 1, 0.5);

        if (smash.phase == SmashPhase.WARN) {
            renderSmashTelegraph(bossLoc, conduitLoc);
            if (smash.ticksLeft <= 0) {
                double baitDistance = fight.config().dbl("conduit-smash-bait-distance", 26.0);
                if (flatDistance(bossLoc, conduitLoc) > baitDistance) {
                    baited(conduitLoc);
                    return;
                }
                smash.phase = SmashPhase.CHARGE;
                smash.totalChargeTicks = Math.max(10, fight.config().num("conduit-smash-charge-ticks", 30));
                smash.ticksLeft = smash.totalChargeTicks;
                smash.damageTaken = 0;
                fight.instance().stagger(smash.totalChargeTicks + 10);
            }
            return;
        }

        // CHARGE: step the boss toward the conduit ourselves — see the class header for why this is
        // safe to do only under stagger().
        double interceptThreshold = fight.config().dbl("conduit-smash-intercept-damage", 30.0);
        if (smash.damageTaken >= interceptThreshold) {
            intercepted(bossLoc);
            return;
        }
        double progress = 1.0 - Math.max(0, smash.ticksLeft) / (double) smash.totalChargeTicks;
        stepToward(bossLoc, conduitLoc, progress);
        Fx.coloredBurst(bossLoc.clone().add(0, 1, 0), LeviathanFight.DEEP_TEAL, 1.6f, 14, 0.4);
        if (smash.ticksLeft <= 0) {
            arrive();
        }
    }

    private void renderSmashTelegraph(Location bossLoc, Location conduitLoc) {
        Fx.line(bossLoc.clone().add(0, 1.2, 0), conduitLoc, Particle.BUBBLE, 20);
        Fx.coloredRing(conduitLoc, LeviathanFight.DEEP_TEAL, 1.4f, 2.0, 20, 0);
    }

    private void stepToward(Location bossLoc, Location conduitLoc, double progress) {
        LivingEntity boss = fight.instance().entity();
        Vector toward = conduitLoc.toVector().subtract(bossLoc.toVector());
        double distance = toward.length();
        if (distance < 1.5) {
            return;
        }
        Vector direction = toward.normalize();
        double step = Math.min(distance - 1.0, distance / Math.max(1, (1.0 - progress) * 6 + 1));
        Location next = bossLoc.clone().add(direction.clone().multiply(Math.max(0.5, step)));
        next.setY(bossLoc.getY());
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        next.setYaw(yaw);
        next.setPitch(bossLoc.getPitch());
        boss.teleport(next);
    }

    private void baited(Location conduitLoc) {
        Fx.coloredBurst(conduitLoc, LeviathanFight.PALE, 1.4f, 20, 0.5);
        Fx.sound(conduitLoc, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.0f, 1.2f);
        smash = null;
    }

    private void intercepted(Location bossLoc) {
        fight.instance().stagger(20);
        Fx.coloredBurst(bossLoc.clone().add(0, 1.4, 0), LeviathanFight.TEAL, 2.0f, 40, 0.8);
        Fx.sound(bossLoc, Sound.ENTITY_ELDER_GUARDIAN_HURT, 1.3f, 0.8f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("INTERCEPTED", NamedTextColor.GREEN),
                    2000L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        smash = null;
    }

    private void arrive() {
        Slot target = smash.target;
        smash = null;
        destroy(target);
    }

    private void destroy(Slot slot) {
        slot.active = false;
        slot.usageScore = 0;
        Grief.setMechanicBlock(fight.griefContext(), slot.conduitBlock, Material.AIR);
        Location at = slot.pedestalCentre;
        Fx.coloredBurst(at.clone().add(0.5, 1.2, 0.5), LeviathanFight.DEEP_TEAL, 2.6f, 70, 1.0);
        Fx.burst(at.clone().add(0.5, 1, 0.5), Particle.EXPLOSION, 4, 0.4);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        Fx.sound(at, Sound.BLOCK_CONDUIT_DEACTIVATE, 1.0f, 0.8f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("CONDUIT DESTROYED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                    2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    // --------------------------------------------------------------------- teardown

    void discardAll() {
        smash = null;
        slots.clear();
    }

    private static double flatDistance(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
