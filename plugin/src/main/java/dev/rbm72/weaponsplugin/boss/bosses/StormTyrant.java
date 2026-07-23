package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
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
 * The Storm Tyrant — the sky's furious sovereign. Four phases
 * (100-75 / 75-40 / 40-15 / enrage &lt;15) built on a shared lightning kit,
 * with Thunderstrike (real lightning) as the signature grief move and a
 * rolling arena-wide Stormcall barrage in enrage.
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
                new BossPhase("Phase 1", 1.0,
                        List.of(chainLightning, thunderstrike, galePush, lightningRods),
                        false, StormTyrant::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Storm Conduits", NamedTextColor.YELLOW),
                                Material.LIGHTNING_ROD, STORM_YELLOW, 0, false)),
                new BossPhase("Eye of the Storm", 0.75,
                        List.of(tornado, ballLightning, chainLightning, thunderstrike),
                        false, StormTyrant::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Storm Conduits", NamedTextColor.YELLOW),
                                Material.LIGHTNING_ROD, STORM_WHITE, 1, false)),
                new BossPhase("Wrath of the Sky", 0.40,
                        List.of(thunderstorm, galePush, ballLightning, tornado, chainOfJudgment),
                        false, StormTyrant::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Thunder Cores", NamedTextColor.GOLD),
                                Material.COPPER_BLOCK, STORM_YELLOW, 2, false)),
                new BossPhase("Maelstrom of Wrath", 0.15,
                        List.of(chainLightning, thunderstrike, galePush, ballLightning, tornado, stormcall, chainOfJudgment),
                        true, StormTyrant::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Eye of the Tempest", NamedTextColor.WHITE),
                                Material.NETHER_STAR, STORM_WHITE, 3, true)));

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

    @Override
    public LootTable lootTable() {
        return lootTable;
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
        // Eye of the Storm: a swirling spark-storm burst spins up around the tyrant.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.ELECTRIC_SPARK, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), STORM_WHITE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.CLOUD, 7.0, 3, 2L);
        Fx.sound(loc, Sound.ITEM_ELYTRA_FLYING, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 0.9f);
        instance.showTitle(
                Component.text("Eye of the Storm", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("The winds gather at his command", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Wrath of the Sky: bolts crack down in a tightening ring around the tyrant.
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
                Component.text("Wrath of the Sky", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("The heavens themselves turn against you", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Maelstrom of Wrath: a towering spark helix and a spinning lightning-rod crown overhead.
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
                Component.text("⚡ MAELSTROM OF WRATH ⚡", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("The Storm Tyrant unleashes the full tempest", NamedTextColor.GRAY));
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
