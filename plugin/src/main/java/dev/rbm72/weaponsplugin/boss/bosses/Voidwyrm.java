package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.events.CollapsingOrbEvent;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.StarfallNovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.VoidBreathAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WyrmDiveAttack;
import dev.rbm72.weaponsplugin.boss.bosses.wyrm.WyrmPhases;
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
 * The Voidwyrm — the roster's category-shifting boss (batch-4 §1.1). Not one fight that escalates but
 * four different fights wearing one name: a chase it flees ({@code WyrmlingPhase}), an ambush from
 * underground ({@code BurrowerPhase}), a multi-segment serpent whose coiling body is the terrain
 * ({@code SerpentPhase}), and a swallow-and-fight-from-inside finale ({@code AncientPhase}). Every
 * collaborating system lives in {@code bosses.wyrm}; this class only wires phases, attacks, loot and
 * flavor together. False Ground — real, faintly-marked illusory floor patches — runs underneath all
 * four phases (composed inside {@code WyrmPhaseMechanic}, not authored here), the one constant thread
 * across a fight that otherwise changes what kind of encounter it is three separate times.
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
        WyrmDiveAttack wyrmDive = new WyrmDiveAttack(plugin);
        StarfallNovaAttack starfallNova = new StarfallNovaAttack(plugin);

        this.phases = List.of(
                // The Wyrmling: tiny, fast, actively evasive. Won't stand and fight — the group has to
                // trap it with arena geometry and arena-supplied blocks before it's meaningfully hittable.
                new BossPhase("The Wyrmling", 1.0,
                        List.of(voidBreath, wyrmDive),
                        false, Voidwyrm::onEnterPhase1,
                        instance -> WyrmPhases.wyrmling(instance, 0.78)),
                // The Burrower: it goes underground, real tunnels open in the floor, and it erupts under
                // whoever it was tracking. A group that reads the tremor meets it at the surfacing point.
                new BossPhase("The Burrower", 0.78,
                        List.of(voidBreath, wyrmDive),
                        false, Voidwyrm::onEnterPhase2,
                        instance -> WyrmPhases.burrower(instance, 0.54)),
                // The Serpent: it surfaces fully as a long segmented body that coils through the arena —
                // the body itself is now terrain, and only a migrating subset of segments is vulnerable.
                new BossPhase("The Serpent", 0.54,
                        List.of(voidBreath),
                        false, Voidwyrm::onEnterPhase3,
                        instance -> WyrmPhases.serpent(instance, 0.28)),
                // The Ancient: full size, ringed by its own coiled body, periodically swallowing a
                // player into a real interior space they have to break their way out of from within.
                new BossPhase("The Ancient", 0.28,
                        List.of(voidBreath, starfallNova),
                        true, Voidwyrm::onEnterEnrage,
                        WyrmPhases::ancient));

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
        return configDouble("arena-radius", 50.0);
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
     * Four times the roster default, same reasoning as the Fallen King: cornering it, tracking a burrow,
     * cracking a migrating segment and surviving the swallow are physical acts under pressure rather
     * than a burst window, and every phase's {@code progressSignal} resets the clock on partial headway.
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /** Offset from its phase boundaries (0.78 / 0.54 / 0.28) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.88, 0.40}),
                new CollapsingOrbEvent(plugin, id(), new double[] {0.66, 0.20}));
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
        instance.showTitle(
                Component.text("The Wyrmling", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("It won't fight you — corner it", NamedTextColor.GRAY));
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.5, 1.0);
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), VOID_PURPLE, 1.8f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.PORTAL, 6.5, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.1f, 1.0f);
        instance.showTitle(
                Component.text("The Burrower", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("It's already bigger — and about to go under", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.4, 1.0);
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), VOID_PURPLE, 2.0f, 44, 0.9);
        Fx.burst(loc.clone().add(0, 1.4, 0), Particle.PORTAL, 40, 0.7);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.8f);
        instance.showTitle(
                Component.text("The Serpent", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Only the cracked segments can be hurt", NamedTextColor.GRAY));
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
