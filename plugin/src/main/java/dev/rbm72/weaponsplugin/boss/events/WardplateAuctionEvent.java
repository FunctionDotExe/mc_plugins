package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wardplate Auction: the king tears his own armour off and flings the pieces across the arena. Every
 * plate destroyed before the timer is gone for good; every plate left standing flies back onto him and
 * permanently hardens him for the rest of the fight.
 * <p>
 * What makes this different from every other break-the-props mechanic in the plugin is that it is not
 * a gate and there is no pass/fail — it is a <em>negotiation</em>. Nothing is locked, the king is fully
 * hittable throughout, and the group chooses in the moment how much of the window to spend splitting up
 * for plates versus staying on the boss. Take three of four and you have measurably improved the rest
 * of your fight; ignore it entirely and you have chosen a harder king in exchange for uninterrupted
 * damage right now. Both are legitimate, and the consequence is permanent either way, so the decision
 * carries weight instead of resetting on the next cycle.
 * <p>
 * Plates land far apart on purpose. A group that wants all of them has to scatter, which is exactly
 * when the king's own attack pool is most dangerous.
 */
public final class WardplateAuctionEvent extends BossEvent {

    private static final Color PLATE_GOLD = Color.fromRGB(212, 175, 55);

    private final double[] triggers;
    private final int plateCount;
    private final double plateHealth;
    private final int windowTicks;
    private final double placementFraction;
    private final double reattachDamageReduction;
    private final double reattachHeal;

    private final List<ArenaTotem> plates = new ArrayList<>();
    private BukkitTask task;
    private Runnable onComplete;
    private boolean resolved;

