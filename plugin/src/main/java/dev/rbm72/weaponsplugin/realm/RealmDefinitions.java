package dev.rbm72.weaponsplugin.realm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Biome;

/**
 * Every realm's definition in one place: a themed dimension per boss, each built from
 * {@link RealmChunkGenerator} (the one generic arena builder) but driven by an {@link ArenaTheme}
 * chosen to fit that boss — {@link FloorStyle} and {@link WallStyle} vary the actual construction
 * (not just materials) so no two bosses share a silhouette, towers/pillars/moats are only used where
 * they won't get in that boss's own moveset's way (open floors for fliers and big AoE bosses), and
 * palette/biome are pulled from each boss's own existing {@code BossAmbiance} so the realm always
 * matches the fight it already puts on.
 */
public final class RealmDefinitions {

    private RealmDefinitions() {
    }

    public static void registerAll(RealmRegistry registry) {
        registry.register(new Realm("fallen_kingdom", "realm_fallen_kingdom",
                Component.text("Fallen Kingdom", NamedTextColor.DARK_RED),
                Material.NETHERITE_SCRAP,
                new ArenaTheme(
                        FloorStyle.FLAT, WallStyle.BATTLEMENT,
                        Material.POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE,
                        Material.GILDED_BLACKSTONE,
                        Material.POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE,
                        true, Material.POLISHED_BLACKSTONE_BRICKS, Material.DEEPSLATE_TILES,
                        Material.SOUL_LANTERN, Material.POLISHED_BLACKSTONE_BRICKS, null, false,
                        World.Environment.NETHER, Biome.CRIMSON_FOREST,
                        Particle.DUST, Color.fromRGB(200, 20, 90), false, false),
                "fallen_king"));

        registry.register(new Realm("frozen_reach", "realm_frozen_reach",
                Component.text("The Frozen Reach", NamedTextColor.AQUA),
                Material.DIAMOND,
                new ArenaTheme(
                        FloorStyle.UNDULATE, WallStyle.SPIKES,
                        Material.PACKED_ICE, Material.SNOW_BLOCK, Material.BLUE_ICE,
                        Material.PACKED_ICE, Material.SNOW_BLOCK,
                        false, Material.PACKED_ICE, Material.BLUE_ICE,
                        Material.SEA_LANTERN, Material.PACKED_ICE, Material.WATER, false,
                        World.Environment.NORMAL, Biome.FROZEN_PEAKS,
                        Particle.SNOWFLAKE, null, false, false),
                "frost_queen"));

        registry.register(new Realm("stormreach_peak", "realm_stormreach_peak",
                Component.text("Stormreach Peak", NamedTextColor.YELLOW),
                Material.AMETHYST_SHARD,
                new ArenaTheme(
                        FloorStyle.UNDULATE, WallStyle.OBELISKS,
                        Material.CALCITE, Material.SMOOTH_BASALT, Material.COPPER_BLOCK,
                        Material.CALCITE, Material.SMOOTH_BASALT,
                        false, Material.CALCITE, Material.SMOOTH_BASALT,
                        Material.END_ROD, null, null, false,
                        World.Environment.NORMAL, Biome.JAGGED_PEAKS,
                        Particle.ELECTRIC_SPARK, null, false, true),
                "storm_tyrant"));

        registry.register(new Realm("cinder_wastes", "realm_cinder_wastes",
                Component.text("The Cinder Wastes", NamedTextColor.GOLD),
                Material.MAGMA_CREAM,
                new ArenaTheme(
                        FloorStyle.TERRACES_UP, WallStyle.SPIKES,
                        Material.BLACKSTONE, Material.POLISHED_BASALT, Material.MAGMA_BLOCK,
                        Material.BLACKSTONE, Material.GILDED_BLACKSTONE,
                        true, Material.BLACKSTONE, Material.POLISHED_BASALT,
                        Material.SHROOMLIGHT, null, Material.LAVA, false,
                        World.Environment.NETHER, Biome.BASALT_DELTAS,
                        Particle.FLAME, null, false, false),
                "inferno_warlord"));

        registry.register(new Realm("rotmire", "realm_rotmire",
                Component.text("The Rotmire", NamedTextColor.GREEN),
                Material.FERMENTED_SPIDER_EYE,
                new ArenaTheme(
                        FloorStyle.UNDULATE, WallStyle.MOUNDS,
                        Material.MOSSY_COBBLESTONE, Material.MUD_BRICKS, Material.SCULK,
                        Material.MUD_BRICKS, Material.MOSSY_STONE_BRICKS,
                        false, Material.MUD_BRICKS, Material.MOSSY_STONE_BRICKS,
                        Material.OCHRE_FROGLIGHT, Material.MOSSY_STONE_BRICKS, Material.WATER, false,
                        World.Environment.NORMAL, Biome.SWAMP,
                        Particle.SPORE_BLOSSOM_AIR, null, false, false),
                "plague_warden"));

        registry.register(new Realm("void_sanctum", "realm_void_sanctum",
                Component.text("The Void Sanctum", NamedTextColor.DARK_PURPLE),
                Material.ENDER_EYE,
                new ArenaTheme(
                        FloorStyle.FLAT, WallStyle.OBELISKS,
                        Material.PURPUR_BLOCK, Material.END_STONE_BRICKS, Material.OBSIDIAN,
                        Material.PURPUR_BLOCK, Material.PURPUR_PILLAR,
                        false, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS,
                        Material.END_ROD, Material.PURPUR_PILLAR, null, false,
                        World.Environment.NORMAL, Biome.THE_END,
                        Particle.PORTAL, null, false, false),
                "void_sovereign"));

        registry.register(new Realm("sunspire", "realm_sunspire",
                Component.text("The Sunspire", NamedTextColor.GOLD),
                Material.GLOWSTONE_DUST,
                new ArenaTheme(
                        FloorStyle.TERRACES_UP, WallStyle.BATTLEMENT,
                        Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.GOLD_BLOCK,
                        Material.SMOOTH_SANDSTONE, Material.CHISELED_SANDSTONE,
                        true, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE,
                        Material.GLOWSTONE, Material.CHISELED_SANDSTONE, null, false,
                        World.Environment.NORMAL, Biome.DESERT,
                        Particle.END_ROD, null, false, false),
                "solar_colossus"));

        registry.register(new Realm("abyssal_trench", "realm_abyssal_trench",
                Component.text("The Abyssal Trench", NamedTextColor.DARK_AQUA),
                Material.PRISMARINE_CRYSTALS,
                new ArenaTheme(
                        FloorStyle.FLAT, WallStyle.ARCHES,
                        Material.PRISMARINE, Material.DARK_PRISMARINE, Material.PRISMARINE_BRICKS,
                        Material.PRISMARINE, Material.DARK_PRISMARINE,
                        false, Material.PRISMARINE, Material.DARK_PRISMARINE,
                        Material.SEA_LANTERN, null, Material.WATER, true,
                        World.Environment.NORMAL, Biome.DEEP_OCEAN,
                        Particle.BUBBLE, null, false, false),
                "tide_leviathan"));

        registry.register(new Realm("windswept_barrow", "realm_windswept_barrow",
                Component.text("The Windswept Barrow", NamedTextColor.DARK_RED),
                Material.PHANTOM_MEMBRANE,
                new ArenaTheme(
                        FloorStyle.UNDULATE, WallStyle.ARCHES,
                        Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.CHISELED_STONE_BRICKS,
                        Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                        false, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                        Material.LANTERN, null, null, false,
                        World.Environment.NORMAL, Biome.WINDSWEPT_HILLS,
                        Particle.FLAME, null, false, false),
                "dragon_elder"));

        registry.register(new Realm("hollow_graveyard", "realm_hollow_graveyard",
                Component.text("The Hollow Graveyard", NamedTextColor.DARK_GREEN),
                Material.WITHER_ROSE,
                new ArenaTheme(
                        FloorStyle.FLAT, WallStyle.MOUNDS,
                        Material.MOSSY_COBBLESTONE, Material.COBBLESTONE, Material.SCULK,
                        Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
                        false, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
                        Material.SOUL_LANTERN, Material.MOSSY_COBBLESTONE_WALL, null, false,
                        World.Environment.NORMAL, Biome.DARK_FOREST,
                        Particle.SCULK_SOUL, null, false, false),
                "necro_overlord"));

        registry.register(new Realm("flesh_atrium", "realm_flesh_atrium",
                Component.text("The Flesh Atrium", NamedTextColor.DARK_GREEN),
                Material.ROTTEN_FLESH,
                new ArenaTheme(
                        FloorStyle.TERRACES_DOWN, WallStyle.ARCHES,
                        Material.CALCITE, Material.DRIPSTONE_BLOCK, Material.CRYING_OBSIDIAN,
                        Material.DEEPSLATE_TILES, Material.DRIPSTONE_BLOCK,
                        true, Material.DEEPSLATE_TILES, Material.DRIPSTONE_BLOCK,
                        Material.CRYING_OBSIDIAN, Material.DEEPSLATE_TILES, null, false,
                        World.Environment.NORMAL, Biome.DRIPSTONE_CAVES,
                        Particle.ITEM_COBWEB, null, false, false),
                "grafted_horror"));

        registry.register(new Realm("threefold_crypt", "realm_threefold_crypt",
                Component.text("The Threefold Crypt", NamedTextColor.AQUA),
                Material.WITHER_SKELETON_SKULL,
                new ArenaTheme(
                        FloorStyle.FLAT, WallStyle.SPIKES,
                        Material.BLACKSTONE, Material.CHISELED_NETHER_BRICKS, Material.NETHER_BRICKS,
                        Material.NETHER_BRICKS, Material.CHISELED_NETHER_BRICKS,
                        true, Material.NETHER_BRICKS, Material.CHISELED_NETHER_BRICKS,
                        Material.SOUL_LANTERN, Material.NETHER_BRICK_FENCE, null, false,
                        World.Environment.NETHER, Biome.WARPED_FOREST,
                        Particle.SOUL, null, false, false),
                "threefold_bane"));

        registry.register(new Realm("starless_expanse", "realm_starless_expanse",
                Component.text("The Starless Expanse", NamedTextColor.LIGHT_PURPLE),
                Material.DRAGON_BREATH,
                new ArenaTheme(
                        FloorStyle.FLAT, WallStyle.OBELISKS,
                        Material.END_STONE, Material.END_STONE_BRICKS, Material.OBSIDIAN,
                        Material.END_STONE_BRICKS, Material.PURPUR_BLOCK,
                        false, Material.END_STONE_BRICKS, Material.PURPUR_BLOCK,
                        Material.END_ROD, null, null, false,
                        World.Environment.NORMAL, Biome.THE_END,
                        Particle.PORTAL, null, false, false),
                "voidwyrm"));

        registry.register(new Realm("the_slime_pit", "realm_the_slime_pit",
                Component.text("The Slime Pit", NamedTextColor.GREEN),
                Material.SLIME_BALL,
                new ArenaTheme(
                        FloorStyle.UNDULATE, WallStyle.MOUNDS,
                        Material.SLIME_BLOCK, Material.MOSS_BLOCK, Material.HONEY_BLOCK,
                        Material.MOSS_BLOCK, Material.MUD_BRICKS,
                        false, Material.MOSS_BLOCK, Material.MUD_BRICKS,
                        Material.OCHRE_FROGLIGHT, null, Material.WATER, false,
                        World.Environment.NORMAL, Biome.MANGROVE_SWAMP,
                        Particle.ITEM_SLIME, null, false, false),
                "amalgamated_bulk"));

        registry.register(new Realm("the_deep_choir", "realm_the_deep_choir",
                Component.text("The Deep Choir", NamedTextColor.LIGHT_PURPLE),
                Material.AMETHYST_CLUSTER,
                new ArenaTheme(
                        FloorStyle.TERRACES_DOWN, WallStyle.SPIKES,
                        Material.SCULK, Material.DEEPSLATE_TILES, Material.SCULK_CATALYST,
                        Material.DEEPSLATE_TILES, Material.SCULK,
                        true, Material.DEEPSLATE_TILES, Material.SCULK,
                        Material.SOUL_LANTERN, Material.DEEPSLATE_TILES, null, false,
                        World.Environment.NORMAL, Biome.DEEP_DARK,
                        Particle.SOUL, null, false, false),
                "hollow_choir"));

        registry.register(new Realm("the_weeping_coast", "realm_the_weeping_coast",
                Component.text("The Weeping Coast", NamedTextColor.BLUE),
                Material.GHAST_TEAR,
                new ArenaTheme(
                        FloorStyle.UNDULATE, WallStyle.ARCHES,
                        Material.ANDESITE, Material.STONE_BRICKS, Material.DRIPSTONE_BLOCK,
                        Material.STONE_BRICKS, Material.ANDESITE,
                        false, Material.STONE_BRICKS, Material.ANDESITE,
                        Material.LANTERN, null, Material.WATER, false,
                        World.Environment.NORMAL, Biome.STONY_SHORE,
                        Particle.SPLASH, null, true, false),
                "weeping_colossus"));

        registry.register(new Realm("the_ancient_city", "realm_the_ancient_city",
                Component.text("The Ancient City", NamedTextColor.DARK_PURPLE),
                Material.ECHO_SHARD,
                new ArenaTheme(
                        FloorStyle.TERRACES_DOWN, WallStyle.OBELISKS,
                        Material.DEEPSLATE_BRICKS, Material.REINFORCED_DEEPSLATE, Material.SCULK_CATALYST,
                        Material.REINFORCED_DEEPSLATE, Material.DEEPSLATE_BRICKS,
                        false, Material.REINFORCED_DEEPSLATE, Material.DEEPSLATE_TILES,
                        Material.SOUL_LANTERN, null, null, false,
                        World.Environment.NORMAL, Biome.THE_END,
                        Particle.SCULK_SOUL, null, false, false),
                "worldender"));
    }
}
