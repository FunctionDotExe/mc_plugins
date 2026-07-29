package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What each of the three heads actually does when its clock strikes, plus the Convergence they fire
 * together when two or more clocks align (batch-3 §2.4). Kept apart from {@link Clocks} because the
 * clocks are a scheduler and this is a weapon: the scheduler must stay small enough to audit against the
 * §2.8 constraint that the visible machinery and the real attack timing are the same source of truth.
 * <p>
 * Every strike is telegraphed by its own note block a beat early — the audio <em>is</em> the tell, which
 * is what lets a player fight this boss without looking at it.
 */
final class HeadAttacks {

    private final BaneFight fight;

    HeadAttacks(BaneFight fight) {
        this.fight = fight;
    }

    /** Left head — real wither skulls along a firing line. Counterplay is stepping off the line on the beat. */
    void skullVolley() {
        World world = fight.world();
        List<Player> targets = fight.combatants();
        if (world == null || targets.isEmpty()) {
            return;
        }
        Location muzzle = fight.instance().entity().getLocation().add(0, 2.4, 0);
        int shots = fight.config().num("skull-volley-shots", 2) + Math.max(0, fight.playerCount() - 2);
        for (int i = 0; i < Math.min(shots, fight.config().num("skull-volley-max", 6)); i++) {
            Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
            Vector direction = target.getLocation().add(0, 1, 0).toVector().subtract(muzzle.toVector()).normalize();
            WitherSkull skull = world.spawn(muzzle, WitherSkull.class);
            skull.setShooter(fight.instance().entity());
            skull.setDirection(direction);
            skull.setCharged(false);
            skull.setYield(0f);
            skull.setIsIncendiary(false);
            fight.instance().trackEntity(skull);
        }
        Fx.sound(muzzle, Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.8f);
    }

    /**
     * Centre head — a wide floor ring with a real decay bite: heavy damage plus Wither, which is the
     * roster's standard "healing does not answer this" clause (§2.6).
     */
    void decayNova() {
        Location centre = fight.instance().entity().getLocation();
        double radius = fight.config().dbl("decay-nova-radius", 9.0);
        Telegraph.dangerZone(centre, radius);
        Fx.sound(centre, Sound.ENTITY_WITHER_HURT, 1.2f, 0.5f);
        fight.plugin().getServer().getScheduler().runTaskLater(fight.plugin(), () -> {
            if (!fight.instance().entity().isValid()) {
                return;
            }
            Location at = fight.instance().entity().getLocation();
            Fx.coloredRing(at, BaneFight.DECAY_GREY, 2.0f, radius, 40, 0);
            Fx.sound(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.4f, 0.6f);
            double damage = fight.config().dbl("decay-nova-damage", 12.0);
            int witherTicks = fight.config().num("decay-nova-wither-ticks", 120);
            for (Player player : dev.rbm72.weaponsplugin.boss.Arena.combatants(at, radius)) {
                player.damage(damage, fight.instance().entity());
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, 0, true, true, true));
            }
        }, fight.config().num("decay-nova-telegraph-ticks", 30));
    }

    /** Right head — three sweeping beams. Counterplay is breaking the sweep line rather than out-ranging it. */
    void tripleGaze() {
        Location origin = fight.instance().entity().getLocation().add(0, 2.2, 0);
        double range = fight.config().dbl("gaze-range", 24.0);
        double damage = fight.config().dbl("gaze-damage", 9.0);
        double halfWidth = fight.config().dbl("gaze-half-width-degrees", 8.0);
        double baseAngle = ThreadLocalRandom.current().nextDouble(0, 360.0);

        for (int beam = 0; beam < 3; beam++) {
            double angle = baseAngle + beam * 120.0;
            double radians = Math.toRadians(angle);
            Location end = origin.clone().add(Math.cos(radians) * range, -1.5, Math.sin(radians) * range);
            Fx.line(origin, end, Particle.SOUL_FIRE_FLAME, 30);
            for (Player player : fight.combatants()) {
                double bearing = Math.toDegrees(Math.atan2(
                        player.getLocation().getZ() - origin.getZ(),
                        player.getLocation().getX() - origin.getX()));
                double delta = Math.abs(((bearing - angle + 540) % 360) - 180);
                if (delta <= halfWidth) {
                    player.damage(damage, fight.instance().entity());
                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), BaneFight.SOUL_BLUE, 1.4f, 14, 0.4);
                }
            }
        }
        Fx.sound(origin, Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.4f);
    }

    /**
     * <b>Convergence.</b> Two or three clocks striking together, and the one attack in this fight that is
     * unsurvivable in the open regardless of gear (§2.6). The counter is <em>cover</em>: anyone with a
     * solid block between them and the Bane takes nothing at all, which is why the arena grows bone
     * pillars in the first place. Never a wall, never unavoidable — just unforgiving about geometry.
     */
    void convergence(int heads) {
        Location at = fight.instance().entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 2, 0), BaneFight.SOUL_BLUE, 3.0f, 80, 1.2);
        Fx.expandingRings(fight.plugin(), at, Particle.SOUL_FIRE_FLAME, 12.0, 4, 2L);
        Fx.sound(at, Sound.ENTITY_WITHER_SPAWN, 1.6f, 0.6f);

        double damage = fight.config().dbl("convergence-damage", 22.0) * Math.max(2, heads) / 2.0;
        for (Player player : fight.combatants()) {
            if (!player.hasLineOfSight(fight.instance().entity())) {
                fight.plugin().actionBarHub().flash(player,
                        Component.text("COVER HELD", NamedTextColor.GREEN),
                        1600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
                continue;
            }
            player.damage(damage, fight.instance().entity());
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    fight.config().num("convergence-wither-ticks", 100), 0, true, true, true));
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), BaneFight.DECAY_GREY, 2.0f, 26, 0.5);
        }
    }
}
