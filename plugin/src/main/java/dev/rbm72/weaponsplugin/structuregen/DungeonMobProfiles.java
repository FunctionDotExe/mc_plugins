package dev.rbm72.weaponsplugin.structuregen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/** Every boss's dungeon-grunt profile, one entry per boss id — see {@link DungeonMobProfile}. */
final class DungeonMobProfiles {

    private static final Map<String, DungeonMobProfile> PROFILES = new HashMap<>();

    static {
        register("fallen_king", "Fallen Guard", NamedTextColor.DARK_RED,
                EntityType.WITHER_SKELETON, null, 30.0, MobAbility.CHARGE);
        register("frost_queen", "Frost Stalker", NamedTextColor.AQUA,
                EntityType.STRAY, null, 22.0, MobAbility.NONE);
        register("storm_tyrant", "Storm Reaver", NamedTextColor.YELLOW,
                EntityType.VINDICATOR, Material.IRON_AXE, 26.0, MobAbility.CHARGE);
        register("inferno_warlord", "Cinder Wretch", NamedTextColor.GOLD,
                EntityType.BLAZE, null, 20.0, MobAbility.NONE);
        register("plague_warden", "Rotmire Husk", NamedTextColor.GREEN,
                EntityType.HUSK, null, 24.0, MobAbility.POTION_LOB);
        register("void_sovereign", "Void Wisp", NamedTextColor.DARK_PURPLE,
                EntityType.VEX, null, 16.0, MobAbility.NONE);
        register("solar_colossus", "Sunspire Brute", NamedTextColor.GOLD,
                EntityType.PIGLIN_BRUTE, Material.GOLDEN_AXE, 28.0, MobAbility.NONE);
        register("tide_leviathan", "Trench Drowned", NamedTextColor.DARK_AQUA,
                EntityType.DROWNED, Material.TRIDENT, 24.0, MobAbility.NONE);
        register("dragon_elder", "Barrow Phantom", NamedTextColor.DARK_RED,
                EntityType.PHANTOM, null, 20.0, MobAbility.NONE);
        register("necro_overlord", "Hollow Ghoul", NamedTextColor.DARK_GREEN,
                EntityType.ZOMBIE, Material.IRON_SWORD, 22.0, MobAbility.NONE);
        register("grafted_horror", "Grafted Crawler", NamedTextColor.DARK_GREEN,
                EntityType.CAVE_SPIDER, null, 18.0, MobAbility.NONE);
        register("threefold_bane", "Crypt Zoglin", NamedTextColor.AQUA,
                EntityType.ZOGLIN, null, 26.0, MobAbility.CHARGE);
        register("voidwyrm", "Starless Endermite", NamedTextColor.LIGHT_PURPLE,
                EntityType.ENDERMITE, null, 14.0, MobAbility.NONE);
        register("amalgamated_bulk", "Pit Slime", NamedTextColor.GREEN,
                EntityType.SLIME, null, 24.0, MobAbility.NONE);
        register("hollow_choir", "Deep Chorister", NamedTextColor.LIGHT_PURPLE,
                EntityType.SKELETON, null, 20.0, MobAbility.NONE);
        register("weeping_colossus", "Coastal Guardian", NamedTextColor.BLUE,
                EntityType.GUARDIAN, null, 22.0, MobAbility.NONE);
        register("worldender", "Ancient Evoker", NamedTextColor.DARK_PURPLE,
                EntityType.EVOKER, null, 32.0, MobAbility.NONE);
    }

    private DungeonMobProfiles() {
    }

    private static void register(String bossId, String label, NamedTextColor color, EntityType base,
                                  Material handItem, double health, MobAbility ability) {
        PROFILES.put(bossId, new DungeonMobProfile(Component.text(label, color), base, handItem, health, ability));
    }

    static DungeonMobProfile get(String bossId) {
        return PROFILES.getOrDefault(bossId, DEFAULT);
    }

    private static final DungeonMobProfile DEFAULT =
            new DungeonMobProfile(Component.text("Dungeon Grunt", NamedTextColor.GRAY),
                    EntityType.ZOMBIE, null, 20.0, MobAbility.NONE);
}
