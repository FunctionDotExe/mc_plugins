package dev.rbm72.weaponsplugin.boss.bosses.colossus;

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
import org.bukkit.entity.Player;

/**
 * P2's body-state machine: <b>standing → kneeling → rising (shake-off) → standing</b>, looping until
 * the shoulder core breaks. Batch-2 §2.3: "it kneels. The body becomes climbable terrain, and stays
 * climbable for a window before it rises and shakes."
 * <p>
 * <b>Why the climbing structure is player-placed scaffolding, not a fight-authored tower.</b> An
 * earlier design considered building a scaffolding column with {@code Grief} and re-placing it every
 * pulse to track the Colossus as it walks — the literal reading of "climb it while it moves". That
 * falls apart the moment a player is actually standing on the structure: clearing and re-placing
 * blocks under someone's feet every pulse drops them through the world repeatedly, which reads as a
 * bug, not a boss fight. Batch-1 §0.3 already settled the shape this needed: the arena <em>supplies
 * the item</em> ({@link ColossusSupplies}) and the players build with it. So the kneel snapshots one
 * anchor point when it starts (clamped inside the arena for free — see the class header on
 * {@link Joints} — because the Colossus is already leash-confined there every tick), scaffolding is
 * dropped at that anchor, and the "moving boss" fantasy is delivered by the knee/shoulder joints
 * genuinely thrashing in place (chip damage keeps landing, the mechanic keeps demanding attention)
 * and by the shake-off itself, rather than by sliding the whole structure around underfoot.
 */
final class KneelCycle {

    private enum Stance {
        STANDING, KNEELING, RISING
    }

    private final ColossusFight fight;

    private Stance stance = Stance.STANDING;
    private Location anchor;
    private int kneelTicksLeft;
    private int standingTicksLeft;
    private int risingTicksLeft;
    private double lastKneeProgress;
    private int kneelCycles;

    KneelCycle(ColossusFight fight) {
        this.fight = fight;
    }

    /** P2 opens already kneeling — its entry condition (both ankles broken) is exactly what triggers a kneel. */
    void begin() {
        beginKneel();
    }

    void pulse(int intervalTicks) {
        switch (stance) {
            case STANDING -> pulseStanding(intervalTicks);
            case KNEELING -> pulseKneeling(intervalTicks);
            case RISING -> pulseRising(intervalTicks);
        }
    }

    boolean kneeling() {
        return stance == Stance.KNEELING;
    }

    int kneelCycles() {
        return kneelCycles;
    }

    // ---------------------------------------------------------------- standing

    private void pulseStanding(int intervalTicks) {
        // Knees stay reachable at ground level even between kneels — hitting them is what the "ground
        // crew" job actually is (batch-2 §2.3: "the ground crew must keep it kneeling by re-breaking leg
        // joints"), and progress here shortens the wait for the next window instead of only extending
        // one already open.
        double delta = kneeProgressDelta();
        if (delta > 0) {
            standingTicksLeft -= (int) Math.round(delta * fight.config().num("kneel-standing-hasten-ticks", 400));
        }
        standingTicksLeft -= intervalTicks;
        if (standingTicksLeft <= 0) {
            beginKneel();
        }
    }

    // ---------------------------------------------------------------- kneeling

