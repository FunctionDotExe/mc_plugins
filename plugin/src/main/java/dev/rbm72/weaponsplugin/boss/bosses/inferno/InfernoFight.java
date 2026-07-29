package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.meter.MeterConditions;
import dev.rbm72.weaponsplugin.boss.meter.MeterSpec;
import dev.rbm72.weaponsplugin.boss.meter.PlayerMeter;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Inferno Warlord's fight-scoped state: the Burning meter, the cauldrons and bucket economy, the
 * rising lava tile registry, fire trails, TNT clusters, magma hazards and burning logs. One per live
 * fight, same registry+untracked-watchdog pattern as {@code NecroFight}/{@code StormFight} — the lava
 * tile map in particular has to outlive individual phase transitions, since P4's opening flood reads
 * exactly which tiles P2 and P3's group turned into stone and obsidian.
 * <p>
 * <b>Burning</b> is deliberately built without a {@link dev.rbm72.weaponsplugin.boss.meter.MeterThreshold}.
 * Every other skin in the roster ends in one big detonation at the cap; the design text for this one
 * asks for "steady damage that ignores most mitigation and can't be outhealed cheaply" instead — a
 * punishment that is continuous, not a bomb. {@link InfernoPhaseMechanic#tick()} reads
 * {@link PlayerMeter#fraction} every pulse and applies damage proportional to it directly, which is the
 * alternative the design brief itself sanctions over forcing a threshold payload where one does not fit.
 * <p>
 * <b>Heat Aura</b> (§1.4: "passive in melee range... continuous Burning application while in melee") is
 * not its own class. It is exactly {@link MeterConditions#nearBoss} as a gain condition on the Burning
 * meter — the same trick {@code StormFight}'s Static Charge meter uses for "×2 near a charged ally" — so
 * a whole mechanic collapses into one line of the meter spec instead of a fourth ticking system.
 */
final class InfernoFight {

    private static final Map<UUID, InfernoFight> ACTIVE = new HashMap<>();
    private static final long WATCHDOG_INTERVAL_TICKS = 10L;

    static final Color EMBER = Color.fromRGB(255, 120, 0);
    static final Color DEEP_FIRE = Color.fromRGB(200, 40, 0);
    static final Color STEAM = Color.fromRGB(200, 220, 230);

    private final WeaponsPlugin plugin;
    private final BossInstance instance;
    private final InfernoConfig config;
    private final AttackContext griefContext;

    private final Cauldrons cauldrons;
    private final RisingLava lava;
    private final FireTrails trails;
    private final TntClusters clusters;
    private final MagmaHazards magma;
    private final BurningLogs logs;
    private final CinderNova cinderNova;
    private final PlayerMeter burning;
    private final Dousing dousing;

    private BukkitTask watchdog;

    private InfernoFight(BossInstance instance) {
        this.plugin = instance.plugin();
        this.instance = instance;
        this.config = new InfernoConfig(plugin);
        this.griefContext = new AttackContext(plugin, instance, null);

        this.cauldrons = new Cauldrons(this);
        this.lava = new RisingLava(this);
        this.trails = new FireTrails(this);
        this.clusters = new TntClusters(this);
        this.magma = new MagmaHazards(this);
        this.logs = new BurningLogs(this);
        this.cinderNova = new CinderNova(this);
        this.burning = buildBurningMeter(instance);
        this.dousing = new Dousing();
        plugin.getServer().getPluginManager().registerEvents(dousing, plugin);

        cauldrons.place();
    }

    static InfernoFight of(BossInstance instance) {
        InfernoFight existing = ACTIVE.get(instance.entity().getUniqueId());
        if (existing != null) {
            return existing;
        }
        InfernoFight fight = new InfernoFight(instance);
        ACTIVE.put(instance.entity().getUniqueId(), fight);
        fight.startWatchdog();
        return fight;
    }

    private PlayerMeter buildBurningMeter(BossInstance instance) {
        double cauldronRadius = config.dbl("burning-cauldron-radius", 2.4);
        MeterSpec spec = MeterSpec.builder("burning", "Burning")
                .accent(EMBER)
                .cap(100.0)
                // Heat Aura: continuous gain in melee range, the anti-facetank tax (§1.4/§1.6).
                .gain(MeterConditions.nearBoss(config.dbl("heat-aura-radius", 4.0)),
                        config.dbl("heat-aura-gain-per-second", 7.0))
                // Standing in a fire trail, a fuse, or real rising lava — MeterConditions.inFire() covers
                // vanilla LAVA contact as well as FIRE, so lava contact alone already starts the clock.
                .gain(MeterConditions.inFire(), config.dbl("burning-fire-gain-per-second", 10.0))
                .cure(MeterConditions.inWater(), config.dbl("burning-cure-water-per-second", 45.0))
                .cure(MeterConditions.nearBlock(cauldronRadius, Material.WATER_CAULDRON),
                        config.dbl("burning-cure-cauldron-per-second", 35.0))
                .warnAt(0.5)
                .hints("step in water or reach a cauldron", "BURNING")
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

    InfernoConfig config() {
        return config;
    }

    AttackContext griefContext() {
        return griefContext;
    }

    Cauldrons cauldrons() {
        return cauldrons;
    }

    RisingLava lava() {
        return lava;
    }

    FireTrails trails() {
        return trails;
    }

    TntClusters clusters() {
        return clusters;
    }

    MagmaHazards magma() {
        return magma;
    }

    BurningLogs logs() {
        return logs;
    }

    CinderNova cinderNova() {
        return cinderNova;
    }

    PlayerMeter burning() {
        return burning;
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

    boolean solo() {
        return playerCount() <= 1;
    }

    // ------------------------------------------------------------- lifecycle

    private void startWatchdog() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.bossManager().isLive("inferno_warlord")) {
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
            cauldrons.discardAll();
            lava.discardAll();
            trails.discardAll();
            clusters.discardAll();
            HandlerList.unregisterAll(dousing);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Inferno Warlord fight teardown threw — the arena ledger still restores its terrain.", e);
        }
    }

    // ---------------------------------------------------------------- dousing

    /**
     * "Another player dousing you" (§1.4 Burning row) — an ally right-clicking a burning combatant with
     * a water bucket cures faster and more fully than self-dousing, which is what the §1.5 multiplayer
     * section means by "dousing a burning teammate is faster than self-dousing". The bucket is spent
     * either way, so it still competes with every other use for the same contested resource.
     */
    private final class Dousing implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDouseAlly(PlayerInteractEntityEvent event) {
            if (!(event.getRightClicked() instanceof Player target) || !Arena.isCombatant(target)) {
                return;
            }
            ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
            if (held == null || held.getType() != Material.WATER_BUCKET) {
                return;
            }
            if (burning.value(target) <= 0) {
                return;
            }
            event.setCancelled(true);
            burning.cure(target, config.dbl("burning-ally-douse-cure-amount", 70.0));
            ItemStack empty = new ItemStack(Material.BUCKET);
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                event.getPlayer().getInventory().setItemInOffHand(empty);
            } else {
                event.getPlayer().getInventory().setItemInMainHand(empty);
            }
            Fx.burst(target.getLocation().add(0, 1, 0), Particle.CLOUD, 26, 0.5);
            Fx.sound(target.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.1f, 1.0f);
            Fx.sound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.9f);
        }
    }
}
