package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
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
                new BossPhase("The Colossus", 1.0,
                        List.of(tearBarrage, sorrowfulWail),
                        false, WeepingColossus::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Weeping Hide", NamedTextColor.BLUE),
                                Material.SOUL_LANTERN, SORROW_BLUE, 0, false)),
                new BossPhase("First Contraction", 0.72,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze),
                        false, WeepingColossus::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Tightening Hide", NamedTextColor.BLUE),
                                Material.PRISMARINE_CRYSTALS, SORROW_BLUE, 1, false)),
                new BossPhase("Second Contraction", 0.40,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze, frenziedContraction),
                        false, WeepingColossus::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Shrunken Husk", NamedTextColor.BLUE),
                                Material.GHAST_TEAR, SORROW_BLUE, 2, false)),
                new BossPhase("Final Contraction", 0.15,
                        List.of(tearBarrage, sorrowfulWail, collapsingGaze, frenziedContraction),
                        true, WeepingColossus::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Core of Sorrow", NamedTextColor.RED),
                                Material.NETHER_STAR, Color.fromRGB(180, 20, 20), 3, true)));

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
        return configDouble("arena-radius", 24.0);
    }

    @Override
    public List<BossPhase> phases() {
        return phases;
    }

    @Override
    public LootTable lootTable() {
        return lootTable;
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
