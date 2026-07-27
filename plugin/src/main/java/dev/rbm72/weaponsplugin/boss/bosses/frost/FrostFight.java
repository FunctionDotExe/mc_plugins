package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.meter.MeterConditions;
import dev.rbm72.weaponsplugin.boss.meter.MeterSpec;
import dev.rbm72.weaponsplugin.boss.meter.MeterThresholds;
import dev.rbm72.weaponsplugin.boss.meter.PlayerMeter;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Frost Queen's fight-scoped state: the Chill meter, her four campfires, the growing ice field, the
 * prisons it feeds, the moving avalanche band, and the Frozen Heart she drops in P3. One per live fight,
 * shared by all four phases — same registry+watchdog pattern as {@code KingFight}, for the same reason:
 * the ice field's radius, which campfires are still lit, and how many prisons have been broken all have
 * to survive a phase change intact, or crossing a health seam would hand the group a free reset on the
 * terrain they have spent the fight fighting through.
 * <p>
 * <b>Lifecycle.</b> Registered under the boss entity's id and torn down by one <em>untracked</em>
 * watchdog once {@code BossManager} stops listing the fight as live — untracked because
 * {@link BossInstance#end} cancels every tracked task as its first step, which would kill a tracked
 * watchdog before it noticed the fight was over.
 */
final class FrostFight {

    private static final Map<UUID, FrostFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color FROST_BLUE = Color.fromRGB(150, 220, 255);
    static final Color PALE_ICE = Color.fromRGB(210, 240, 255);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final FrostConfig config;
    private final AttackContext griefContext;

    /** Arena centre, fixed for the whole fight — where the Frozen Heart takes root in P3. */
    private final Location heartSpot;

    private final Campfires campfires;
    private final IceField iceField;
    private final FrozenPrison prison;
    private final Avalanche avalanche;
    private final FrozenHeart heart;
    private final PlayerMeter chill;

    private BukkitTask watchdog;

    private FrostFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new FrostConfig(plugin);
        // Grief's block writes only read ctx.instance() to reach the ledger; nothing here has an
        // attacker or a victim behind a campfire, an ice column or the Heart's shell.
        this.griefContext = new AttackContext(plugin, instance, null);
        this.heartSpot = instance.arena().center();

        this.campfires = new Campfires(this);
        this.iceField = new IceField(this);
        this.prison = new FrozenPrison(this);
        this.avalanche = new Avalanche(this);
        this.heart = new FrozenHeart(this);
        this.chill = buildChillMeter(instance);
    }

    static FrostFight of(BossInstance instance) {
        FrostFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        FrostFight fight = new FrostFight(instance);
        ACTIVE.put(instance.entity().getUniqueId(), fight);
        fight.startWatchdog();
        return fight;
    }

    /**
     * Arms the Chill meter once, at fight construction rather than per phase: the design runs it "all
     * fight" against a fixed set of campfires, and a meter that reset on every phase transition would
     * hand players a free purge for pushing damage across a seam.
     */
    private PlayerMeter buildChillMeter(BossInstance instance) {
        MeterSpec spec = MeterSpec.builder("chill", "Chill")
                .accent(FROST_BLUE)
                .cap(100.0)
                .gain(MeterConditions.always(), 3.0)
                .multiplier(MeterConditions.nearBoss(6.0), 2.0)
                .multiplier(MeterConditions.standingOn(Material.PACKED_ICE, Material.BLUE_ICE), 1.5)
                .cure(MeterConditions.nearLitCampfire(4.0), 20.0)
                .cure(MeterConditions.holding(Material.TORCH, Material.FLINT_AND_STEEL, Material.BLAZE_ROD), 20.0)
                .threshold(MeterThresholds.all(
                        MeterThresholds.freezeSolid(
                                config.num("prison-hold-ticks", 100),
                                config.dbl("prison-bleed-per-second", 1.6)),
                        (meter, player) -> prison.encase(player)),
                        0.0)
                .thresholdCooldown(config.dbl("chill-threshold-cooldown-seconds", 8.0))
                .warnAt(0.6)
                .hints("stand at a lit campfire", "FREEZING — get to warmth")
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

    FrostConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Location heartSpot() {
        return heartSpot.clone();
    }

    Campfires campfires() {
        return campfires;
    }

    IceField iceField() {
        return iceField;
    }

    FrozenPrison prison() {
        return prison;
    }

    Avalanche avalanche() {
        return avalanche;
    }

    FrozenHeart heart() {
        return heart;
    }

    PlayerMeter chill() {
        return chill;
    }

    World world() {
        return instance.arena().world();
    }

    int playerCount() {
        return Math.max(1, Arena.combatants(instance.arena().center(), instance.arena().radius()).size());
    }

    java.util.List<Player> combatants() {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("frost_queen")) {
                return;
            }
            release();
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    /**
     * Unhooks every listener this fight registered. Deliberately does <em>not</em> undo block work: the
     * campfires, the ice field, every prison shell and the Heart are all in the arena ledger, which
     * restores them wholesale a moment after this runs.
     */
    private void release() {
        ACTIVE.remove(instance.entity().getUniqueId());
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        try {
            prison.discard();
            heart.discard();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Frost Queen fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }
}
