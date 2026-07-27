package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * His army: real undead mobs that walk in from marked arena edges and never stop coming.
 * <p>
 * Three deliberate choices make the rest of the fight work:
 * <ul>
 *   <li><b>Only sun-flammable ranks.</b> Zombies, skeletons, zombie villagers and strays all burn under
 *       a daytime sky in vanilla. Husks and wither skeletons do not, and a horde with either in it would
 *       quietly mute the boss's defining mechanic — the Overlord is himself a wither skeleton, so the
 *       sun burning his army and pointedly not him is the whole picture.</li>
 *   <li><b>No helmets, no item pickup.</b> Any headgear at all makes an undead sun-proof, and the arena
 *       hands the group a chest's worth of gear on the floor. A horde that could pick a helmet up would
 *       walk out of the daylight mechanic by accident.</li>
 *   <li><b>Reinforcements off.</b> A zombie that spawns reinforcements produces adds this fight never
 *       tracked, which would survive the end of it — {@code SPAWN_REINFORCEMENTS} is zeroed so every
 *       undead in the arena is one {@link dev.rbm72.weaponsplugin.boss.AddManager} owns.</li>
 * </ul>
 * Waves telegraph as lit lanes on the arena edge before anything spawns, and the countdown lives in
 * {@link #pulse} rather than in a scheduled task of its own — nothing here schedules, so nothing here
 * can leak.
 */
final class UndeadHorde {

    /** Every rank in the horde catches fire under an open daytime sky. That is not a coincidence — see the class doc. */
    private static final EntityType[] RANKS = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ZOMBIE_VILLAGER, EntityType.STRAY
    };

    private final NecroFight fight;
    private final CorpseField corpses;

    /** Wave/grave/pile adds this class raised, and the last place each was seen alive. */
    private final Map<UUID, Location> risen = new LinkedHashMap<>();

    private int waveCooldownLeft;
    private int laneWarningLeft;
    private List<Location> pendingLanes = List.of();
    private int pendingWaveSize;
    private int backLineCooldownLeft;
    private int scorchCooldownLeft;
    private boolean holdBackLine = true;

    UndeadHorde(NecroFight fight, CorpseField corpses) {
        this.fight = fight;
        this.corpses = corpses;
    }

    /**
     * One pulse of the army: notice who died, mark and land waves, and keep the Overlord behind his own
     * front line.
     */
    void pulse(int intervalTicks) {
        collectTheDead();
        driveWaves(intervalTicks);
        keepScorching(intervalTicks);
        if (holdBackLine) {
            retreat(intervalTicks);
        }
    }

    /** True while there is room under the performance cap for another undead. */
    boolean hasRoom() {
        return fight.instance().addManager().aliveCount() < maxAlive();
    }

    int aliveCount() {
        return fight.instance().addManager().aliveCount();
    }

    /**
     * P4 only: he stops hiding behind the army and closes in himself, which is the phase's whole
     * physical tell.
     */
    void stepIntoTheFight() {
        holdBackLine = false;
    }

    /**
     * Raises one undead at {@code where} — used by a reanimating corpse pile and by a grave marker, both
     * of which are placed objects rather than a wave off the arena edge.
     */
    void raiseOne(Location where) {
        if (!hasRoom()) {
            return;
        }
        spawn(where);
    }

    /**
     * Sets the whole standing horde alight. Only ever called when {@link NecroFight#needsArtificialSun}
     * says the arena cannot receive real sunlight — an underground arena, or a nether/end realm with no
     * day clock. Real fire on real mobs, so the shroud break still lands as the fight's biggest swing
     * instead of silently doing nothing; vanilla is simply not available to do it for us there.
     */
    void scorch() {
        int burnTicks = Math.max(20, fight.config().num("artificial-sun-burn-ticks", 200));
        for (UUID id : List.copyOf(risen.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity undead && undead.isValid() && !undead.isDead()) {
                undead.setFireTicks(burnTicks);
            }
        }
        scorchCooldownLeft = Math.max(20, fight.config().num("artificial-sun-interval-ticks", 60));
    }

    /**
     * Re-applies the faked sunlight on a slow interval. Real daylight catches every undead that walks in
     * afterwards for free; a scripted ignition only ever catches whoever was standing there when it fired,
     * so without this the "the sun is on his army" state would decay back to a normal horde within seconds.
     */
    private void keepScorching(int intervalTicks) {
        if (!fight.needsArtificialSun()) {
            return;
        }
        scorchCooldownLeft -= intervalTicks;
        if (scorchCooldownLeft <= 0) {
            scorch();
        }
    }

    // ------------------------------------------------------------------ deaths

    /**
     * Turns kills into corpse piles.
     * <p>
     * Polled rather than event-driven, and keyed strictly off {@code isDead()}, because that is what
     * separates a kill from a cleanup. {@link BossInstance} force-despawns every add on a phase change,
     * and a despawned entity reads invalid but never dead — so a phase transition can never carpet the
     * floor with dozens of piles the group never earned. The death animation holds {@code isDead()} true
     * for about a second, comfortably longer than the pulse interval, so real kills are not missed.
     */
    private void collectTheDead() {
        for (UUID id : List.copyOf(risen.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null) {
                risen.remove(id);
                continue;
            }
            if (entity.isDead()) {
                // Still in the world for its death animation, so this is genuinely where it fell. The
                // last-seen position is only a fallback for the case where that read comes back empty.
                Location fell = entity.getLocation();
                Location lastSeen = risen.remove(id);
                Location grave = fell.getWorld() != null ? fell : lastSeen;
                if (grave != null) {
                    corpses.bury(grave);
                }
                continue;
            }
            risen.put(id, entity.getLocation());
        }
    }

    // ------------------------------------------------------------------- waves

    private void driveWaves(int intervalTicks) {
        if (laneWarningLeft > 0) {
            laneWarningLeft -= intervalTicks;
            markLanes();
            if (laneWarningLeft <= 0) {
                landWave();
            }
            return;
        }
        waveCooldownLeft -= intervalTicks;
        if (waveCooldownLeft > 0) {
            return;
        }
        callWave();
    }

    /**
     * Wave size scales with the group and nothing else — {@code 4} at the floor for a solo player,
     * {@code +2} per extra body, clamped by the live-add cap. Damage per undead never changes; a bigger
     * group faces more of them, which is also what makes it bury its own arena faster.
     */
    private void callWave() {
        int size = fight.config().num("wave-base-size", 4)
                + fight.config().num("wave-size-per-extra-player", 2) * (fight.playerCount() - 1);
        pendingWaveSize = Math.min(size, Math.max(0, maxAlive() - aliveCount()));
        waveCooldownLeft = Math.max(40, fight.config().num("wave-interval-ticks", 200));
        if (pendingWaveSize <= 0) {
            return;
        }
        pendingLanes = pickLanes();
        laneWarningLeft = Math.max(20, fight.config().num("wave-telegraph-ticks", 40));
        for (Location lane : pendingLanes) {
            Fx.sound(lane, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 0.5f);
        }
    }

    /** Two lanes solo, four in a group — coverage scaling, so a group cannot funnel everything into one corridor. */
    private List<Location> pickLanes() {
        int lanes = fight.playerCount() <= 1
                ? fight.config().num("wave-lanes-solo", 2)
                : fight.config().num("wave-lanes-grouped", 4);
        double edge = fight.config().dbl("wave-lane-radius-fraction", 0.92);
        double offset = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        List<Location> spots = new ArrayList<>(lanes);
        for (int i = 0; i < lanes; i++) {
            spots.add(surfaceSpot(offset + Math.PI * 2 * i / lanes, edge));
        }
        return spots;
    }

    /** Pure telegraph: the lane the dead are about to walk out of, lit up well before any of them arrive. */
    private void markLanes() {
        for (Location lane : pendingLanes) {
            Fx.ring(lane, Particle.SOUL, 1.8, 14);
            Fx.burst(lane.clone().add(0, 0.3, 0), Particle.SCULK_SOUL, 4, 0.6);
        }
    }

    private void landWave() {
        List<Location> lanes = pendingLanes;
        if (lanes.isEmpty()) {
            return;
        }
        for (int i = 0; i < pendingWaveSize && hasRoom(); i++) {
            Location lane = lanes.get(i % lanes.size());
            Location at = lane.clone().add(
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0,
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
            spawn(at);
        }
        for (Location lane : lanes) {
            NecroFight.necroticFlourish(lane, Sound.ENTITY_ZOMBIE_AMBIENT, 0.6f);
        }
        pendingLanes = List.of();
        pendingWaveSize = 0;
    }

    private void spawn(Location at) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        EntityType type = RANKS[ThreadLocalRandom.current().nextInt(RANKS.length)];
        Player quarry = nearestCombatant(at);
        LivingEntity undead = fight.instance().addManager().spawn(world, at, type, entity -> {
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                // Strip anything vanilla handed it on spawn. A helmet is all it takes to make an undead
                // immune to daylight, and this boss is solved by daylight.
                equipment.setHelmet(null);
            }
            AttributeInstance reinforcements = entity.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
            if (reinforcements != null) {
                reinforcements.setBaseValue(0.0);
            }
            if (entity instanceof Mob mob && quarry != null) {
                mob.setTarget(quarry);
            }
        });
        // Nameplates are left off on purpose: twenty floating labels stacked across the arena hide the
        // one readout that matters (the mechanic bar) and the horde reads better as a mass than a roster.
        risen.put(undead.getUniqueId(), undead.getLocation());
        Fx.burst(at.clone().add(0, 1, 0), Particle.SCULK_SOUL, 12, 0.4);
    }

    // -------------------------------------------------------------- back line

    /**
     * Keeps him behind his own army until P4 — the anti-burst-skip answer for this boss, and a
     * positional one on purpose.
     * <p>
     * He is never made invulnerable and never filters a hit. The group can always reach him; reaching him
     * means walking through the horde, and the moment they arrive he steps back across the arena to the
     * far side, so pressure on him has to be bought with ground. That distinction matters: "the boss is
     * immune while you do a chore" is the archetype this roster removed from four bosses, and dressing it
     * as a position rather than a flag would have been the same wall wearing a costume.
     */
    private void retreat(int intervalTicks) {
        backLineCooldownLeft -= intervalTicks;
        if (backLineCooldownLeft > 0) {
            return;
        }
        BossInstance instance = fight.instance();
        // Never mid-cast. The attack would still land where its telegraph pointed — the contract holds —
        // but a boss who vanishes to the far wall between the wind-up and the hit reads as the telegraph
        // having lied. The cooldown is already spent, so this retreats on the next pulse after the cast
        // finishes rather than losing the retreat altogether.
        if (instance.attacking()) {
            return;
        }
        Location bossAt = instance.entity().getLocation();
        Player closest = nearestCombatant(bossAt);
        if (closest == null) {
            return;
        }
        double pressureRange = fight.config().dbl("back-line-pressure-range", 8.0);
        if (closest.getLocation().distanceSquared(bossAt) > pressureRange * pressureRange) {
            return;
        }

        // Straight away from whoever is closest, measured from the arena's fixed centre so he always
        // lands on real floor rather than on the far side of a wall he happened to be standing beside.
        double away = Math.atan2(bossAt.getZ() - closest.getLocation().getZ(),
                bossAt.getX() - closest.getLocation().getX());
        Location backLine = surfaceSpot(away, fight.config().dbl("back-line-radius-fraction", 0.8));

        Fx.burst(bossAt.clone().add(0, 1, 0), Particle.SCULK_SOUL, 30, 0.6);
        Fx.sound(bossAt, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        instance.entity().teleport(backLine);
        Fx.burst(backLine.clone().add(0, 1, 0), Particle.SOUL_FIRE_FLAME, 24, 0.6);
        Fx.sound(backLine, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.5f);
        backLineCooldownLeft = Math.max(40, fight.config().num("back-line-cooldown-ticks", 120));
    }

    // ---------------------------------------------------------------- helpers

    /** Hard ceiling on live adds. The horde is meant to feel endless, not to be endless — this is the server's limit, not the design's. */
    private int maxAlive() {
        return Math.max(4, fight.config().num("horde-max-alive", 20));
    }

    private Player nearestCombatant(Location from) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : Arena.combatants(fight.instance().arena().center(),
                fight.instance().arena().radius() + 8.0)) {
            double distance = player.getLocation().distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /**
     * A point at {@code fraction} of the arena radius from its <em>fixed</em> centre, dropped onto the
     * surface. Anchored to the centre rather than to the boss, who spends most of the fight pressed
     * against the far wall by his own retreat and would otherwise place every wave lane outside the
     * reachable floor.
     */
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
