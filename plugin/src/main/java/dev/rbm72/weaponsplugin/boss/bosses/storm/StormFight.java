package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.meter.MeterConditions;
import dev.rbm72.weaponsplugin.boss.meter.MeterSpec;
import dev.rbm72.weaponsplugin.boss.meter.MeterThresholds;
import dev.rbm72.weaponsplugin.boss.meter.PlayerMeter;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Storm Tyrant's fight-scoped state: the Static Charge meter, his four lightning rods, the flooded
 * sections his Floodplain phase pours across the floor, and the four Storm Pylons his Eye of the Storm
 * phase feeds from. One per live fight, same registry+watchdog pattern as {@code KingFight}/
 * {@code FrostFight} — the rods and pylons in particular have to survive a phase change intact, since
 * P3 counts pylons destroyed toward its exit condition regardless of which health band that happens in.
 */
final class StormFight {

    private static final Map<UUID, StormFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);
    static final Color STORM_WHITE = Color.fromRGB(235, 245, 255);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final StormConfig config;
    private final AttackContext griefContext;

    private final LightningRods rods;
    private final Flooding flooding;
    private final StormPylons pylons;
    private final PlayerMeter charge;

    private BukkitTask watchdog;

    private StormFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new StormConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);

        this.rods = new LightningRods(this);
        this.flooding = new Flooding(this);
        this.pylons = new StormPylons(this);
        this.charge = buildChargeMeter(instance);
    }

    static StormFight of(BossInstance instance) {
        StormFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        StormFight fight = new StormFight(instance);
        ACTIVE.put(instance.entity().getUniqueId(), fight);
        fight.startWatchdog();
        return fight;
    }

    private PlayerMeter buildChargeMeter(BossInstance instance) {
        MeterSpec spec = MeterSpec.builder("static_charge", "Static")
                .accent(STORM_YELLOW)
                .cap(100.0)
                .gain(MeterConditions.always(), 4.0)
                .multiplier(MeterConditions.inWater(), 2.0)
                .contagion(config.dbl("charge-contagion-radius", 5.0), 0.6, 2.0, 0.0,
                        config.num("charge-contagion-max-carriers", 4))
                .cure(MeterConditions.touchingBlock(Material.LIGHTNING_ROD), config.dbl("charge-discharge-per-second", 70.0))
                .threshold(MeterThresholds.lightningRod(
                        config.dbl("charge-chain-radius", 5.0),
                        config.dbl("charge-strike-damage", 10.0),
                        config.dbl("charge-chain-damage", 6.0),
                        config.dbl("charge-chain-spike", 35.0)),
                        0.0)
                .thresholdCooldown(config.dbl("charge-threshold-cooldown-seconds", 6.0))
                .warnAt(0.6)
                .hints("touch a lightning rod", "CHARGED — discharge now")
                .build();
        return instance.meters().attach(spec);
    }

    // ------------------------------------------------------------------ access

    WeaponsPlugin plugin() {
        return plugin;
    }

    BossInstance instance() {
        return instance;
    }

    StormConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    LightningRods rods() {
        return rods;
    }

    Flooding flooding() {
        return flooding;
    }

    StormPylons pylons() {
        return pylons;
    }

    PlayerMeter charge() {
        return charge;
    }

    World world() {
        return instance.arena().world();
    }

    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    List<Player> combatants() {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    /** Discharged right now — at or under a quarter charge. Storm Pylons only let these players hurt him. */
    boolean isDischarged(Player player) {
        return charge.fraction(player) <= config.dbl("discharged-fraction", 0.25);
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("storm_tyrant")) {
                return;
            }
            release();
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    private void release() {
        ACTIVE.remove(instance.entity().getUniqueId());
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        try {
            rods.discardAll();
            pylons.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Storm Tyrant fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