    public WardplateAuctionEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        super(plugin, bossId);
        this.triggers = triggers.clone();
        this.plateCount = configInt("auction-plate-count", 4);
        this.plateHealth = configDouble("auction-plate-health", 28.0);
        this.windowTicks = configInt("auction-window-ticks", 220);
        this.placementFraction = configDouble("auction-placement-fraction", 0.7);
        this.reattachDamageReduction = configDouble("auction-reattach-damage-reduction", 0.12);
        this.reattachHeal = configDouble("auction-reattach-heal", 25.0);
    }

    /**
     * The king keeps swinging throughout — the plates are a side objective you weigh against uptime,
     * and that trade only exists while he is still a threat.
     */
    @Override
    public boolean blocksCombat() {
        return false;
    }

    @Override
    public String id() {
        return "wardplate_auction";
    }

    @Override
    public double[] triggerFractions() {
        return triggers.clone();
    }

    @Override
    public void run(BossInstance instance, Runnable onComplete) {
        this.onComplete = onComplete;
        this.resolved = false;
        plates.clear();

        Location center = instance.arena().center();
        World world = center.getWorld();
        if (world == null) {
            finish(instance);
            return;
        }

        Location bossLoc = instance.entity().getLocation();
        Fx.coloredBurst(bossLoc.clone().add(0, 1.4, 0), PLATE_GOLD, 2.4f, 60, 0.9);
        Fx.sound(bossLoc, Sound.ITEM_SHIELD_BREAK, 1.4f, 0.7f);
        instance.showTitle(
                Component.text("WARDPLATE AUCTION", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Destroy the plates — any left will reattach", NamedTextColor.GRAY));

        double spread = instance.arena().radius() * placementFraction;
        double baseAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < plateCount; i++) {
            double angle = baseAngle + 2 * Math.PI * i / plateCount;
            Location spot = center.clone().add(Math.cos(angle) * spread, 0, Math.sin(angle) * spread);
            spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), PLATE_GOLD, 1.6f, 24, 0.5);
            Fx.line(bossLoc.clone().add(0, 1.4, 0), spot.clone().add(0, 1, 0), Particle.CRIT, 18);

            ArenaTotem plate = ArenaTotem.spawn(plugin, instance, spot, Material.SHIELD,
                    Component.text("Royal Wardplate", NamedTextColor.GOLD),
                    plateHealth, windowTicks + 40,
                    broken -> onPlateDestroyed(instance),
                    expired -> { });
            plates.add(plate);
        }

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;

            @Override
            public void run() {
                if (resolved) {
                    return;
                }
                if (!instance.entity().isValid()) {
                    finish(instance);
                    return;
                }
                if (ticks % 4 == 0) {
                    announce(instance, Math.max(0, (windowTicks - ticks) / 20));
                }
                ticks++;
                if (ticks >= windowTicks || standingCount() == 0) {
                    resolve(instance);
                }
            }
        }, 1L, 1L);
        instance.trackTask(task);
    }

    private void onPlateDestroyed(BossInstance instance) {
        if (resolved) {
            return;
        }
        Location loc = instance.entity().getLocation();
        // Deliberately no action-bar notice here. The running count already lives on the mechanic bar,
        // and firing a PRIORITY_NOTICE flash per plate is precisely what was smothering every other
        // readout in this fight for seconds at a time. A distinct rising chime carries it instead.
        Fx.sound(loc, Sound.ENTITY_ITEM_BREAK, 1.2f, 1.4f);
        Fx.sound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
    }

    private int standingCount() {
        int standing = 0;
        for (ArenaTotem plate : plates) {
            if (plate.isValid()) {
                standing++;
            }
        }
        return standing;
    }

    private void announce(BossInstance instance, int secondsLeft) {
        int standing = standingCount();
        Component text = Component.text("WARDPLATE AUCTION  ", NamedTextColor.GOLD)
                .append(Component.text(standing + " plate(s) still standing", NamedTextColor.WHITE))
                .append(Component.text("   " + secondsLeft + "s", NamedTextColor.GRAY))
                .append(Component.text("   any left reattach and harden him", NamedTextColor.DARK_GRAY));
        instance.mechanicBar().updateShared(MechanicBar.Owner.EVENT, instance.barViewers(), text,
                1.0 - standing / (double) Math.max(1, plateCount), BossBar.Color.YELLOW);
    }

    private void resolve(BossInstance instance) {
        if (resolved) {
            return;
        }
        resolved = true;
        int reattached = standingCount();
        Location loc = instance.entity().getLocation();

        if (reattached > 0) {
            for (ArenaTotem plate : plates) {
                if (plate.isValid()) {
                    Fx.line(plate.location().clone().add(0, 1, 0), loc.clone().add(0, 1.4, 0), Particle.CRIT, 18);
                    plate.discard();
                }
            }
            // Permanent for the rest of the fight — this is the cost of leaving them standing, and it
            // compounds across the event's three firings.
            instance.addPermanentDamageReduction(reattachDamageReduction * reattached);
            var attr = instance.entity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double cap = attr != null ? attr.getValue() : instance.entity().getHealth();
            instance.entity().setHealth(Math.min(cap, instance.entity().getHealth() + reattachHeal * reattached));

            Fx.coloredBurst(loc.clone().add(0, 1.4, 0), PLATE_GOLD, 2.6f, 70, 1.0);
            Fx.sound(loc, Sound.BLOCK_ANVIL_LAND, 1.3f, 0.7f);
            instance.showTitle(
                    Component.text("PLATES REATTACH", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                    Component.text(reattached + " recovered — he is harder now", NamedTextColor.GRAY));
        } else {
            instance.recordExposure();
            Fx.coloredBurst(loc.clone().add(0, 1.4, 0), PLATE_GOLD, 2.6f, 70, 1.0);
            Fx.flash(loc.clone().add(0, 1.4, 0), 2);
            Fx.sound(loc, Sound.ITEM_SHIELD_BREAK, 1.4f, 1.2f);
            instance.showTitle(
                    Component.text("STRIPPED BARE", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                    Component.text("Every plate destroyed", NamedTextColor.GRAY));
        }
        finish(instance);
    }

    private void finish(BossInstance instance) {
        cleanup(instance);
        Runnable resume = onComplete;
        onComplete = null;
        if (resume != null) {
            resume.run();
        }
    }

    @Override
    public void cleanup(BossInstance instance) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        for (ArenaTotem plate : plates) {
            plate.discard();
        }
        plates.clear();
        instance.mechanicBar().release(MechanicBar.Owner.EVENT);
    }
}
