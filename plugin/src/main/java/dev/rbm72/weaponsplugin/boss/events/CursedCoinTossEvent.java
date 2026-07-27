package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cursed Coin Toss: the king flips a coin and the arena splits along a random line. One half blesses
 * whoever stands in it, the other curses them, and the line re-rolls partway through so no spot stays
 * correct for long.
 * <p>
 * This is the only mechanic on the king that makes the <em>floor</em> conditional rather than the boss.
 * It never blocks damage, spawns nothing, and asks for no chore — it simply means the ground you are
 * standing on now has an opinion, and the ground the boss is standing on is not necessarily the ground
 * you want. That tension is the whole mechanic: melee has to weigh chasing the king into the cursed
 * half against giving up uptime, while ranged can sit in the blessing and give up nothing, so it
 * quietly asks different questions of different players without any per-role scripting.
 * <p>
 * The re-roll is what stops it becoming a one-time repositioning tax. Settle in, and it moves.
 */
public final class CursedCoinTossEvent extends BossEvent {

    private static final Color BLESSED = Color.fromRGB(255, 240, 160);
    private static final Color CURSED = Color.fromRGB(120, 40, 160);
    private static final long NOTICE_MS = 1000;
    private static final long TICK_INTERVAL = 4L;

    private final double[] triggers;
    private final int durationTicks;
    private final int rerollTicks;
    private final double curseDamage;
    private final int curseIntervalTicks;

    private BukkitTask task;
    private Runnable onComplete;
    /** Unit normal of the dividing line; a player's side is the sign of their offset dotted with this. */
    private double normalX;
    private double normalZ;
    private boolean resolved;