    private void beginKneel() {
        stance = Stance.KNEELING;
        anchor = fight.instance().entity().getLocation().clone();
        boolean solo = fight.solo();
        kneelTicksLeft = fight.config().num(solo ? "kneel-window-ticks-solo" : "kneel-window-ticks-group",
                solo ? 300 : 200);
        lastKneeProgress = 0;
        kneelCycles++;

        double kneeHp = fight.config().dbl("knee-max-hp", 45.0);
        double coreHpBase = fight.config().dbl("shoulder-core-max-hp-base", 60.0);
        double coreHpPerPlayer = fight.config().dbl("shoulder-core-max-hp-per-player", 20.0);
        double coreHp = coreHpBase + coreHpPerPlayer * (fight.playerCount() - 1);

        fight.joints().spawn(Joints.Id.KNEE_LEFT, kneeHp);
        fight.joints().spawn(Joints.Id.KNEE_RIGHT, kneeHp);
        fight.joints().spawn(Joints.Id.SHOULDER, coreHp);
        fight.joints().setExposed(Joints.Id.KNEE_LEFT, true);
        fight.joints().setExposed(Joints.Id.KNEE_RIGHT, true);
        fight.joints().setExposed(Joints.Id.SHOULDER, true);

        placeStarterScaffold(anchor);
        ColossusSupplies.dropForKneel(fight, anchor);

        fight.instance().showTitle(
                Component.text("IT KNEELS", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Climb — break the shoulder core before it rises", NamedTextColor.GRAY));
        Fx.coloredBurst(anchor.clone().add(0, 1, 0), ColossusFight.SOLAR_GOLD, 2.4f, 60, 1.0);
        Fx.sound(anchor, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.4f, 0.5f);
        Fx.sound(anchor, Sound.ENTITY_GENERIC_BIG_FALL, 1.0f, 0.6f);
    }

    private void pulseKneeling(int intervalTicks) {
        double delta = kneeProgressDelta();
        if (delta > 0) {
            int refresh = fight.config().num("kneel-refresh-ticks-per-progress", 500);
            int cap = fight.config().num("kneel-window-cap-ticks", 420);
            kneelTicksLeft = Math.min(cap, kneelTicksLeft + (int) Math.round(delta * refresh));
        }
        kneelTicksLeft -= intervalTicks;

        Fx.coloredRing(anchor, ColossusFight.SOLAR_GOLD, 1.2f, 2.2, 20, fight.instance().entity().getTicksLived() * 0.1);
        if (kneelTicksLeft <= 0) {
            beginRising();
        }
    }

    // ---------------------------------------------------------------- rising / shake-off

    private void beginRising() {
        stance = Stance.RISING;
        boolean solo = fight.solo();
        risingTicksLeft = fight.config().num(solo ? "shake-telegraph-ticks-solo" : "shake-telegraph-ticks-group",
                solo ? 50 : 30);
        fight.instance().showTitle(
                Component.text("IT RISES", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Get down now, or be thrown", NamedTextColor.GRAY));
        Fx.sound(anchor, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.4f);
    }

    private void pulseRising(int intervalTicks) {
        risingTicksLeft -= intervalTicks;
        Fx.coloredBurst(anchor.clone().add(0, 1.5, 0), ColossusFight.CORE_RED, 1.6f, 20, 0.6);
        if (risingTicksLeft <= 0) {
            shakeOff();
        }
    }

    private void shakeOff() {
        double radius = fight.config().dbl("shake-radius", 3.5);
        double heightAbove = fight.config().dbl("shake-min-height-above-anchor", 1.3);
        double damageFraction = fight.config().dbl("shake-damage-fraction", 0.4);
        for (Player player : fight.combatants()) {
            if (player.getWorld() == null || !player.getWorld().equals(anchor.getWorld())) {
                continue;
            }
            double flat = flatDistance(player.getLocation(), anchor);
            if (flat > radius || player.getLocation().getY() < anchor.getY() + heightAbove) {
                continue;
            }
            ejectToGround(player, damageFraction);
        }

        fight.joints().despawn(Joints.Id.KNEE_LEFT);
        fight.joints().despawn(Joints.Id.KNEE_RIGHT);
        fight.joints().despawn(Joints.Id.SHOULDER);
        clearScaffold(anchor);

        Fx.coloredBurst(anchor.clone().add(0, 1.5, 0), ColossusFight.SOLAR_GOLD, 2.8f, 80, 1.2);
        Fx.sound(anchor, Sound.ENTITY_IRON_GOLEM_HURT, 1.6f, 0.5f);
        Fx.sound(anchor, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);

        stance = Stance.STANDING;
        boolean solo = fight.solo();
        standingTicksLeft = fight.config().num(solo ? "kneel-standing-recovery-ticks-solo" : "kneel-standing-recovery-ticks-group",
                solo ? 60 : 90);
        // Knees stay a ground-level target through the standing recovery too — see #pulseStanding.
        fight.joints().spawn(Joints.Id.KNEE_LEFT, fight.config().dbl("knee-max-hp", 45.0));
        fight.joints().spawn(Joints.Id.KNEE_RIGHT, fight.config().dbl("knee-max-hp", 45.0));
    }

    /** §0.3: a pit-style heavy hit and an eject to the floor — never a real fall death. */
    private void ejectToGround(Player player, double damageFraction) {
        World world = player.getWorld();
        Location safe = player.getLocation().clone();
        safe.setY(world.getHighestBlockYAt(safe.getBlockX(), safe.getBlockZ()) + 1.0);
        player.teleport(safe);
        player.setFallDistance(0f);
        player.damage(Math.max(1.0, player.getMaxHealth() * damageFraction), fight.instance().entity());
        Fx.burst(safe, Particle.CLOUD, 30, 0.6);
        Fx.sound(safe, Sound.ENTITY_GENERIC_BIG_FALL, 1.2f, 0.7f);
    }

    // ---------------------------------------------------------------- scaffolding

    private void placeStarterScaffold(Location anchor) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int height = fight.config().num("scaffold-starter-height", 3);
        Block base = world.getBlockAt(anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ());
        for (int y = 0; y < height; y++) {
            Block block = base.getRelative(0, y, 0);
            if (block.getType().isAir()) {
                Grief.setMechanicBlock(fight.griefContext(), block, Material.SCAFFOLDING);
            }
        }
    }

    private void clearScaffold(Location anchor) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int radius = fight.config().num("scaffold-clear-radius", 4);
        int height = fight.config().num("scaffold-clear-height", 10);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= height; y++) {
                    Block block = world.getBlockAt(anchor.getBlockX() + x, anchor.getBlockY() + y, anchor.getBlockZ() + z);
                    Material type = block.getType();
                    if (type == Material.SCAFFOLDING || type == Material.LADDER) {
                        Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private double kneeProgressDelta() {
        double current = fight.joints().damageProgress(Joints.Id.KNEE_LEFT, Joints.Id.KNEE_RIGHT);
        double delta = Math.max(0, current - lastKneeProgress);
        lastKneeProgress = current;
        return delta;
    }

    private static double flatDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Fight-end teardown. The arena ledger restores the scaffolding regardless; this is best-effort tidying. */
    void discard() {
        if (anchor != null) {
            clearScaffold(anchor);
        }
    }
}
