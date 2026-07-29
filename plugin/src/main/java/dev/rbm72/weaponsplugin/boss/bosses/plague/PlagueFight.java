package dev.rbm72.weaponsplugin.boss.bosses.plague;

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
 * The Plague Warden's fight-scoped state: the Infection meter, his pyres, the Spore Nodes that corrupt
 * the ground in P2, the Bloated Carriers, and the sculk Host he burrows into for P3. One per live fight,
 * same registry+watchdog pattern as {@code KingFight}/{@code FrostFight}/{@code StormFight}.
 */
final class PlagueFight {

    private static final Map<UUID, PlagueFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color TOXIC = Color.fromRGB(80, 140, 40);
    static final Color SICKLY = Color.fromRGB(150, 200, 80);

    /** Corrupted-ground materials the Bloom spreads — real mud, soul sand and fungal growth (batch-1 §4.3). */
    static final Material[] CORRUPTED_GROUND = {Material.MUD, Material.SOUL_SAND, Material.CRIMSON_NYLIUM};

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final PlagueConfig config;
    private final AttackContext griefContext;

    private final Pyres pyres;
    private final SporeNodes sporeNodes;
    private final Carriers carriers;
    private final Host host;
    private final PlayerMeter infection;

    private BukkitTask watchdog;

    private PlagueFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new PlagueConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);

        this.pyres = new Pyres(this);
        this.sporeNodes = new SporeNodes(this);
        this.carriers = new Carriers(this);
        this.host = new Host(this);
        this.infection = buildInfectionMeter(instance);
    }

    static PlagueFight of(BossInstance instance) {
        PlagueFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        PlagueFight fight = new PlagueFight(instance);
        ACTIVE.put(instance.entity().getUniqueId(), fight);
        fight.startWatchdog();
        return fight;
    }

    private PlayerMeter buildInfectionMeter(BossInstance instance) {
        MeterSpec spec = MeterSpec.builder("infection", "Infection")
                .accent(TOXIC)
                .cap(100.0)
                .gain(MeterConditions.nearBoss(8.0), 2.5)
                .multiplier(MeterConditions.standingOn(CORRUPTED_GROUND), 2.0)
                .contagion(config.dbl("infection-contagion-radius", 6.0), 0.5, 2.0, 0.0,
                        config.num("infection-contagion-max-carriers", 4))
                .cure(MeterConditions.inFire(), config.dbl("infection-cure-per-second", 22.0))
                .convertHealing(config.dbl("rot-heal-cap-per-second", 4.0),
                        config.dbl("rot-heal-cap-burst-seconds", 1.5),
                        config.dbl("rot-meter-per-health", 1.5))
                .threshold(MeterThresholds.rupture(
                        config.dbl("rupture-radius", 5.0),
                        config.dbl("rupture-self-damage", 10.0),
                        config.dbl("rupture-nearby-damage", 6.0),
                        config.dbl("rupture-nearby-spike", 30.0)),
                        0.0)
                .thresholdCooldown(config.dbl("infection-threshold-cooldown-seconds", 6.0))
                .warnAt(0.6)
                .hints("stand in fire — cleanse it", "INFECTED — keep your distance")
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

    PlagueConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Pyres pyres() {
        return pyres;
    }

    SporeNodes sporeNodes() {
        return sporeNodes;
    }

    Carriers carriers() {
        return carriers;
    }

    Host host() {
        return host;
    }

    PlayerMeter infection() {
        return infection;
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

    boolean isCleansed(Player player) {
        return infection.fraction(player) <= config.dbl("cleansed-fraction", 0.25);
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("plague_warden")) {
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
            carriers.discard();
            host.discard();
            sporeNodes.discard();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Plague Warden fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
