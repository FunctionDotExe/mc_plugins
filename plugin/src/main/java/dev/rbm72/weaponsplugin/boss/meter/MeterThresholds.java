package dev.rbm72.weaponsplugin.boss.meter;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The stock payloads a full meter ends in, one per skin the roster actually ships: frozen solid,
 * turned into a lightning rod, ruptured, banished.
 * <p>
 * They live here rather than in each boss for the same reason the meter itself does — all four are
 * parameterised versions of two primitives (pin the player and bleed them, or hurt everyone around
 * them) and writing them four times is how the fourth one quietly ends up rewarding the failure. Every
 * number is an argument, so nothing here encodes a boss's tuning.
 * <p>
 * The one-off "this just happened to you" line goes to
 * {@link dev.rbm72.weaponsplugin.ui.ActionBarHub#flash} rather than the meter bar. That is the correct
 * split: the bar carries running state the player must be able to read continuously, and a detonation
 * is a momentary notice, which is exactly what the action bar's priority-flash mechanism is for.
 */
public final class MeterThresholds {

    private static final long NOTICE_MS = 2200L;

    private MeterThresholds() {
    }

    /** Runs several payloads in order. Each is isolated, so one throwing does not skip the rest. */
    public static MeterThreshold all(MeterThreshold... payloads) {
        return (meter, player) -> {
            for (MeterThreshold payload : payloads) {
                try {
                    payload.fire(meter, player);
                } catch (Exception e) {
                    meter.instance().plugin().getLogger().log(java.util.logging.Level.SEVERE,
                            "Composed meter threshold step threw — continuing with the rest.", e);
                }
            }
        };
    }

    /**
     * Frozen solid: pinned where you stand, taking damage, until it wears off or an ally digs you out.
     * <p>
     * The framework holds and bleeds the player; it deliberately does <em>not</em> place the ice shell.
     * That shell is real blocks, so it belongs to the boss and has to go through the arena ledger — and
     * the design's counterplay is allies breaking those blocks with a pickaxe, which only means
     * anything if they are genuinely in the world. A boss pairs this with its own encasement and calls
     * {@link MeterAfflictions#release} when the last block comes down.
     *
     * @param holdTicks       how long the pin lasts with nobody helping
     * @param damagePerSecond bleed while held — "helpless and bleeding HP until freed"
     */
    public static MeterThreshold freezeSolid(int holdTicks, double damagePerSecond) {
        return (meter, player) -> {
            meter.afflictions().hold(player, "FROZEN", holdTicks, damagePerSecond, false, true);
            Location at = player.getLocation();
            Fx.coloredBurst(at.clone().add(0, 1.0, 0), Color.fromRGB(150, 220, 255), 2.2f, 60, 1.0);
            Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
            Fx.sound(at, Sound.BLOCK_POWDER_SNOW_PLACE, 1.4f, 0.7f);
            notice(meter.instance(), player,
                    Component.text("FROZEN SOLID", NamedTextColor.AQUA));
        };
    }

    /**
     * You become the lightning rod: struck where you stand, and the strike jumps to everyone near you,
     * dumping charge into them too. The anti-stack tax the Storm Tyrant is built around, expressed as
     * the consequence of ignoring the rods rather than as an extra attack.
     * <p>
     * Uses {@code strikeLightningEffect} plus explicit damage rather than a real bolt on purpose: a
     * real strike sets fires the {@link dev.rbm72.weaponsplugin.boss.grief.ArenaLedger} never recorded,
     * so the burn scars would survive the rollback and accumulate across every clear of the fight.
     *
     * @param chainRadius  how far the arc reaches
     * @param strikeDamage damage to the player who capped
     * @param chainDamage  damage to each player it jumps to
     * @param chainSpike   meter units dumped into each player it jumps to — this is what makes a
     *                     stacked group cascade
     */
    public static MeterThreshold lightningRod(double chainRadius, double strikeDamage,
                                              double chainDamage, double chainSpike) {
        return (meter, player) -> {
            BossInstance instance = meter.instance();
            Location at = player.getLocation();
            World world = at.getWorld();
            if (world != null) {
                world.strikeLightningEffect(at);
            }
            hurt(instance, player, strikeDamage);
            Fx.coloredRing(at, Color.fromRGB(180, 220, 255), 1.6f, chainRadius, 30, 0);

            for (Player nearby : Arena.combatants(at, chainRadius)) {
                if (nearby.equals(player)) {
                    continue;
                }
                Fx.line(at.clone().add(0, 1, 0), nearby.getLocation().clone().add(0, 1, 0),
                        Particle.ELECTRIC_SPARK, 12);
                hurt(instance, nearby, chainDamage);
                meter.add(nearby, chainSpike);
                notice(instance, nearby, Component.text("ARC!", NamedTextColor.YELLOW));
            }
            Fx.sound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 1.0f);
            notice(instance, player, Component.text("STRUCK — you were the rod", NamedTextColor.YELLOW));
        };
    }

    /**
     * You rupture: a heavy hit on yourself and an infection spike on everyone standing with you. The
     * Plague Warden's whole thesis — you are the danger to your allies — with the damage falling
     * hardest on the person who let it happen, so eating it is never the efficient line.
     *
     * @param radius       how far the burst carries
     * @param selfDamage   damage to the player who ruptured
     * @param nearbyDamage damage to each ally caught in it
     * @param nearbySpike  meter units pushed into each ally caught in it
     */
    public static MeterThreshold rupture(double radius, double selfDamage,
                                         double nearbyDamage, double nearbySpike) {
        return (meter, player) -> {
            BossInstance instance = meter.instance();
            Location at = player.getLocation();
            Fx.coloredBurst(at.clone().add(0, 1.0, 0), Color.fromRGB(120, 180, 60), 2.6f, 70, 1.1);
            Fx.coloredRing(at, Color.fromRGB(120, 180, 60), 1.8f, radius, 30, 0);
            Fx.sound(at, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.3f, 0.6f);

            hurt(instance, player, selfDamage);
            for (Player nearby : Arena.combatants(at, radius)) {
                if (nearby.equals(player)) {
                    continue;
                }
                hurt(instance, nearby, nearbyDamage);
                meter.add(nearby, nearbySpike);
                notice(instance, nearby, Component.text("INFECTED", NamedTextColor.DARK_GREEN));
            }
            notice(instance, player, Component.text("YOU RUPTURE", NamedTextColor.DARK_GREEN));
        };
    }

    /**
     * Banished: pulled out of the fight, blind, pinned and bleeding until it lapses or the rescue act
     * lands. The tether the design gives allies to break, and the chorus fruit a solo player eats, are
     * both physical props the boss owns — either one calls {@link MeterAfflictions#release}.
     * <p>
     * Note what this deliberately does not do: it does not teleport the player anywhere. A real pocket
     * sub-space is a boss-scale feature (it needs somewhere to put them and a guaranteed way back), and
     * a framework that half-implements it would be the thing stranding players outside a finished
     * fight.
     */
    public static MeterThreshold banish(int holdTicks, double damagePerSecond) {
        return (meter, player) -> {
            meter.afflictions().hold(player, "BANISHED", holdTicks, damagePerSecond, true, false);
            Location at = player.getLocation();
            Fx.coloredBurst(at.clone().add(0, 1.0, 0), Color.fromRGB(90, 40, 140), 2.4f, 60, 1.0);
            Fx.burst(at.clone().add(0, 1.0, 0), Particle.PORTAL, 80, 1.2);
            Fx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f, 0.5f);
            notice(meter.instance(), player, Component.text("BANISHED", NamedTextColor.DARK_PURPLE));
        };
    }

    /** A plain heavy hit, for a skin whose consequence is meant to be blunt rather than a state. */
    public static MeterThreshold hit(double damage) {
        return (meter, player) -> hurt(meter.instance(), player, damage);
    }

    private static void hurt(BossInstance instance, Player player, double amount) {
        if (player == null || !player.isOnline() || !player.isValid() || player.isDead() || amount <= 0) {
            return;
        }
        player.damage(amount, instance.entity());
    }

    private static void notice(BossInstance instance, Player player, Component message) {
        instance.plugin().actionBarHub().flash(player, message, NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
    }
}
