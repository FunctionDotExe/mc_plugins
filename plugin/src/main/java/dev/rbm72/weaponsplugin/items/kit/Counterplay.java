package dev.rbm72.weaponsplugin.items.kit;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.meter.PlayerMeter;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * The four things a boss does to a player, and the answers a boss's own drop is allowed to give.
 * <p>
 * Boss drops currently differ from each other only in damage numbers, which makes gearing illegible: a
 * player who beats the Frost Queen has no way to tell that her weapon is <em>for</em> her fight, and no
 * reason to bring it to the next one. The roster already speaks in a small vocabulary of verbs — a meter
 * armour cannot stop, a silence that removes your audio cues, terrain placed to wall you in, and footing
 * taken away underneath you — so this class names each verb once and gives a weapon one line to answer
 * it with. A drop then means "this shakes off what that boss does", which is a reason to own it beyond
 * its damage stat.
 * <p>
 * Every method degrades to a harmless no-op outside a boss fight, and says so in its return value rather
 * than throwing. That is what makes these safe to put on an ability a player also uses to kill zombies:
 * an ability whose only effect is counterplay would feel broken in the open world, so weapons pair the
 * call with something that always happens and treat a {@code false} as "no boss to answer here".
 */
public final class Counterplay {

    /**
     * How much armour-ignoring meter a normal counterplay hit is worth, as a fraction of the meter's cap.
     * <p>
     * Deliberately a large fraction rather than all of it. A drop that empties a meter outright deletes
     * the boss's anti-facetank clock (batch-1 §0.2 rule 4) for anyone holding the right item, which turns
     * "the correct weapon helps" into "the correct weapon skips the mechanic". A big dent still has to be
     * spent at the right moment and still leaves the boss's cure act worth doing.
     */
    private static final double DEFAULT_METER_RELIEF = 0.45;

    /** Footing replacements, by what took the footing away. A boss's floor is answered with a floor. */
    private static final Map<Material, Material> FOOTING_FIXES = Map.of(
            Material.BLUE_ICE, Material.PACKED_MUD,
            Material.PACKED_ICE, Material.PACKED_MUD,
            Material.ICE, Material.PACKED_MUD,
            Material.FROSTED_ICE, Material.PACKED_MUD,
            Material.POWDER_SNOW, Material.SNOW_BLOCK,
            Material.SOUL_SAND, Material.SMOOTH_STONE,
            Material.MAGMA_BLOCK, Material.SMOOTH_STONE,
            Material.SLIME_BLOCK, Material.SMOOTH_STONE);

    private Counterplay() {
    }

