package dev.rbm72.weaponsplugin.boss.gates;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;

import java.util.function.Function;

/**
 * One-line constructors for every {@link PhaseMechanic} archetype, with tuned defaults and per-boss
 * config overrides wired up automatically.
 * <p>
 * Without this, giving a boss four different gates meant four hand-written factory methods and forty
 * lines of constructor arguments in that boss's class — enough friction that the path of least
 * resistance was to reuse the same gate for every phase, which is how the whole roster ended up
 * playing identically in the first place. Here a phase picks its archetype in one line and still gets
 * a full set of {@code bosses.<id>.<key>-*} config keys for tuning.
 */
public final class Gates {

    private Gates() {
    }

    private static double cfg(WeaponsPlugin plugin, String bossId, String key, double def) {
        return plugin.getConfig().getDouble("bosses." + bossId + "." + key, def);
    }

    private static int cfgInt(WeaponsPlugin plugin, String bossId, String key, int def) {
        return plugin.getConfig().getInt("bosses." + bossId + "." + key, def);
    }

    /**
     * Kill the escort. Boss untouchable while any guard stands; clear them in time to stagger it open,
     * let the timer lapse and it heals and calls a fresh detachment.
     */
    public static Function<BossInstance, PhaseMechanic> addCull(WeaponsPlugin plugin, String bossId, String key,
                                                             String title, String guardName, Color color,
                                                             EntityType guardType, Material weapon) {
        return instance -> new AddCullGate(instance,
                Component.text(title),
                Component.text(guardName, NamedTextColor.GRAY),
                color, guardType, weapon,
                cfgInt(plugin, bossId, key + "-count", 4),
                cfg(plugin, bossId, key + "-health", 26.0),
                cfgInt(plugin, bossId, key + "-initial-delay-ticks", 70),
                cfgInt(plugin, bossId, key + "-timeout-ticks", 220),
                cfg(plugin, bossId, key + "-fail-heal", 40.0),
                cfg(plugin, bossId, key + "-fail-knockback", 1.4),
                cfg(plugin, bossId, key + "-exposed-multiplier", 2.0),
                cfgInt(plugin, bossId, key + "-exposed-ticks", 150),
                cfgInt(plugin, bossId, key + "-stagger-ticks", 60),
                cfgInt(plugin, bossId, key + "-recall-delay-ticks", 90));
    }

    /** Shoot the airborne conduits. The one gate a melee-only group cannot solve by standing still. */
    public static Function<BossInstance, PhaseMechanic> skyshot(WeaponsPlugin plugin, String bossId, String key,
                                                             String title, Color color, Material conduit) {
        return instance -> new SkyshotGate(instance,
                Component.text(title),
                color, conduit,
                cfgInt(plugin, bossId, key + "-count", 3),
                cfg(plugin, bossId, key + "-health", 24.0),
                cfg(plugin, bossId, key + "-height", 7.0),
                cfg(plugin, bossId, key + "-spread-fraction", 0.6),
                cfgInt(plugin, bossId, key + "-initial-delay-ticks", 70),
                cfg(plugin, bossId, key + "-exposed-multiplier", 2.0),
                cfgInt(plugin, bossId, key + "-exposed-ticks", 170),
                cfgInt(plugin, bossId, key + "-stagger-ticks", 55),
                cfgInt(plugin, bossId, key + "-reform-delay-ticks", 140),
                cfgInt(plugin, bossId, key + "-drain-interval-ticks", 70),
                cfg(plugin, bossId, key + "-drain-damage", 3.0));
    }

    /** Hold the drifting circle. Ward is down only while someone stands in it, and standing there burns. */
    public static Function<BossInstance, PhaseMechanic> controlZone(WeaponsPlugin plugin, String bossId, String key,
                                                                 String title, Color color) {
        return instance -> new ControlZoneGate(instance,
                Component.text(title), color,
                cfg(plugin, bossId, key + "-radius", 4.5),
                cfg(plugin, bossId, key + "-orbit-fraction", 0.55),
                cfg(plugin, bossId, key + "-degrees-per-second", 22.0),
                cfgInt(plugin, bossId, key + "-initial-delay-ticks", 70),
                cfg(plugin, bossId, key + "-held-multiplier", 1.6),
                cfgInt(plugin, bossId, key + "-burn-interval-ticks", 30),
                cfg(plugin, bossId, key + "-burn-damage", 3.0));
    }

    /** Free the captive. Collapses the whole group onto one square while the boss keeps swinging. */
    public static Function<BossInstance, PhaseMechanic> rescue(WeaponsPlugin plugin, String bossId, String key,
                                                            String title, Color color, Material pillar,
                                                            Sound captureSound) {
        return instance -> new RescueGate(instance,
                Component.text(title), color, pillar, captureSound,
                cfgInt(plugin, bossId, key + "-initial-delay-ticks", 80),
                cfgInt(plugin, bossId, key + "-hold-ticks", 120),
                cfgInt(plugin, bossId, key + "-channel-ticks", 60),
                cfg(plugin, bossId, key + "-fail-damage", 20.0),
                cfg(plugin, bossId, key + "-exposed-multiplier", 2.0),
                cfgInt(plugin, bossId, key + "-exposed-ticks", 160),
                cfgInt(plugin, bossId, key + "-stagger-ticks", 60),
                cfgInt(plugin, bossId, key + "-recapture-delay-ticks", 100));
    }

    /** Strike the opening. Sealed on a rhythm; one hit inside the telegraphed window shatters the ward. */
    public static Function<BossInstance, PhaseMechanic> punishWindow(WeaponsPlugin plugin, String bossId, String key,
                                                                  String title, Color color, Sound openSound) {
        return instance -> new PunishWindowGate(instance,
                Component.text(title), color, openSound,
                cfgInt(plugin, bossId, key + "-initial-delay-ticks", 60),
                cfgInt(plugin, bossId, key + "-sealed-ticks", 90),
                cfgInt(plugin, bossId, key + "-telegraph-ticks", 30),
                cfgInt(plugin, bossId, key + "-window-ticks", 40),
                cfg(plugin, bossId, key + "-exposed-multiplier", 2.4),
                cfgInt(plugin, bossId, key + "-exposed-ticks", 140),
                cfgInt(plugin, bossId, key + "-stagger-ticks", 50),
                cfg(plugin, bossId, key + "-miss-damage", 8.0));
    }
}
