package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

/**
 * <b>Cinder Nova</b> (§1.4): "when nobody is in melee range for too long... arena-wide heavy fire
 * damage" — the roster's anti-kite tax, expressed here as a trigger on <em>absence</em> of melee contact
 * rather than a timer on the attack pool. A pooled {@code BossAttack} fires on its own cooldown
 * regardless of where anyone is standing, which cannot express "only when nobody has closed the
 * distance"; this class tracks exactly that condition instead, which is why it is phase-owned mechanic
 * state and not a fourth entry in the existing {@code CinderNovaAttack} rotation.
 * <p>
 * Cover is real: a raised stone wall the group built with a water bucket blocks the ray trace between
 * the Warlord and a sheltering player exactly the same way any other solid block would (§1.4: "close the
 * distance, or take cover behind a raised stone wall you built") — no bespoke "is this player behind
 * player-made stone" check, just line of sight against whatever is actually standing in the world.
 */
final class CinderNova {

    private final InfernoFight fight;

    private boolean armed;
    private double idleSeconds;

    CinderNova(InfernoFight fight) {
        this.fight = fight;
    }

    void arm() {
        armed = true;
        idleSeconds = 0.0;
    }

    void disarm() {
        armed = false;
    }

    void pulse(int intervalTicks) {
        if (!armed) {
            return;
        }
        double meleeRadius = fight.config().dbl("heat-aura-radius", 4.0);
        boolean anyoneClose = fight.combatants().stream()
                .anyMatch(p -> p.getLocation().distanceSquared(fight.instance().entity().getLocation()) <= meleeRadius * meleeRadius);
        if (anyoneClose) {
            idleSeconds = 0.0;
            return;
        }
        idleSeconds += intervalTicks / 20.0;
        double threshold = fight.config().dbl("cinder-nova-idle-seconds", 12.0);
        if (idleSeconds >= threshold) {
            idleSeconds = 0.0;
            fire();
        }
    }

    private void fire() {
        LivingEntity boss = fight.instance().entity();
        Location origin = boss.getLocation();
        // The -idle- infix keeps these off CinderNovaAttack's cinder-nova-damage/-telegraph-ticks:
        // both read bosses.inferno_warlord.<key>, and that attack is still in the rotation.
        int telegraphTicks = fight.config().num("cinder-nova-idle-telegraph-ticks", 42);

        Fx.coloredRing(origin, InfernoFight.EMBER, 2.0f, 1.5, 20, 0);
        Fx.sound(origin, Sound.ENTITY_BLAZE_AMBIENT, 1.4f, 0.4f);
        fight.instance().showTitle(
                Component.text("CINDER NOVA", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Close in, or get behind cover", NamedTextColor.GRAY));

        org.bukkit.scheduler.BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!boss.isValid() || elapsed >= telegraphTicks) {
                    if (boss.isValid()) {
                        detonate();
                    }
                    cancel();
                    return;
                }
                double radius = fight.instance().arena().radius();
                Telegraph.dangerZone(origin, radius, elapsed / (double) telegraphTicks);
                elapsed += 5;
            }
        }.runTaskTimer(fight.plugin(), 0L, 5L);
        fight.instance().trackTask(task);
    }

    private void detonate() {
        LivingEntity boss = fight.instance().entity();
        Location origin = boss.getLocation();
        double damage = fight.config().dbl("cinder-nova-idle-damage", 15.0);
        double radius = fight.instance().arena().radius();

        Fx.expandingRings(fight.plugin(), origin, Particle.FLAME, radius, 6, 3L);
        Fx.coloredBurst(origin.clone().add(0, 1, 0), InfernoFight.DEEP_FIRE, 2.6f, 70, radius * 0.4);
        Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.5f);

        for (Player player : fight.combatants()) {
            if (hasCover(origin, player)) {
                continue;
            }
            player.damage(damage, boss);
            fight.burning().add(player, fight.config().dbl("cinder-nova-burning-add", 25.0));
        }
    }

    /** Real line-of-sight against whatever solid block currently stands between them — including player-made stone. */
    private boolean hasCover(Location origin, Player player) {
        World world = origin.getWorld();
        if (world == null) {
            return false;
        }
        Location from = origin.clone().add(0, 1.2, 0);
        Location to = player.getLocation().add(0, 1.0, 0);
        double distance = from.distance(to);
        if (distance < 0.5) {
            return false;
        }
        RayTraceResult hit = world.rayTraceBlocks(from, to.toVector().subtract(from.toVector()).normalize(),
                distance, FluidCollisionMode.NEVER, true);
        return hit != null && hit.getHitBlock() != null;
    }
}
