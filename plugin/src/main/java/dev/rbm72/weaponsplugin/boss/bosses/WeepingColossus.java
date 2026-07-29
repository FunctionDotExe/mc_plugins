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
import dev.rbm72.weaponsplugin.boss.bosses.weeping.WeepingPhases;
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
 * The Weeping Colossus — the horror is the <b>room</b>, not the creature. It is chained inside a machine
 * that closes on it and on you: four real piston wall sections advance inward on a visible cycle, real
 * dripstone falls from the ceiling and stays where it lands, and in P3 the walls seal overhead and the
 * lights genuinely go out (batch-3 spec §5). It is the inverse of the Void Sovereign — that boss removes
 * floor, this one removes <em>space</em>, and the Colossus shrinks and speeds up as the room does.
 * <ol>
 *   <li><b>The Chamber</b> — learn the wall cycle and jam two sections.</li>
 *   <li><b>Dripstone</b> — the danger goes vertical in a room already closing horizontally.</li>
 *   <li><b>The Dark</b> — real block darkness; torches are construction, and it snuffs them.</li>
 *   <li><b>The Box</b> — the walls halt, and the finale is fought in the room the group kept.</li>
 * </ol>
 * Every phase the room is smaller and there is no phase that gives space back, but
 * {@code weeping.PistonWalls#minChamber()} is a hard floor that scales with the group, so the final
 * chamber is always winnable — §5.8 names that as the real work of this boss.
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
                // The Chamber: full-size room, slow huge Colossus, and the walls begin to advance.
                new BossPhase("The Chamber", 1.0,
                        List.of(tearBarrage, sorrowfulWail),
                        false, WeepingColossus::onEnterPhase1,
                        instance -> WeepingPhases.chamber(instance, 0.76)),
                // Dripstone: ceiling awareness on top of wall management on top of a boss.
                new BossPhase("Dripstone", 0.76,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze),
                        false, WeepingColossus::onEnterPhase2,
                        instance -> WeepingPhases.dripstone(instance, 0.52)),
                // The Dark: the ceiling seals, and piston work happens by torchlight the boss puts out.
                new BossPhase("The Dark", 0.52,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze, frenziedContraction),
                        false, WeepingColossus::onEnterPhase3,
                        instance -> WeepingPhases.theDark(instance, 0.26)),
                // The Box: a close-quarters duel in terrain the fight created.
                new BossPhase("The Box", 0.26,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze, frenziedContraction),
                        true, WeepingColossus::onEnterEnrage,
                        WeepingPhases::theBox));

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
     * Four times the roster default. Jamming a wall means walking to it, cutting a feed under fire and
     * getting back, and {@code WeepingPhaseMechanic#progressSignal} counts every jam — a group holding
     * the room open is doing the fight even while the boss's health barely moves.
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /** Offset from its phase boundaries (0.76 / 0.52 / 0.26) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.86, 0.38}),
                new BeaconEvent(plugin, id(), new double[] {0.64, 0.32}));
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
                Component.text("Dripstone", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("Look up — and stop standing together", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(0.78, 1.2);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SORROW_BLUE, 1.8f, 40, 0.9);
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SPLASH, 34, 0.7);
        Fx.sound(loc, Sound.ENTITY_GHAST_HURT, 1.2f, 1.1f);
        instance.showTitle(
                Component.text("The Dark", NamedTextColor.DARK_BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("The ceiling closes — get torches up and keep them up", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(0.7, 1.3);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SPLASH, 55, 0.8);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 1.8f, 4.0, 26, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL_FIRE_FLAME, 6.5, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_GHAST_SCREAM, 1.4f, 1.5f);
        instance.showTitle(
                Component.text("☁ THE BOX ☁", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
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