    /**
     * Answers the meter verb: knocks a chunk off every armour-ignoring stack the covering fight has on
     * {@code player} — Chill, Static Charge, Infection, Void Echo alike.
     * <p>
     * Skin-blind on purpose. A weapon that named one meter would be dead weight in the three fights that
     * use a different one, and the batch-4 audit settled that the roster holds at exactly four skins, so
     * "whatever this boss is stacking on me" is a stable thing to write against.
     *
     * @param fraction how much of each meter's cap to remove, or 0 to use the default relief
     * @return true if at least one meter was actually reduced.
     */
    public static boolean relieveMeters(WeaponsPlugin plugin, Player player, double fraction) {
        BossInstance fight = coveringFight(plugin, player);
        if (fight == null || fight.meters() == null) {
            return false;
        }
        double share = fraction > 0 ? Math.min(1.0, fraction) : DEFAULT_METER_RELIEF;
        boolean any = false;
        for (PlayerMeter meter : fight.meters().meters()) {
            if (meter.value(player) <= 0) {
                continue;
            }
            meter.cure(player, meter.spec().cap() * share);
            any = true;
        }
        if (any) {
            Fx.sound(player, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.6f);
            Fx.coloredBurst(player.getEyeLocation(), org.bukkit.Color.WHITE, 1.2f, 22, 0.5);
            plugin.actionBarHub().flash(player, Component.text("Purged", NamedTextColor.AQUA),
                    1000, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        return any;
    }

    /**
     * Answers the silence verb: gives {@code player} their senses back for {@code ticks}.
     * <p>
     * The Hollow Choir's silence is a real {@link PotionEffectType#DARKNESS} that the mechanic
     * <em>re-applies on a timer</em> — so a one-shot {@code removePotionEffect} is cleared again within
     * the second and reads as the ability doing nothing. Holding the removal open for a window is what
     * makes this an answer rather than a flicker, and the window ending naturally is what stops it being
     * a permanent opt-out of the fight's hardest phase.
     *
     * @return true if the player was actually silenced when asked.
     */
    public static boolean breakSilence(WeaponsPlugin plugin, Player player, int ticks) {
        if (!player.hasPotionEffect(PotionEffectType.DARKNESS)) {
            return false;
        }
        Fx.sound(player, Sound.ITEM_GOAT_HORN_SOUND_0, 0.9f, 1.4f);
        plugin.actionBarHub().flash(player, Component.text("Silence Broken", NamedTextColor.GOLD),
                1200, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);

        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (elapsed >= ticks || !player.isOnline()) {
                    cancel();
                    return;
                }
                player.removePotionEffect(PotionEffectType.DARKNESS);
                elapsed += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
        return true;
    }

    /**
     * Answers the terrain verb: undoes whatever the fight built within {@code radius} of {@code player} —
     * ice encasements, stone pillars, a wall dropped between the group and the boss.
     * <p>
     * Undo rather than break, via {@link dev.rbm72.weaponsplugin.boss.grief.ArenaLedger#restoreNear}: the
     * blocks go back to what they were and leave the fight's undo log consistent. Breaking them instead
     * would drop items the boss never meant to give away and would desync the rollback.
     *
     * @return how many blocks were cleared. Zero means there was nothing of the boss's here.
     */
    public static int breakPillars(WeaponsPlugin plugin, Player player, double radius) {
        int cleared = plugin.tempTerrain().revertNear(player.getLocation(), radius);

        BossInstance fight = coveringFight(plugin, player);
        if (fight != null) {
            cleared += fight.ledger().restoreNear(player.getLocation(), radius);
        }
        if (cleared > 0) {
            Fx.sound(player, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
            Fx.blockBurst(player.getLocation().add(0, 1, 0), Material.STONE, 20, 0.7);
        }
        return cleared;
    }

    /**
     * Answers the footing verb: turns the floor under and around {@code player} back into something that
     * can be stood and fought on, for {@code ticks}.
     * <p>
     * A real block swap through {@link TempTerrain}, not a potion. Slipping on blue ice is a physics
     * property of the block, so no status effect can answer it — the only honest counterplay to "the
     * floor is ice" is "briefly, it is not ice". Because the patch is temporary, the boss's terrain is
     * back shortly and this is a window to reposition rather than a cure for the phase.
     *
     * @return how many floor blocks were replaced.
     */
    public static int fixFooting(WeaponsPlugin plugin, Player player, double radius, int ticks) {
        Location centre = player.getLocation();
        int radiusBlocks = (int) Math.ceil(radius);
        int fixed = 0;

        for (int dx = -radiusBlocks; dx <= radiusBlocks; dx++) {
            for (int dz = -radiusBlocks; dz <= radiusBlocks; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                // Two levels: the block being stood on, and the one a powder-snow pit would have you
                // sunk into. Anything deeper is not footing, it is terrain the player is not touching.
                for (int dy = -1; dy <= 0; dy++) {
                    Block block = centre.clone().add(dx, dy, dz).getBlock();
                    Material fix = FOOTING_FIXES.get(block.getType());
                    if (fix != null && plugin.tempTerrain().place(player, block, fix, ticks)) {
                        fixed++;
                    }
                }
            }
        }
        if (fixed > 0) {
            Fx.sound(player, Sound.BLOCK_ROOTED_DIRT_PLACE, 1.0f, 0.9f);
            plugin.actionBarHub().flash(player, Component.text("Footing Held", NamedTextColor.GREEN),
                    1000, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        return fixed;
    }

    /**
     * Answers the displacement verb: cancels the throw a boss just put on {@code player} and plants them.
     * <p>
     * Separate from the others because knockback is not a status, a block or a stack — it is velocity that
     * has already been applied by the time any ability can respond. Zeroing it is the only counterplay
     * there is, and the brief slow-fall keeps a player who was mid-air from immediately eating the fall
     * damage the shove was going to cause.
     */
    public static void plant(WeaponsPlugin plugin, Player player, int slowFallTicks) {
        player.setVelocity(new Vector(0, 0, 0));
        if (slowFallTicks > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    PotionEffectType.SLOW_FALLING, slowFallTicks, 0, false, false));
        }
        Fx.sound(player, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
    }

    /**
     * The fight whose arena contains this player, or null.
     * <p>
     * Fails closed and quietly: a boss manager that is mid-startup or mid-shutdown means "no fight to
     * answer", never an exception thrown out of the middle of a player's ability.
     */
    private static BossInstance coveringFight(WeaponsPlugin plugin, Player player) {
        try {
            if (plugin.bossManager() == null) {
                return null;
            }
            return plugin.bossManager().instanceCovering(player.getLocation()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
