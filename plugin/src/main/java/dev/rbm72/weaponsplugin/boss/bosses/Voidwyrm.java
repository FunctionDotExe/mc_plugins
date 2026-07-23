package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MeteorCallAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.StarfallNovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.VoidBreathAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WyrmDiveAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Starfang;
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
 * The Voidwyrm — spawns as a wyrmling barely bigger than a player and grows every phase, ending
 * Full Coronation as an ancient, screen-filling dragon. Size is the entire arc of this fight: the
 * attacks get scarier, but it's watching the thing physically grow that sells the escalation.
 */
public final class Voidwyrm extends Boss {

    private static final Color VOID_PURPLE = Color.fromRGB(110, 20, 160);
    private static final Color STARLIGHT = Color.fromRGB(220, 180, 255);

    private static final double WYRMLING_SCALE = 0.4;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public Voidwyrm(WeaponsPlugin plugin) {
        super(plugin);

        VoidBreathAttack voidBreath = new VoidBreathAttack(plugin);
        MeteorCallAttack meteorCall = new MeteorCallAttack(plugin);
        WyrmDiveAttack wyrmDive = new WyrmDiveAttack(plugin);
        StarfallNovaAttack starfallNova = new StarfallNovaAttack(plugin);

        this.phases = List.of(
                new BossPhase("The Wyrmling", 1.0,
                        List.of(voidBreath, wyrmDive),
                        false, Voidwyrm::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Newborn Scales", NamedTextColor.LIGHT_PURPLE),
                                Material.END_STONE, VOID_PURPLE, 0, false)),
                new BossPhase("The Growing Wyrm", 0.75,
                        List.of(voidBreath, wyrmDive, meteorCall),
                        false, Voidwyrm::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Hardened Hide", NamedTextColor.LIGHT_PURPLE),
                                Material.OBSIDIAN, VOID_PURPLE, 1, false)),
                new BossPhase("The Elder Wyrm", 0.40,
                        List.of(voidBreath, wyrmDive, meteorCall),
                        false, Voidwyrm::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Ancient Carapace", NamedTextColor.LIGHT_PURPLE),
                                Material.CRYING_OBSIDIAN, VOID_PURPLE, 2, false)),
                new BossPhase("Full Coronation", 0.15,
                        List.of(voidBreath, wyrmDive, meteorCall, starfallNova),
                        true, Voidwyrm::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Heart of the Void", NamedTextColor.RED),
                                Material.NETHER_STAR, Color.fromRGB(180, 20, 20), 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Starfang(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> wyrmhideArmor(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> wyrmhideArmor(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), Voidwyrm::voidMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), Voidwyrm::crownOfTheVoid);
    }

    @Override
    public String id() {
        return "voidwyrm";
    }

    @Override
    public Component displayName() {
        return Component.text("The Voidwyrm", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.ENDER_DRAGON;
    }

    @Override
    public double arenaRadius() {
        return configDouble("arena-radius", 28.0);
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
        return BossAmbiance.of(Particle.PORTAL, "boss.voidwyrm.ambient", Sound.ENTITY_ENDER_DRAGON_AMBIENT,
                false, null);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("★ THE VOIDWYRM ★", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("Something small just woke up hungry", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("★ THE VOIDWYRM FALLS ★", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("It never got the chance to grow any further", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        // Spawns full-grown by default — shrink it down to a wyrmling before the fight is seen.
        AttributeInstance scaleAttr = instance.entity().getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(WYRMLING_SCALE);
        }
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1, 0), VOID_PURPLE, 1.4f, 30, 0.6);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.PORTAL, 24, 0.5);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.0f, 1.4f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.5, 1.0);
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), VOID_PURPLE, 1.8f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.PORTAL, 6.5, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.1f, 1.0f);
        instance.showTitle(
                Component.text("The Growing Wyrm", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("It's already bigger than it was a moment ago", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.4, 1.0);
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), VOID_PURPLE, 2.0f, 44, 0.9);
        Fx.burst(loc.clone().add(0, 1.4, 0), Particle.PORTAL, 40, 0.7);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.8f);
        instance.showTitle(
                Component.text("The Elder Wyrm", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Ancient carapace hardens over its hide", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.3, 1.1);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.PORTAL, 60, 0.9);
        Fx.coloredRing(loc, STARLIGHT, 2.0f, 5.5, 30, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.END_ROD, 8.0, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.6f);
        instance.showTitle(
                Component.text("★ FULL CORONATION ★", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Ancient at last, and furious about the wait", NamedTextColor.GRAY));
    }

    private static ItemStack wyrmhideArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Wyrmhide", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack voidMaterials() {
        Material[] picks = {Material.END_STONE, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.ENDER_PEARL};
        Material picked = picks[ThreadLocalRandom.current().nextInt(picks.length)];
        return new ItemStack(picked, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfTheVoid() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of the Void", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("It never stopped growing. Someone made it stop.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
