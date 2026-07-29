package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * P4's swallow: a real, enclosed pocket room the victim is physically teleported into, built once (a
 * fixed hideaway far outside the arena, never seen from it) and reset between uses rather than rebuilt
 * from scratch every time. One weak-point block ({@code CRYING_OBSIDIAN}, broken exactly like a Storm
 * Pylon core — ordinary hits chip a tracked HP pool) is both the escape route and the payoff: breaking
 * it opens the wall and lands real damage on the boss, so being swallowed is a detour with a reward
 * attached, not just a punishment (batch-4 §1.3). Only ever one victim at a time (§1.5) — a second
 * swallow simply can't start while {@link #hasActiveSwallow()} is true.
 */
final class Interior {

    private final WyrmFight fight;
    private boolean built;
    private Location roomCentre;
    private Block weakPoint;
    private Handler handler;

    private UUID victimId;
    private Location returnLocation;
    private double weakPointHp;
    private double weakPointMaxHp;
    private BukkitTask dotTask;

    Interior(WyrmFight fight) {
        this.fight = fight;
    }

    boolean hasActiveSwallow() {
        return victimId != null;
    }

    boolean isVictim(Player player) {
        return victimId != null && victimId.equals(player.getUniqueId());
    }

    /**
     * Teleports {@code victim} into the pocket room, remembering where to send them back. No-op if a
     * swallow is already in progress — the caller (P4's swallow timer) should never attempt a second
     * one, but this is the hard backstop.
     */
    void swallow(Player victim) {
        if (hasActiveSwallow()) {
            return;
        }
        ensureBuilt();
        if (roomCentre == null) {
            return;
        }
        victimId = victim.getUniqueId();
        returnLocation = victim.getLocation().clone();
        boolean solo = fight.playerCount() <= 1;
        weakPointMaxHp = fight.config().dbl(solo ? "interior-weakpoint-hp-solo" : "interior-weakpoint-hp", solo ? 30.0 : 55.0);
        weakPointHp = weakPointMaxHp;
        resetWeakPoint();

        victim.teleport(roomCentre);
        victim.setFallDistance(0f);
        Fx.coloredBurst(roomCentre, WyrmFight.VOID_PURPLE, 2.4f, 60, 0.8);
        Fx.sound(roomCentre, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.6f);
        fight.plugin().actionBarHub().flash(victim,
                Component.text("SWALLOWED — break the crying obsidian to get out", NamedTextColor.LIGHT_PURPLE),
                3000L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);

        startDot();
    }

    private void startDot() {
        double dps = fight.config().dbl("interior-damage-per-second", 2.5);
        dotTask = fight.plugin().getServer().getScheduler().runTaskTimer(fight.plugin(), () -> {
            Player victim = currentVictim();
            if (victim == null) {
                return;
            }
            dev.rbm72.weaponsplugin.boss.TickDamage.apply(fight.instance(), victim, dps);
            Fx.burst(victim.getLocation(), Particle.SQUID_INK, 6, 0.3);
        }, 20L, 20L);
        fight.instance().trackTask(dotTask);
    }

    private Player currentVictim() {
        if (victimId == null) {
            return null;
        }
        Player player = fight.plugin().getServer().getPlayer(victimId);
        return player != null && player.isOnline() && player.isValid() ? player : null;
    }

    /** Called from the weak-point break handler: pays out boss damage and releases the victim. */
    private void breakOut() {
        double payout = fight.config().dbl("interior-breakout-boss-damage", 40.0);
        Player victim = currentVictim();
        if (fight.instance().entity().isValid()) {
            if (victim != null) {
                fight.instance().entity().damage(payout, victim);
            } else {
                fight.instance().entity().damage(payout);
            }
        }
        if (victim != null) {
            victim.teleport(safeReturn(victim));
            victim.setFallDistance(0f);
            victim.removePotionEffect(PotionEffectType.NAUSEA);
            Fx.coloredBurst(victim.getLocation(), WyrmFight.STARLIGHT, 2.0f, 40, 0.6);
            Fx.sound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f);
        }
        release();
    }

    private Location safeReturn(Player victim) {
        if (returnLocation != null && returnLocation.getWorld() != null) {
            return returnLocation;
        }
        return fight.instance().arena().center().add(0, 1, 0);
    }

    /** Safety valve: a phase change or fight end with someone still inside must not strand them. */
    void forceRelease() {
        Player victim = currentVictim();
        if (victim != null) {
            victim.teleport(safeReturn(victim));
            victim.setFallDistance(0f);
        }
        release();
    }

    private void release() {
        victimId = null;
        returnLocation = null;
        if (dotTask != null) {
            dotTask.cancel();
            dotTask = null;
        }
    }

    private void ensureBuilt() {
        if (built) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location arenaCentre = fight.instance().arena().center();
        // A fixed hideaway well outside any arena's footprint — never seen, never walked to. Y=200 is
        // arbitrary but safely inside the build limit regardless of which world the fight is running in.
        Location floorCentre = new Location(world, arenaCentre.getX() + 800, 200, arenaCentre.getZ() + 800);
        int half = 2;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = 0; y <= 3; y++) {
                    boolean shell = Math.abs(x) == half || Math.abs(z) == half || y == 0 || y == 3;
                    if (!shell) {
                        continue;
                    }
                    Block block = world.getBlockAt(floorCentre.clone().add(x, y, z));
                    Grief.setMechanicBlock(fight.griefContext(), block, Material.OBSIDIAN);
                }
            }
        }
        roomCentre = floorCentre.clone().add(0.5, 1, 0.5);
        weakPoint = world.getBlockAt(floorCentre.clone().add(half, 1, 0));
        ensureHandler();
        built = true;
    }

    private void resetWeakPoint() {
        if (weakPoint != null) {
            Grief.setMechanicBlock(fight.griefContext(), weakPoint, Material.CRYING_OBSIDIAN);
        }
    }

    private void ensureHandler() {
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    void discardAll() {
        forceRelease();
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(BlockDamageEvent event) {
            if (weakPoint == null || !hasActiveSwallow() || !weakPoint.equals(event.getBlock())) {
                return;
            }
            weakPointHp -= fight.config().dbl("interior-weakpoint-hit-damage", 5.0);
            Fx.blockBurst(event.getBlock().getLocation().add(0.5, 0.5, 0.5), Material.CRYING_OBSIDIAN, 12, 0.4);
            if (weakPointHp <= 0) {
                Grief.setMechanicBlock(fight.griefContext(), weakPoint, Material.AIR);
                breakOut();
            }
        }
    }
}
