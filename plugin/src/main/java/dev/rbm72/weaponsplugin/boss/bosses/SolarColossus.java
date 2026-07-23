package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FistThrowAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.HealingAddsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MeteorAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.RadiantBeamCrossAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.RadiantNovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlindingRadianceAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SeismicSlamAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SolarBeamAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SolarFlareAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SunfireRainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SupernovaAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Dawnbreaker;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Solar Colossus — a towering iron titan overloaded with captured
 * sunlight. Big, slow, and heavy-hitting: 500 HP over four phases
 * (100-75 / 75-40 / 40-15 / enrage &lt;15), with Seismic Slam as the
 * signature terrain-wrecking move and a self-destructive Supernova in enrage.
 */
public final class SolarColossus extends Boss {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);
    private static final Color RADIANT_WHITE = Color.fromRGB(255, 250, 220);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public SolarColossus(WeaponsPlugin plugin) {
        super(plugin);

        SolarBeamAttack solarBeam = new SolarBeamAttack(plugin);
        SeismicSlamAttack seismicSlam = new SeismicSlamAttack(plugin);
        FistThrowAttack fistThrow = new FistThrowAttack(plugin);
        RadiantNovaAttack radiantNova = new RadiantNovaAttack(plugin);
        MeteorAttack meteor = new MeteorAttack(plugin);

        SolarFlareAttack solarFlare = new SolarFlareAttack(plugin);
        SunfireRainAttack sunfireRain = new SunfireRainAttack(plugin);
        RadiantBeamCrossAttack radiantBeamCross = new RadiantBeamCrossAttack(plugin);
        SupernovaAttack supernova = new SupernovaAttack(plugin);
        BlindingRadianceAttack blindingRadiance = new BlindingRadianceAttack(plugin);
        HealingAddsAttack sunAcolytes = new HealingAddsAttack(plugin, "solar_colossus", "Sun Acolyte",
                EntityType.BLAZE, SOLAR_GOLD);

        this.phases = List.of(
                new BossPhase("Phase 1", 1.0,
                        List.of(solarBeam, seismicSlam, fistThrow, radiantNova),
                        false, SolarColossus::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Solar Conduits", NamedTextColor.GOLD),
                                Material.GLOWSTONE, SOLAR_GOLD, 0, false)),
                new BossPhase("Sunforged", 0.75,
                        List.of(meteor, solarFlare, solarBeam, seismicSlam, sunAcolytes),
                        false, SolarColossus::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Solar Conduits", NamedTextColor.GOLD),
                                Material.GLOWSTONE, SOLAR_GOLD, 1, false)),
                new BossPhase("Zenith", 0.40,
                        List.of(sunfireRain, radiantBeamCross, seismicSlam, fistThrow, blindingRadiance),
                        false, SolarColossus::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Radiant Cores", NamedTextColor.YELLOW),
                                Material.BEACON, RADIANT_WHITE, 2, false)),
                new BossPhase("Supernova Imminent", 0.15,
                        List.of(solarBeam, seismicSlam, fistThrow, meteor, radiantNova, supernova, blindingRadiance),
                        true, SolarColossus::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Fractured Core", NamedTextColor.RED),
                                Material.NETHER_STAR, RADIANT_WHITE, 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Dawnbreaker(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> sunplateArmor(Material.GOLDEN_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> sunplateArmor(Material.GOLDEN_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), SolarColossus::goldIngots)
                .weighted(configDouble("loot-materials-chance", 0.6), SolarColossus::glowstoneDust)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), SolarColossus::radiantCrown);
    }

    @Override
    public String id() {
        return "solar_colossus";
    }

    @Override
    public Component displayName() {
        return Component.text("The Solar Colossus", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.IRON_GOLEM;
    }

    @Override
    public double maxHealth() {
        return configDouble("max-health", 500.0);
    }

    @Override
    public double maxCraterRadius() {
        return configDouble("max-crater-radius", 7.0);
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
        return BossAmbiance.of(Particle.END_ROD, "boss.solar_colossus.ambient", Sound.BLOCK_BEACON_AMBIENT,
                true, Biome.DESERT);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("☀ THE SOLAR COLOSSUS ☀", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A titan forged from the light of a dying star", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("☀ THE SOLAR COLOSSUS GOES DARK ☀", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("Its light finally fades", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The colossus ignites: a rising pillar of light and a corona of end rods.
        Fx.expandingRings(instance.plugin(), loc, Particle.END_ROD, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), SOLAR_GOLD, 1.8f, 45, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.END_ROD, 30, 0.6);
        Fx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Sunforged: molten light hardens over its frame.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.END_ROD, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SOLAR_GOLD, 1.7f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.FLAME, 7.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.7f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
        instance.showTitle(
                Component.text("Sunforged", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Its plating glows white-hot", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Zenith: the sun reaches its peak — a blinding corona collapses inward.
        Fx.flash(loc.clone().add(0, 1.2, 0), 3);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), RADIANT_WHITE, 2.0f, 40, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, SOLAR_GOLD, 1.5f, 6.0 - ring * 1.5, 22, ring * 0.6);
        }
        Fx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        instance.showTitle(
                Component.text("Zenith", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("The sun burns directly overhead", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Supernova Imminent: the colossus destabilizes, a star gathering in its core.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.END_ROD, 55, 0.8);
        Fx.coloredRing(loc, SOLAR_GOLD, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.GLOWSTONE, 1.4f, 100, 12.0);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !instance.entity().isValid()) {
                    cancel();
                    return;
                }
                Fx.helixFrame(instance.entity().getLocation(), Particle.END_ROD, 1.3, 3, ticks * 0.5, ticks * 0.15);
                ticks++;
            }
        }.runTaskTimer(instance.plugin(), 0L, 1L);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 1.0f);
        Fx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
        instance.showTitle(
                Component.text("☀ SUPERNOVA IMMINENT ☀", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("It will take the whole arena with it", NamedTextColor.GRAY));
    }

    private static ItemStack sunplateArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Sunplate", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack goldIngots() {
        return new ItemStack(Material.GOLD_INGOT, ThreadLocalRandom.current().nextInt(2, 6));
    }

    private static ItemStack glowstoneDust() {
        return new ItemStack(Material.GLOWSTONE_DUST, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack radiantCrown() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Radiant Crown", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("A shard of trapped daylight, warm to the touch.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
