package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.events.BeaconEvent;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.bosses.storm.StormPhases;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BallLightningAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ChainLightningAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GalePushAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ChainOfJudgmentAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.LightningRodsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.StormcallAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ThunderstormAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ThunderstrikeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TornadoAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.TempestMaul;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Storm Tyrant — an open-sky arena where the storm owns the vertical axis. Four phases
 * (100-70 / 70-45 / 45-15 / &lt;15), all built on {@code storm.StormPhases}: the Static Charge meter and
 * his four lightning rods run the whole fight, and each phase adds one more structural demand
 * (batch-1 spec §3.3):
 * <ol>
 *   <li><b>Static Build</b> — the charge meter and the rod rotation are taught.</li>
 *   <li><b>Floodplain</b> — real water floods the floor; any bolt landing in a connected pool damages
 *       everyone standing in it, so the safe floor is a shape rather than a distance.</li>
 *   <li><b>Eye of the Storm</b> — he ascends, fed by four Storm Pylons. He never goes invulnerable:
 *       while any pylon stands, only currently-discharged players land real damage on him — a rule
 *       about how you fight, not a wall. A rolling barrage sweeps the arena with one rotating gap.</li>
 *   <li><b>Stormcall</b> — he lands, permanently charged, and every rod now burns out after a single
 *       use — a real turn-order endgame.</li>
 * </ol>
 * He is never invulnerable in any of the four bands; see {@code StormPhaseMechanic}'s header.
 */
public final class StormTyrant extends Boss {

