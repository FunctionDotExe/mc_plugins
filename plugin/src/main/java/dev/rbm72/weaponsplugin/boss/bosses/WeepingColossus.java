package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.boss.events.BeaconEvent;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.mechanics.DriftingDiscField;
import dev.rbm72.weaponsplugin.boss.mechanics.EchoZoneMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.FloodingFloorMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.MechanicField;
import dev.rbm72.weaponsplugin.boss.mechanics.StackMeterMechanic;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CollapsingGazeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrenziedContractionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SorrowfulWailAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TearBarrageAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Tearfall;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Weeping Colossus — a screen-filling ghast that gets smaller and faster the more it hurts,
 * contracting phase over phase until its enrage form is a tiny, shrieking blur of a thing. Where
 * most bosses grow into their final form, this one shrinks into it.
 */
public final class WeepingColossus extends Boss {

    private static final Color SORROW_BLUE = Color.fromRGB(70, 120, 200);
    private static final double COLOSSAL_SCALE = 2.2;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public WeepingColossus(WeaponsPlugin plugin) {
        super(plugin);

        TearBarrageAttack tearBarrage = new TearBarrageAttack(plugin);
        SorrowfulWailAttack sorrowfulWail = new SorrowfulWailAttack(plugin);
        CollapsingGazeAttack collapsingGaze = new CollapsingGazeAttack(plugin);
        FrenziedContractionAttack frenziedContraction = new FrenziedContractionAttack(plugin);

        this.phases = List.of(
                // Rising Tears: its grief floods the arena and drains again on a rhythm. It costs the
                // group the floor rather than their health, and finally makes the terrain matter.
                new BossPhase("The Colossus", 1.0,
                        List.of(tearBarrage, sorrowfulWail),
                        false, WeepingColossus::onEnterPhase1,
                        this::risingTears),
                // Ungated: no mechanic, just the colossus and its grief. Pure race.
                new BossPhase("First Contraction", 0.72,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze),
                        false, WeepingColossus::onEnterPhase2),
                // Grief Echo: its signature. Everywhere it has stood detonates again a few seconds
                // later, so chasing it is exactly the wrong instinct — you have to lead it instead.
                new BossPhase("Second Contraction", 0.40,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze, frenziedContraction),
                        false, WeepingColossus::onEnterPhase3,
                        this::griefEcho),
                // Downpour Despair: the storm closes in and only the few patches of shelter hold it
                // off. Its last stand is fought permanently on the move between them.
                new BossPhase("Final Contraction", 0.15,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze, frenziedContraction),
                        true, WeepingColossus::onEnterEnrage,
                        this::downpourDespair));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Tearfall(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> sorrowplate(Material.DIAMOND_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> sorrowplate(Material.DIAMOND_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), WeepingColossus::tearMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), WeepingColossus::crownOfSorrow);
    }

    @Override
    public String id() {
        return "weeping_colossus";
    }

    @Override
    public Component displayName() {
        return Component.text("The Weeping Colossus", NamedTextColor.BLUE)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.GHAST;
    }

    @Override
    public double arenaRadius() {
        return configDouble("arena-radius", 43.0);
    }

    @Override
    public List<BossPhase> phases() {
        return phases;
    }

    @Override
    public LootTable lootTable() {
        return lootTable;
    }

    /**
     * Rising Tears: the waterline climbs, drowns everything under it, peaks and drains. Shared with the
     * Tide Leviathan's flood by design — it is one primitive, parameterised, rather than two
     * near-identical implementations.
     */
    private PhaseMechanic risingTears(BossInstance instance) {
        return new FloodingFloorMechanic(instance, "Rising Tears", SORROW_BLUE,
                configDouble("tears-rise-per-second", 0.22),
                configDouble("tears-max-rise", 4.5),
                configInt("tears-peak-hold-ticks", 100),
                configInt("tears-drained-pause-ticks", 90),
                configDouble("tears-drown-damage-per-second", 4.5),
                false);
    }

    /**
     * Grief Echo: its signature. Every few seconds the arena remembers where the colossus stood and
     * detonates there again. Melee players are punished for standing where it <em>was</em>, which is
     * exactly where chasing it leaves them — the answer is to lead it rather than follow.
     */
    private PhaseMechanic griefEcho(BossInstance instance) {
        return new EchoZoneMechanic(instance, "Grief Echo", SORROW_BLUE,
                configInt("echo-record-interval-ticks", 40),
                configInt("echo-delay-ticks", 100),
                configDouble("echo-radius", 4.0),
                configDouble("echo-damage", 14.0),
                configInt("echo-max-pending", 5));
    }

    /**
     * Downpour Despair: the storm blinds and grinds down anyone caught in the open, and only the
     * drifting patches of shelter hold it off. Late roster, so failing it hardens the colossus rather
     * than only hurting the player (design rule 4).
     */
    private PhaseMechanic downpourDespair(BossInstance instance) {
        MechanicField shelters = new DriftingDiscField(
                configInt("downpour-shelters", 2),
                configDouble("downpour-shelter-radius", 4.5),
                configDouble("downpour-shelter-drift", 2.0),
                Color.fromRGB(200, 220, 255),
                configDouble("downpour-shelter-spread", 0.5));
        return new StackMeterMechanic(instance, "Despair", SORROW_BLUE, shelters,
                configDouble("downpour-gain-per-second", 9.0),
                configDouble("downpour-drain-per-second", 20.0),
                configDouble("downpour-cap", 100.0),
                StackMeterMechanic.cripplingAndEmpower(
                        configDouble("downpour-damage", 20.0),
                        configInt("downpour-debuff-ticks", 80),
                        configDouble("downpour-harden-step", 0.04)),
                Component.text("DOWNPOUR", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("Get under shelter — it moves", NamedTextColor.GRAY),
                "sheltered", "EXPOSED — find shelter");
    }

    /** Offset from its phase boundaries (0.72 / 0.40 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.86, 0.30}),
                new BeaconEvent(plugin, id(), new double[] {0.62, 0.24}));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.SPLASH, "boss.weeping_colossus.ambient", Sound.ENTITY_GHAST_AMBIENT,
                false, null);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("☁ THE WEEPING COLOSSUS ☁", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A grief too large to fit in the sky", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("☁ THE COLOSSUS FALLS SILENT ☁", NamedTextColor.DARK_BLUE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("Whatever it was mourning, it's done now", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        AttributeInstance scaleAttr = instance.entity().getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(COLOSSAL_SCALE);
        }
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), SORROW_BLUE, 1.8f, 36, 0.7);
        Fx.burst(loc.clone().add(0, 1.5, 0), Particle.SPLASH, 26, 0.6);
        Fx.sound(loc, Sound.ENTITY_GHAST_AMBIENT, 1.0f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(0.82, 1.15);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SORROW_BLUE, 2.0f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.SPLASH, 6.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_GHAST_HURT, 1.1f, 0.9f);
        instance.showTitle(
                Component.text("First Contraction", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("It's pulling itself smaller on purpose", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(0.78, 1.2);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SORROW_BLUE, 1.8f, 40, 0.9);
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SPLASH, 34, 0.7);
        Fx.sound(loc, Sound.ENTITY_GHAST_HURT, 1.2f, 1.1f);
        instance.showTitle(
                Component.text("Second Contraction", NamedTextColor.DARK_BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("Smaller, faster, and considerably angrier", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(0.7, 1.3);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SPLASH, 55, 0.8);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 1.8f, 4.0, 26, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL_FIRE_FLAME, 6.5, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_GHAST_SCREAM, 1.4f, 1.5f);
        instance.showTitle(
                Component.text("☁ FINAL CONTRACTION ☁", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Barely a shadow of itself, and twice as fast", NamedTextColor.GRAY));
    }

    private static ItemStack sorrowplate(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Sorrowplate", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack tearMaterials() {
        Material[] picks = {Material.GHAST_TEAR, Material.PRISMARINE_CRYSTALS, Material.SOUL_SAND};
        Material picked = picks[ThreadLocalRandom.current().nextInt(picks.length)];
        return new ItemStack(picked, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfSorrow() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Core of Sorrow", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Small enough to hold now. It wasn't, once.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
