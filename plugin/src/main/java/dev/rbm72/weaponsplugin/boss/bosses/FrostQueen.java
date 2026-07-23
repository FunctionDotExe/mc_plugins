package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AbsoluteZeroAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AfflictionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AvalancheAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlizzardAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ChillingTouchAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrostArmorAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrostNovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrozenPrisonAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GlacierSpikesAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.IceLanceAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrozenHeartAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.GlacialScepter;
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
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Frost Queen — winter's cruel sovereign. Four phases
 * (100-75 / 75-40 / 40-15 / enrage &lt;15) built on a shared frost kit, with
 * Glacier Spikes as the signature terrain-wrecking move and an arena-wide
 * Absolute Zero flash-freeze in enrage.
 */
public final class FrostQueen extends Boss {

    private static final Color FROST_BLUE = Color.fromRGB(150, 220, 255);
    private static final Color PALE_ICE = Color.fromRGB(210, 240, 255);
    private static final Color ARMOR_BLUE = Color.fromRGB(140, 205, 245);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public FrostQueen(WeaponsPlugin plugin) {
        super(plugin);

        FrostNovaAttack frostNova = new FrostNovaAttack(plugin);
        IceLanceAttack iceLance = new IceLanceAttack(plugin);
        GlacierSpikesAttack glacierSpikes = new GlacierSpikesAttack(plugin);
        ChillingTouchAttack chillingTouch = new ChillingTouchAttack(plugin);
        FrostArmorAttack frostArmor = new FrostArmorAttack(plugin);
        BlizzardAttack blizzard = new BlizzardAttack(plugin);
        AvalancheAttack avalanche = new AvalancheAttack(plugin);
        FrozenPrisonAttack frozenPrison = new FrozenPrisonAttack(plugin);
        AbsoluteZeroAttack absoluteZero = new AbsoluteZeroAttack(plugin);
        FrozenHeartAttack frozenHeart = new FrozenHeartAttack(plugin);
        AfflictionAttack deepFrostbite = new AfflictionAttack(plugin, "frost_queen", "Deep Frostbite", FROST_BLUE,
                Sound.BLOCK_GLASS_BREAK);

        this.phases = List.of(
                new BossPhase("Phase 1", 1.0,
                        List.of(frostNova, iceLance, glacierSpikes, chillingTouch, frostArmor),
                        false, FrostQueen::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Frozen Wardstones", NamedTextColor.AQUA),
                                Material.BLUE_ICE, FROST_BLUE, 0, false)),
                new BossPhase("Winter Deepens", 0.75,
                        List.of(blizzard, avalanche, iceLance, glacierSpikes, deepFrostbite),
                        false, FrostQueen::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Frozen Wardstones", NamedTextColor.AQUA),
                                Material.BLUE_ICE, PALE_ICE, 1, false)),
                new BossPhase("Heart of Ice", 0.40,
                        List.of(frozenPrison, frostNova, iceLance, blizzard, frozenHeart, deepFrostbite),
                        false, FrostQueen::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Glacial Cores", NamedTextColor.DARK_AQUA),
                                Material.PACKED_ICE, FROST_BLUE, 2, false)),
                new BossPhase("Absolute Winter", 0.15,
                        List.of(frostNova, iceLance, glacierSpikes, blizzard, avalanche, absoluteZero, frozenHeart),
                        true, FrostQueen::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Heart of Winter", NamedTextColor.WHITE),
                                Material.NETHER_STAR, PALE_ICE, 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new GlacialScepter(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> frostforgedRegalia(Material.LEATHER_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> frostforgedRegalia(Material.LEATHER_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), FrostQueen::craftingMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), FrostQueen::crownOfWinter);
    }

    @Override
    public String id() {
        return "frost_queen";
    }

    @Override
    public Component displayName() {
        return Component.text("The Frost Queen", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.STRAY;
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
        return BossAmbiance.of(Particle.SNOWFLAKE, "boss.frost_queen.ambient", Sound.WEATHER_RAIN,
                true, Biome.FROZEN_PEAKS);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("❄ THE FROST QUEEN ❄", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("Winter's cruel sovereign descends", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("❄ THE FROST QUEEN IS SHATTERED ❄", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The endless winter finally thaws", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            equipment.setHelmet(dyedArmor(Material.LEATHER_HELMET));
            equipment.setChestplate(dyedArmor(Material.LEATHER_CHESTPLATE));
            equipment.setLeggings(dyedArmor(Material.LEATHER_LEGGINGS));
            equipment.setBoots(dyedArmor(Material.LEATHER_BOOTS));
        }
        // A rising column of snow + a spinning crown of frost as the queen takes her throne.
        Fx.expandingRings(instance.plugin(), loc, Particle.SNOWFLAKE, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), FROST_BLUE, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SNOWBALL, 25, 0.6);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.6f);
        Fx.sound(loc, Sound.BLOCK_GLASS_PLACE, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Winter deepens: a swirling snow-storm burst engulfs the arena.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SNOWFLAKE, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), PALE_ICE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.SNOWFLAKE, 7.0, 3, 2L);
        Fx.sound(loc, Sound.WEATHER_RAIN, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 0.8f);
        instance.showTitle(
                Component.text("Winter Deepens", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("The queen calls the storm to her side", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Heart of Ice: an inward-collapsing ring of frost hardening around the queen.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.ITEM_SNOWBALL, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), FROST_BLUE, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, PALE_ICE, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 0.4f);
        instance.showTitle(
                Component.text("Heart of Ice", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("No warmth remains in her frozen heart", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Absolute Winter: a towering frost helix and a spinning ice crown overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SNOWFLAKE, 55, 0.8);
        Fx.coloredRing(loc, FROST_BLUE, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.BLUE_ICE, 1.4f, 100, 12.0);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.4f);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        instance.showTitle(
                Component.text("❄ ABSOLUTE WINTER ❄", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true),
                Component.text("She will bury this world in ice", NamedTextColor.GRAY));
    }

    private static ItemStack dyedArmor(Material material) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(ARMOR_BLUE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack frostforgedRegalia(Material material) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(ARMOR_BLUE);
            meta.displayName(Component.text("Frostforged Regalia", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack craftingMaterials() {
        return new ItemStack(Material.BLUE_ICE, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfWinter() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of Winter", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Frozen for a thousand years, and colder still.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
