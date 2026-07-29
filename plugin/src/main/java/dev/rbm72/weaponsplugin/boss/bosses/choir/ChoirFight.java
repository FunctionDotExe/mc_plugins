package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
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
 * The Hollow Choir's fight-scoped state: the noise model, the instruments, the phrase and the attacks
 * that read from all three. The noise model in particular is fight-wide rather than per-phase — the
 * Choir's memory of who it last heard has to survive a health band ticking over, or its targeting would
 * silently reset at exactly the moments the fight gets hardest. Same registry+watchdog pattern as
 * {@code StormFight}.
 */
final class ChoirFight {

    private static final Map<UUID, ChoirFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color PALE_VIOLET = Color.fromRGB(160, 130, 220);
    static final Color DEEP_INDIGO = Color.fromRGB(60, 40, 110);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final ChoirConfig config;
    private final AttackContext griefContext;

    private final Noise noise;
    private final Instruments instruments;
    private final Phrase phrase;
    private final ChoirAttacks attacks;

    private BukkitTask watchdog;

    private ChoirFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new ChoirConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);
        this.noise = new Noise(this);
        this.instruments = new Instruments(this);
        this.phrase = new Phrase(this);
        this.attacks = new ChoirAttacks(this);
    }

    static ChoirFight of(BossInstance instance) {
        ChoirFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        ChoirFight fight = new ChoirFight(instance);
        ACTIVE.put(instance.entity().getUniqueId(), fight);
        fight.startWatchdog();
        return fight;
    }

    // ------------------------------------------------------------------ access

    WeaponsPlugin plugin() {
        return plugin;
    }

    BossInstance instance() {
        return instance;
    }

    ChoirConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Noise noise() {
        return noise;
    }

    Instruments instruments() {
        return instruments;
    }

    Phrase phrase() {
        return phrase;
    }

    ChoirAttacks attacks() {
        return attacks;
    }

    World world() {
        return instance.arena().world();
    }

    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    List<Player> combatants() {
        return Arena.combatants(instance.arena().center(), instance.arena().radius());
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("hollow_choir")) {
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
            attacks.discardAll();
            phrase.stop();
            instruments.discardAll();
            noise.discardAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Hollow Choir fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
