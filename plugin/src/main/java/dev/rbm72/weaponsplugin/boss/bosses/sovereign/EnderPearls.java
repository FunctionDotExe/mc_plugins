package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * §5.2/§5.4's ender pearls: "a real thrown projectile... dodge — or catch it (it drops as an item) and
 * re-throw it later... you and he swap positions, often straight into a rift." All-fight rather than
 * phase-gated, same as the Echo trail — the mechanics table's "one throw per cycle" is a frequency, not
 * a phase restriction.
 * <p>
 * A real {@link EnderPearl} rather than a simulated swap: sneaking at the moment of impact is the catch
 * (the same input Minecraft already uses to signal "I am being careful right now"), and a caught pearl
 * becomes a genuine dropped item instead of firing the swap — reusable exactly as far as the vanilla
 * item already lets it be.
 */
final class EnderPearls {

    private final SovereignFight fight;
    private int cooldownTicks;
    private Handler handler;

    EnderPearls(SovereignFight fight) {
        this.fight = fight;
    }

    void pulse(int intervalTicks) {
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
        cooldownTicks -= intervalTicks;
        if (cooldownTicks > 0) {
            return;
        }
        cooldownTicks = fight.config().num("pearl-interval-ticks", 100);
        throwPearl();
    }

    private void throwPearl() {
        List<Player> combatants = fight.combatants();
        if (combatants.isEmpty() || !fight.instance().entity().isValid()) {
            return;
        }
        Player target = combatants.get(ThreadLocalRandom.current().nextInt(combatants.size()));
        Location from = fight.instance().entity().getEyeLocation();
        Fx.coloredBurst(from, SovereignFight.VOID_PURPLE, 1.2f, 16, 0.3);
        Fx.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 0.6f);

        Vector toward = target.getEyeLocation().toVector().subtract(from.toVector());
        double speed = fight.config().dbl("pearl-speed", 1.4);
        EnderPearl pearl = fight.instance().entity().launchProjectile(EnderPearl.class,
                toward.normalize().multiply(speed));
        fight.instance().trackEntity(pearl);
    }

    private void swap(Location impact, Player target) {
        Location bossAt = fight.instance().entity().getLocation();
        Location playerAt = target.getLocation();
        fight.instance().entity().teleport(playerAt);
        target.teleport(bossAt);
        Fx.coloredBurst(bossAt.clone().add(0, 1, 0), SovereignFight.VOID_BLACK, 2.0f, 40, 0.7);
        Fx.coloredBurst(playerAt.clone().add(0, 1, 0), SovereignFight.VOID_PURPLE, 2.0f, 40, 0.7);
        Fx.sound(playerAt, Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, 0.8f);
        fight.plugin().actionBarHub().flash(target,
                Component.text("SWAPPED", NamedTextColor.DARK_PURPLE),
                2000L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
    }

    private void caught(Location impact, Player target) {
        World world = impact.getWorld();
        if (world != null) {
            world.dropItemNaturally(impact, new org.bukkit.inventory.ItemStack(Material.ENDER_PEARL));
        }
        Fx.burst(impact.clone().add(0, 1, 0), Particle.PORTAL, 20, 0.4);
        Fx.sound(impact, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        fight.plugin().actionBarHub().flash(target,
                Component.text("CAUGHT — yours to throw back", NamedTextColor.LIGHT_PURPLE),
                2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
    }

    /**
     * In-flight pearls are covered by the boss instance's own entity tracking, but the handler is not:
     * a {@link Listener} registered straight with the plugin manager lives until the plugin disables,
     * so leaving it behind leaked one permanently-registered handler per fight, each holding a hard
     * reference to that fight and its {@code BossInstance}, and each still resolving pearl hits for a
     * Sovereign that no longer exists.
     */
    void discard() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(ProjectileHitEvent event) {
            Projectile projectile = event.getEntity();
            if (!(projectile instanceof EnderPearl) || !(projectile.getShooter() instanceof org.bukkit.entity.Entity shooter)
                    || !shooter.equals(fight.instance().entity())) {
                return;
            }
            Player target = event.getHitEntity() instanceof Player player ? player
                    : nearestCombatant(projectile.getLocation());
            projectile.remove();
            if (target == null) {
                return;
            }
            if (target.isSneaking()) {
                caught(projectile.getLocation(), target);
            } else {
                swap(projectile.getLocation(), target);
            }
        }

        private Player nearestCombatant(Location at) {
            Player nearest = null;
            double best = 9.0;
            for (Player player : fight.combatants()) {
                if (!player.getWorld().equals(at.getWorld())) {
                    continue;
                }
                double distance = player.getLocation().distanceSquared(at);
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            return nearest;
        }
    }
}
