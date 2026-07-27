package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Shulker;

import java.util.concurrent.ThreadLocalRandom;

/**
 * P2 onward: real floor deletion that never comes back for the rest of the fight (restored, like
 * everything else, only by the arena ledger once the fight ends — batch-1 §5.3). Falling in is not a
 * removal; the roster-wide pit rule ({@code BossInstance#enforcePitFloor}) already turns any hole deep
 * enough into a heavy, survivable hit and an eject back onto solid ground, which is exactly the "costs
 * your health bar, not your participation" contract this phase leans on without reimplementing it.
 * <p>
 * Real Shulkers are placed at each new rift's edge — their bullets are both the threat (drift you over
 * the hole) and the tool (cross one), per batch-1 §5.2.
 */
final class Rifts {

    private final SovereignFight fight;
    private int opened;

    Rifts(SovereignFight fight) {
        this.fight = fight;
    }

    int opened() {
        return opened;
    }

    /** Rift count scales with the group — more players, faster arena loss (batch-1 §5.5). */
    int targetCount() {
        return 1 + fight.playerCount() / 2;
    }

    void telegraphNext(Location at, double radius, double progress) {
        Telegraph.dangerZone(at, radius, progress);
    }

    void open(Location at, double radius) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        int ir = (int) Math.ceil(radius);
        double r2 = radius * radius;
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                if (x * x + z * z > r2) {
                    continue;
                }
                Block block = world.getBlockAt(at.getBlockX() + x, at.getBlockY(), at.getBlockZ() + z);
                if (!block.getType().isAir()) {
                    Grief.setBlock(fight.griefContext(), block, Material.AIR);
                }
            }
        }
        opened++;
        Fx.coloredBurst(at.clone().add(0, 1, 0), SovereignFight.VOID_BLACK, 2.6f, 70, 1.0);
        Fx.burst(at.clone().add(0, 1, 0), Particle.SQUID_INK, 40, 0.8);
        Fx.sound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.6f);

        Shulker shulker = world.spawn(at.clone().add(radius * 0.6, 1, 0), Shulker.class);
        shulker.setPersistent(false);
        fight.instance().trackEntity(shulker);
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

    /** A random spot within the still-standing floor — never the exact centre, which stays load-bearing. */
    Location randomSpot() {
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double fraction = ThreadLocalRandom.current().nextDouble(0.3, 0.8);
        return surfaceSpot(angle, fraction);
    }
}
