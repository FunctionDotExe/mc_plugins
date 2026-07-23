package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BubbleTrapAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.HealingAddsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.IceShardAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MaelstromAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.UndertowAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TidalSurgeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TridentThrowAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TsunamiAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WaterJetAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WhirlpoolAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.MaelstromTrident;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Tide Leviathan — a drowned sovereign of the deep. Four phases
 * (100-75 / 75-40 / 40-15 / enrage &lt;15) built on a shared water kit, with
 * Tidal Surge flooding the arena floor as the signature terrain-wrecking move
 * and an arena-wide drowning Maelstrom in enrage.
 */
public final class TideLeviathan extends Boss {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);
    private static final Color PALE = Color.fromRGB(160, 240, 250);
    private static final Color DEEP_TEAL = Color.fromRGB(10, 90, 130);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public TideLeviathan(WeaponsPlugin plugin) {
        super(plugin);

        WaterJetAttack waterJet = new WaterJetAttack(plugin);
        WhirlpoolAttack whirlpool = new WhirlpoolAttack(plugin);
        TidalSurgeAttack tidalSurge = new TidalSurgeAttack(plugin);
        TridentThrowAttack tridentThrow = new TridentThrowAttack(plugin);
        BubbleTrapAttack bubbleTrap = new BubbleTrapAttack(plugin);
        TsunamiAttack tsunami = new TsunamiAttack(plugin);
        IceShardAttack iceShard = new IceShardAttack(plugin);
        MaelstromAttack maelstrom = new MaelstromAttack(plugin);
        UndertowAttack undertow = new UndertowAttack(plugin);
        HealingAddsAttack abyssalPriests = new HealingAddsAttack(plugin, "tide_leviathan", "Abyssal Priest",
                EntityType.DROWNED, TEAL);

        this.phases = List.of(
                new BossPhase("Phase 1", 1.0,
                        List.of(waterJet, whirlpool, tidalSurge, tridentThrow),
                        false, TideLeviathan::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Coral Wardshells", NamedTextColor.DARK_AQUA),
                                Material.PRISMARINE_SHARD, TEAL, 0, false)),
                new BossPhase("The Depths Rise", 0.75,
                        List.of(bubbleTrap, tsunami, waterJet, whirlpool, abyssalPriests),
                        false, TideLeviathan::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Coral Wardshells", NamedTextColor.DARK_AQUA),
                                Material.PRISMARINE_SHARD, PALE, 1, false)),
                new BossPhase("Abyssal Wrath", 0.40,
                        List.of(iceShard, tidalSurge, tsunami, bubbleTrap, undertow),
                        false, TideLeviathan::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Abyssal Cores", NamedTextColor.BLUE),
                                Material.DARK_PRISMARINE, DEEP_TEAL, 2, false)),
                new BossPhase("Maelstrom", 0.15,
                        List.of(waterJet, whirlpool, tidalSurge, tsunami, iceShard, maelstrom, undertow),
                        true, TideLeviathan::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Heart of the Deep", NamedTextColor.AQUA),
                                Material.HEART_OF_THE_SEA, TEAL, 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new MaelstromTrident(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> tideplate(Material.DIAMOND_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> tideplate(Material.DIAMOND_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), TideLeviathan::prismarineShards)
                .weighted(configDouble("loot-heart-chance", 0.05), TideLeviathan::heartOfTheSea)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), TideLeviathan::crownOfTides);
    }

    @Override
    public String id() {
        return "tide_leviathan";
    }

    @Override
    public Component displayName() {
        return Component.text("The Tide Leviathan", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.DROWNED;
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
        return BossAmbiance.of(Particle.BUBBLE, "boss.tide_leviathan.ambient", Sound.AMBIENT_UNDERWATER_LOOP,
                true, Biome.DEEP_OCEAN);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("🌊 THE TIDE LEVIATHAN 🌊", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A drowned sovereign surges from the deep", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("🌊 THE TIDE LEVIATHAN IS DROWNED 🌊", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The waters fall still once more", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.TRIDENT));
        }
        // A geyser of water erupts as the leviathan rises to claim the arena.
        Fx.expandingRings(instance.plugin(), loc, Particle.SPLASH, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), TEAL, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.BUBBLE_COLUMN_UP, 30, 0.6);
        Fx.sound(loc, Sound.AMBIENT_UNDERWATER_LOOP, 1.0f, 0.7f);
        Fx.sound(loc, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The depths rise: a swelling surge of water engulfs the arena.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SPLASH, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), PALE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.BUBBLE, 7.0, 3, 2L);
        Fx.sound(loc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.8f, 0.8f);
        instance.showTitle(
                Component.text("The Depths Rise", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("The leviathan calls the ocean to its side", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Abyssal wrath: an inward-collapsing ring of frigid deep water.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.BUBBLE, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DEEP_TEAL, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, TEAL, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
        Fx.sound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.7f, 0.6f);
        instance.showTitle(
                Component.text("Abyssal Wrath", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("The cold of the deep answers its rage", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Maelstrom: a towering water vortex and a spinning trophy of the sea overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SPLASH, 55, 0.8);
        Fx.coloredRing(loc, TEAL, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.HEART_OF_THE_SEA, 1.4f, 100, 12.0);
        Fx.sound(loc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 0.4f);
        Fx.sound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
        instance.showTitle(
                Component.text("🌊 MAELSTROM 🌊", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("It will drag this world beneath the waves", NamedTextColor.GRAY));
    }

    private static ItemStack tideplate(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Tideplate", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack prismarineShards() {
        return new ItemStack(Material.PRISMARINE_SHARD, ThreadLocalRandom.current().nextInt(2, 6));
    }

    private static ItemStack heartOfTheSea() {
        return new ItemStack(Material.HEART_OF_THE_SEA);
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfTides() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of Tides", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Barnacle-crusted, dredged from the deepest trench.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