    private static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);
    private static final Color STORM_WHITE = Color.fromRGB(235, 245, 255);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public StormTyrant(WeaponsPlugin plugin) {
        super(plugin);

        ChainLightningAttack chainLightning = new ChainLightningAttack(plugin);
        ThunderstrikeAttack thunderstrike = new ThunderstrikeAttack(plugin);
        GalePushAttack galePush = new GalePushAttack(plugin);
        LightningRodsAttack lightningRods = new LightningRodsAttack(plugin);
        TornadoAttack tornado = new TornadoAttack(plugin);
        BallLightningAttack ballLightning = new BallLightningAttack(plugin);
        ThunderstormAttack thunderstorm = new ThunderstormAttack(plugin);
        StormcallAttack stormcall = new StormcallAttack(plugin);
        ChainOfJudgmentAttack chainOfJudgment = new ChainOfJudgmentAttack(plugin);

        this.phases = List.of(
                // Static Build: the charge meter and the rod rotation are taught. He is hittable the
                // entire time — the ask is learning to break off and discharge before you cap out.
                new BossPhase("Static Build", 1.0,
                        List.of(chainLightning, thunderstrike, galePush, lightningRods),
                        false, StormTyrant::onEnterPhase1,
                        instance -> StormPhases.staticBuild(instance, 0.70)),
                // Floodplain: real water across the floor. Still fully hittable — the safe ground is
                // now a conductive shape rather than a distance from the last bolt.
                new BossPhase("Floodplain", 0.70,
                        List.of(tornado, ballLightning, chainLightning, thunderstrike),
                        false, StormTyrant::onEnterPhase2,
                        instance -> StormPhases.floodplain(instance, 0.45)),
                // Eye of the Storm: he ascends behind four pylons. Never invulnerable — only the
                // currently-discharged land real hits while one still stands.
                new BossPhase("Eye of the Storm", 0.45,
                        List.of(thunderstorm, galePush, ballLightning, tornado, chainOfJudgment),
                        false, StormTyrant::onEnterPhase3,
                        instance -> StormPhases.eyeOfTheStorm(instance, 0.15)),
                // Stormcall: he lands, permanently charged, and every rod burns out after one use.
                new BossPhase("Stormcall", 0.15,
                        List.of(chainLightning, thunderstrike, galePush, ballLightning, tornado, stormcall, chainOfJudgment),
                        true, StormTyrant::onEnterEnrage,
                        StormPhases::stormcall));

        this.lootTable = new LootTable()
                .guaranteed(() -> new TempestMaul(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> stormforgedArmor(Material.IRON_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> stormforgedArmor(Material.IRON_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), StormTyrant::craftingMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), StormTyrant::thunderersCrown);
    }

    @Override
    public String id() {
        return "storm_tyrant";
    }

    @Override
    public Component displayName() {
        return Component.text("The Storm Tyrant", NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.WITHER_SKELETON;
    }

    @Override
    public List<BossPhase> phases() {
        return phases;
    }

    /**
     * The whole lightning kit was landing for far too little — every attack telegraphed hard and then
     * barely dented anyone, so the fight read as loud but harmless. Tripled at the boss level rather
     * than by editing each of the nine attacks' damage values, which keeps their relative weighting
     * (Thunderstrike still hits harder than Gale Push) intact.
     */
    @Override
    public double outgoingDamageMultiplier() {
        return configDouble("damage-multiplier", 3.0);
    }

    @Override
    public LootTable lootTable() {
        return lootTable;
    }

    /**
     * Four times the roster default, same reasoning as the Fallen King: getting everyone discharged,
     * surviving a full strike cycle, and wearing down Storm Pylons are physical acts under pressure
     * rather than a burst window, and {@code StormPhaseMechanic#progressSignal} resets the clock on
     * every one of them (including partial pylon damage — see {@code StormPylons#damageProgress}).
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /** Offset from his phase boundaries (0.70 / 0.45 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.86, 0.30}),
                new BeaconEvent(plugin, id(), new double[] {0.62, 0.24}));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.ELECTRIC_SPARK, "boss.storm_tyrant.ambient", Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                true, Biome.JAGGED_PEAKS);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("⚡ THE STORM TYRANT ⚡", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("The sky's furious sovereign descends", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("⚡ THE STORM TYRANT IS GROUNDED ⚡", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The storm breaks and the skies clear", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        }
        // Summon the storm. Restoring the world's prior weather when the fight ends is out of scope —
        // the arena simply stays stormy after the Storm Tyrant is defeated.
        if (instance.entity().getWorld() != null) {
            instance.entity().getWorld().setStorm(true);
            instance.entity().getWorld().setThundering(true);
        }
        // A rising column of sparks + a crackling crown of electric energy as the tyrant takes his stance.
        Fx.expandingRings(instance.plugin(), loc, Particle.ELECTRIC_SPARK, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), STORM_YELLOW, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.END_ROD, 25, 0.6);
        Fx.sound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.7f);
        Fx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 0.6f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Floodplain: a swirling spark-storm burst as the first water pours across the floor.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.ELECTRIC_SPARK, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), STORM_WHITE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.CLOUD, 7.0, 3, 2L);
        Fx.sound(loc, Sound.ITEM_ELYTRA_FLYING, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 0.9f);
        instance.showTitle(
                Component.text("Floodplain", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("The floor floods — watch where the water goes", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Eye of the Storm: bolts crack down in a tightening ring as he takes to the sky.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.END_ROD, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), STORM_YELLOW, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, STORM_WHITE, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        if (loc.getWorld() != null) {
            loc.getWorld().strikeLightningEffect(loc);
        }
        Fx.sound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 0.7f);
        instance.showTitle(
                Component.text("Eye of the Storm", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Only the discharged can touch him now", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Stormcall: a towering spark helix and a spinning lightning-rod crown overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 55, 0.8);
        Fx.coloredRing(loc, STORM_YELLOW, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.LIGHTNING_ROD, 1.4f, 100, 12.0);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !instance.entity().isValid()) {
                    cancel();
                    return;
                }
                Fx.helixFrame(instance.entity().getLocation(), Particle.ELECTRIC_SPARK, 1.3, 3, ticks * 0.5, ticks * 0.15);
                ticks++;
            }
        }.runTaskTimer(instance.plugin(), 0L, 1L);
        Fx.sound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.4f);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f);
        instance.showTitle(
                Component.text("⚡ STORMCALL ⚡", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("Every rod burns out after one use now", NamedTextColor.GRAY));
    }

    private static ItemStack stormforgedArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Stormforged Plate", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack craftingMaterials() {
        return new ItemStack(Material.COPPER_INGOT, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack thunderersCrown() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Thunderer's Crown", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Crackling with the fury of a thousand storms.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