    public CursedCoinTossEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        super(plugin, bossId);
        this.triggers = triggers.clone();
        this.durationTicks = configInt("coin-toss-duration-ticks", 300);
        this.rerollTicks = configInt("coin-toss-reroll-ticks", 120);
        this.curseDamage = configDouble("coin-toss-curse-damage", 4.0);
        this.curseIntervalTicks = configInt("coin-toss-curse-interval-ticks", 40);
    }

    /**
     * The whole mechanic is repositioning under pressure; a king standing politely still while you pick
     * a side would remove the cost entirely.
     */
    @Override
    public boolean blocksCombat() {
        return false;
    }

    @Override
    public String id() {
        return "cursed_coin_toss";
    }

    @Override
    public double[] triggerFractions() {
        return triggers.clone();
    }

    @Override
    public void run(BossInstance instance, Runnable onComplete) {
        this.onComplete = onComplete;
        this.resolved = false;
        reroll(instance, true);

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
                if (ticks > 0 && rerollTicks > 0 && ticks % rerollTicks == 0) {
                    reroll(instance, false);
                }
                drawDivide(instance);
                applySides(instance, ticks);
                ticks += TICK_INTERVAL;
                if (ticks >= durationTicks) {
                    resolve(instance);
                }
            }
        }, 1L, TICK_INTERVAL);
        instance.trackTask(task);
    }

    private void reroll(BossInstance instance, boolean first) {
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        normalX = Math.cos(angle);
        normalZ = Math.sin(angle);

        Location loc = instance.entity().getLocation();
        Fx.sound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, first ? 0.8f : 1.4f);
        if (first) {
            Fx.coloredBurst(loc.clone().add(0, 1.6, 0), BLESSED, 2.2f, 50, 0.8);
            instance.showTitle(
                    Component.text("CURSED COIN", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                    Component.text("Half this floor is against you", NamedTextColor.GRAY));
        } else {
            for (Player player : Arena.combatants(loc, instance.arena().radius())) {
                plugin.actionBarHub().flash(player,
                        Component.text("The coin turns — the halves have swapped!", NamedTextColor.LIGHT_PURPLE),
                        1600, ActionBarHub.PRIORITY_NOTICE);
            }
        }
    }

    /** True when this location sits on the blessed side of the dividing line. */
    private boolean blessedSide(BossInstance instance, Location loc) {
        Location center = instance.arena().center();
        if (center.getWorld() == null || loc.getWorld() == null || !center.getWorld().equals(loc.getWorld())) {
            return true;
        }
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        return dx * normalX + dz * normalZ >= 0;
    }

    /**
     * Draws the seam itself rather than shading whole halves — a full-area particle fill at this radius
     * would be thousands of particles a tick. One clear line plus per-player feedback is both cheaper
     * and easier to read than a haze.
     */
    private void drawDivide(BossInstance instance) {
        Location center = instance.arena().center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radius = instance.arena().radius();
        // The seam runs perpendicular to the normal.
        double lineX = -normalZ;
        double lineZ = normalX;
        for (double t = -radius; t <= radius; t += 0.8) {
            Location spot = center.clone().add(lineX * t, 0, lineZ * t);
            spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1.1);
            Fx.coloredBurst(spot, CURSED, 1.6f, 2, 0.0);
        }
    }

    /**
     * Applies each side's effect and, just as importantly, tells every player which side they are
     * personally standing on.
     * <p>
     * The first version of this relied on a faint particle seam plus an action-bar line, and in play
     * that was unreadable: the seam is easy to miss in a busy fight, and the action-bar line was being
     * suppressed outright by higher-priority combat notices. Players could not tell which half was
     * which, whether they had moved to the right one, or that anything had happened at all. Now the
     * verdict is on the mechanic bar where nothing can stomp it, a ring is drawn under each player's
     * own feet in their side's colour, and the cursed side is announced with a distinct sound the
     * moment you cross onto it.
     */
    private void applySides(BossInstance instance, int ticks) {
        boolean curseTick = curseIntervalTicks > 0 && ticks % curseIntervalTicks < TICK_INTERVAL;
        List<Player> viewers = instance.barViewers();
        for (Player player : viewers) {
            boolean blessed = blessedSide(instance, player.getLocation());
            // A ring under your own feet, in your own side's colour — unmissable and unambiguous even
            // when the arena is full of other particles.
            Fx.coloredRing(player.getLocation(), blessed ? BLESSED : CURSED, 1.1f, 1.3, 12, ticks * 0.2);
            if (blessed) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false));
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 0, true, false));
                if (curseTick) {
                    player.damage(curseDamage, instance.entity());
                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), CURSED, 1.4f, 18, 0.4);
                    Fx.sound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.4f);
                }
            }
        }
        instance.mechanicBar().update(MechanicBar.Owner.EVENT, viewers, player -> {
            boolean blessed = blessedSide(instance, player.getLocation());
            if (blessed) {
                return MechanicBar.Readout.of(
                        Component.text("BLESSED GROUND  ", NamedTextColor.GOLD)
                                .append(Component.text("you are on the safe half", NamedTextColor.WHITE))
                                .append(Component.text("   stay until the coin turns", NamedTextColor.GRAY)),
                        1.0, BossBar.Color.YELLOW);
            }
            return MechanicBar.Readout.of(
                    Component.text("CURSED GROUND  ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text("CROSS THE LINE NOW", NamedTextColor.RED))
                            .append(Component.text("   you are taking damage here", NamedTextColor.GRAY)),
                    0.0, BossBar.Color.PURPLE);
        });
    }

    private void resolve(BossInstance instance) {
        if (resolved) {
            return;
        }
        resolved = true;
        Location loc = instance.entity().getLocation();
        Fx.sound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 0.9f);
        instance.showTitle(
                Component.text("THE COIN SETTLES", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true),
                Component.text("The floor is neutral again", NamedTextColor.DARK_GRAY));
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
        // Short, self-expiring effects, but clear them anyway so nobody walks out of the fight with a
        // lingering curse from an event that has already ended.
        instance.mechanicBar().release(MechanicBar.Owner.EVENT);
        for (Player player : Arena.combatants(instance.entity().getLocation(), instance.arena().radius() + 12)) {
            player.removePotionEffect(PotionEffectType.WEAKNESS);
        }
    }
}
