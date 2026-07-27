package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Void Echoes: every player's own recent positions strike back at them a few seconds later, so
 * continuous movement is the baseline rather than a suggestion (batch-1 §5.2). A sample of each
 * player's location is taken on a fixed rhythm; a real dark marker — not a particle standing in for one
 * — physically sits on that spot once it arms, exactly as batch-1's own mechanics table describes it,
 * and detonating damages whoever is standing there <em>then</em> — usually nobody, since the whole point
 * is to have moved on, but a player who backtracks into their own trail walks straight into it.
 */
final class EchoTrail {

    private static final long SAMPLE_INTERVAL_MS = 500L;
    private static final long ARM_BEFORE_STRIKE_MS = 1000L;

    private static final class Echo {
        final Location at;
        final long fireAtMs;
        BlockDisplay marker;

        Echo(Location at, long fireAtMs) {
            this.at = at;
            this.fireAtMs = fireAtMs;
        }
    }

    private final SovereignFight fight;
    private final Deque<Echo> pending = new ArrayDeque<>();
    private long lastSampleAtMs;
    private int hitsSinceCheck;

    EchoTrail(SovereignFight fight) {
        this.fight = fight;
    }

    void pulse() {
        long now = System.currentTimeMillis();
        long delayMs = fight.config().num("echo-delay-ms", 3000);

        if (now - lastSampleAtMs >= SAMPLE_INTERVAL_MS) {
            lastSampleAtMs = now;
            for (Player player : fight.combatants()) {
                pending.add(new Echo(player.getLocation().clone(), now + delayMs));
            }
        }

        while (!pending.isEmpty() && pending.peek().fireAtMs <= now) {
            resolve(pending.poll());
        }
        for (Echo echo : pending) {
            long left = echo.fireAtMs - now;
            if (left <= ARM_BEFORE_STRIKE_MS && echo.marker == null) {
                echo.marker = spawnMarker(echo.at);
            }
        }
    }

    /**
     * The physical marker itself: a real, flattened black-glass {@link BlockDisplay} sitting on the
     * ground where the strike will land — a genuine object a player can look at and judge distance from,
     * not a burst of particles standing in for one. Untracked by {@code BossInstance} on purpose: it is
     * always removed within {@link #ARM_BEFORE_STRIKE_MS} of spawning by {@link #resolve} or by
     * {@link #clear} on fight teardown, so it can never actually outlive the fight the way a tracked
     * grief prop guards against.
     */
    private BlockDisplay spawnMarker(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        BlockDisplay marker = world.spawn(at.clone().add(0, 0.02, 0), BlockDisplay.class, entity -> {
            entity.setBlock(org.bukkit.Material.BLACK_STAINED_GLASS.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setBrightness(new Display.Brightness(2, 2));
            entity.setGlowing(true);
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.5f, 0f, -0.5f),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(1f, 0.05f, 1f),
                    new AxisAngle4f(0, 0, 1, 0)));
            entity.setPersistent(false);
        });
        return marker;
    }

    private void resolve(Echo echo) {
        Location at = echo.at;
        if (echo.marker != null && echo.marker.isValid()) {
            echo.marker.remove();
        }
        Fx.coloredBurst(at.clone().add(0, 0.5, 0), SovereignFight.VOID_PURPLE, 2.0f, 40, 0.8);
        Fx.burst(at.clone().add(0, 0.5, 0), Particle.REVERSE_PORTAL, 20, 0.5);
        Fx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f);
        double radius = fight.config().dbl("echo-radius", 1.6);
        double damage = fight.config().dbl("echo-damage", 7.0);
        for (Player player : Arena.combatants(at, radius)) {
            player.damage(damage, fight.instance().entity());
            fight.voidEcho().addStacks(player, 1);
            hitsSinceCheck++;
        }
    }

    /** Hits landed since the last check — P1's "survive one full Echo cycle with no player struck". */
    int consumeHits() {
        int hits = hitsSinceCheck;
        hitsSinceCheck = 0;
        return hits;
    }

    void clear() {
        for (Echo echo : pending) {
            if (echo.marker != null && echo.marker.isValid()) {
                echo.marker.remove();
            }
        }
        pending.clear();
    }
}
