package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.meter.MeterConditions;
import dev.rbm72.weaponsplugin.boss.meter.MeterSpec;
import dev.rbm72.weaponsplugin.boss.meter.MeterThresholds;
import dev.rbm72.weaponsplugin.boss.meter.PlayerMeter;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Void Sovereign's fight-scoped state: the Void Echo meter, the delayed-strike position trail every
 * player leaves behind, the rifts that permanently delete floor, the end crystals of P3, and the piston
 * platforms of P4. One per live fight, same registry+watchdog pattern as {@code KingFight}/
 * {@code FrostFight}/{@code StormFight}/{@code PlagueFight} — the rift count in particular has to
 * outlive a phase change, since P2 onward the arena only ever gets smaller.
 */
final class SovereignFight {

    private static final Map<UUID, SovereignFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);
    static final Color VOID_BLACK = Color.fromRGB(25, 0, 40);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final SovereignConfig config;
    private final AttackContext griefContext;

    private final EchoTrail echoTrail;
    private final Rifts rifts;
    private final EndCrystals crystals;
    private final Phantoms phantoms;
    private final Pistons pistons;
    private final BanishPocket banishPocket;
    private final EnderPearls enderPearls;
    private final PlayerMeter voidEcho;

    private BukkitTask watchdog;

    private SovereignFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new SovereignConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);

        this.echoTrail = new EchoTrail(this);
        this.rifts = new Rifts(this);
        this.crystals = new EndCrystals(this);
        this.phantoms = new Phantoms(this);
        this.pistons = new Pistons(this);
        this.banishPocket = new BanishPocket(this);
        this.enderPearls = new EnderPearls(this);
        this.voidEcho = buildVoidEchoMeter(instance);
    }

    static SovereignFight of(BossInstance instance) {
        SovereignFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        SovereignFight fight = new SovereignFight(instance);
        ACTIVE.put(instance.entity().getUniqueId(), fight);
        fight.startWatchdog();
        return fight;
    }

    private PlayerMeter buildVoidEchoMeter(BossInstance instance) {
        MeterSpec spec = MeterSpec.builder("void_echo", "Void Echo")
                .accent(VOID_PURPLE)
                .cap(100.0)
                .stacks(5)
                .gain(MeterConditions.stationary(2.0), 8.0)
                .cure(MeterConditions.moving(4.0), 20.0)
                .threshold(MeterThresholds.all(
                        MeterThresholds.banish(
                                config.num("banish-hold-ticks", 140),
                                config.dbl("banish-bleed-per-second", 2.0)),
                        (meter, player) -> banishPocket.bind(player)),
                        0.0)
                .thresholdCooldown(config.dbl("echo-threshold-cooldown-seconds", 10.0))
                .warnAt(0.6)
                .hints("keep moving — never backtrack", "ECHOES CLOSING")
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

    SovereignConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    EchoTrail echoTrail() {
        return echoTrail;
    }

    Rifts rifts() {
        return rifts;
    }

    EndCrystals crystals() {
        return crystals;
    }

    Phantoms phantoms() {
        return phantoms;
    }

    Pistons pistons() {
        return pistons;
    }

    EnderPearls enderPearls() {
        return enderPearls;
    }

    PlayerMeter voidEcho() {
        return voidEcho;
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

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("void_sovereign")) {
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
            crystals.discard();
            phantoms.discard();
            pistons.discard();
            // Both were previously left out: the pearl handler is a plugin-manager-registered listener
            // that outlives the fight unless it is unregistered here, and the echo trail can be holding
            // armed BlockDisplay markers at the moment the fight ends.
            enderPearls.discard();
            echoTrail.clear();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Void Sovereign fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
