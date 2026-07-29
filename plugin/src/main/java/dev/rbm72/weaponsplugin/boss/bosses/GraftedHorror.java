package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.events.BeaconEvent;
import dev.rbm72.weaponsplugin.boss.events.SpreadCheckEvent;
import dev.rbm72.weaponsplugin.boss.bosses.graft.GraftPhases;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrenziedGraftAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.RendingClawAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Spinelash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Grafted Horror — a corpse someone wired back up and never turned off. Its attacks do not come
 * from the body at all: they come from bolted-on <b>graft modules</b> (dispenser, piston, observer,
 * repair), each powered by a real redstone line running across the arena floor to a real power source
 * (batch-3 spec §1). You do not out-damage it. You disable it, limb by limb, by cutting circuits — and
 * it re-grafts every line you cut, so the fight is a maintenance race, not a damage race.
 * <ol>
 *   <li><b>Two Grafts</b> — dispenser and piston. The first cut teaches everything.</li>
 *   <li><b>Observer Graft</b> — a real observer arc you must approach from outside, or bait.</li>
 *   <li><b>Self-Repair</b> — a module that rebuilds severed lines, so cuts have to be simultaneous.</li>
 *   <li><b>Seams Open</b> — the modules tear loose and keep firing from the floor as turrets, one per
 *       module the group never cut.</li>
 * </ol>
 * It is never invulnerable in any band; see {@code graft.GraftPhaseMechanic}'s header for why this
 * boss needs no damage wall at all.
 */
public final class GraftedHorror extends Boss {

    private static final Color RUST = Color.fromRGB(150, 70, 35);
    private static final Color SICKLY_GREEN = Color.fromRGB(90, 130, 60);
    private static final Color SPARK_RED = Color.fromRGB(255, 60, 40);
    private static final Color DECAY_GREY = Color.fromRGB(60, 60, 60);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public GraftedHorror(WeaponsPlugin plugin) {
        super(plugin);

        // Its own body only ever does one thing — a heavy, committed claw — because somebody always has
        // to be holding its attention while everyone else does wire work (§1.2). Ranged pressure is the
        // modules' job, and duplicating it in the attack pool would make the circuits decorative.
        RendingClawAttack rendingClaw = new RendingClawAttack(plugin);
        FrenziedGraftAttack frenziedGraft = new FrenziedGraftAttack(plugin);

        this.phases = List.of(
                // Two Grafts: two live modules, two visible lines. Exits on both being cut once.
                new BossPhase("Two Grafts", 1.0,
                        List.of(rendingClaw),
                        false, GraftedHorror::onEnterPhase1,
                        instance -> GraftPhases.twoGrafts(instance, 0.74)),
                // Observer Graft: line-of-sight discipline and wire work at the same time.
                new BossPhase("Observer Graft", 0.74,
                        List.of(rendingClaw),
                        false, GraftedHorror::onEnterPhase2,
                        instance -> GraftPhases.observerGraft(instance, 0.50)),
                // Self-Repair: one cut is now worthless. Three at once, inside a window the group opens.
                new BossPhase("Self-Repair", 0.50,
                        List.of(rendingClaw),
                        false, GraftedHorror::onEnterPhase3,
                        instance -> GraftPhases.selfRepair(instance, 0.24)),
                // Seams Open: the turret field is whatever the group let run all fight.
                new BossPhase("Seams Open", 0.24,
                        List.of(rendingClaw, frenziedGraft),
                        true, GraftedHorror::onEnterEnrage,
                        GraftPhases::seamsOpen));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Spinelash(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> graftedHide(Material.DIAMOND_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> graftedHide(Material.DIAMOND_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), GraftedHorror::donorRemains)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), GraftedHorror::trophyOfTheMenagerie);
    }

    @Override
    public String id() {
        return "grafted_horror";
    }

    @Override
    public Component displayName() {
        return Component.text("The Grafted Horror", NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.ZOMBIE;
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
     * Four times the roster default, same reasoning as the Storm Tyrant: tracing a line, cutting it, and
     * timing three cuts against a repair window are physical acts under fire rather than a burst window,
     * and {@code GraftPhaseMechanic#progressSignal} resets the clock on every cut the group lands.
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /** Offset from its phase boundaries (0.74 / 0.50 / 0.24) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new BeaconEvent(plugin, id(), new double[] {0.86, 0.36}),
                new SpreadCheckEvent(plugin, id(), new double[] {0.62, 0.30}));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.ITEM_COBWEB, "boss.grafted_horror.ambient", Sound.ENTITY_ZOMBIE_AMBIENT,
                false, null);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("⚚ THE GRAFTED HORROR ⚚", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("Stitched from every beast that fell before it", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("⚚ THE GRAFTED HORROR UNRAVELS ⚚", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("Every graft falls still at once", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.REDSTONE));
            equipment.setItemInOffHand(new ItemStack(Material.REPEATER));
        }
        // Something switches on inside it: sparks jump the seams and the first two lines light up.
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SICKLY_GREEN, 1.6f, 34, 0.6);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 24, 0.5);
        Fx.sound(loc, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.6f);
        Fx.sound(loc, Sound.BLOCK_LEVER_CLICK, 1.2f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // A third module bolts itself on, and this one is watching.
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SPARK_RED, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 30, 0.6);
        Fx.expandingRings(instance.plugin(), loc, Particle.SMOKE, 6.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_DISPENSER_FAIL, 1.1f, 0.7f);
        instance.showTitle(
                Component.text("Observer Graft", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Don't move in front of it — come at it from behind", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            ItemStack skull = new ItemStack(Material.WITHER_SKELETON_SKULL);
            if (skull.getItemMeta() instanceof SkullMeta meta) {
                meta.displayName(Component.text("Grafted Skull", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
                skull.setItemMeta(meta);
            }
            equipment.setHelmet(skull);
        }
        // The busiest module yet drops into its socket and starts pulling severed lines back together.
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), RUST, 2.0f, 34, 0.8);
        Fx.point(loc.clone().add(0, 1.4, 0), Particle.SMOKE, 20);
        Fx.sound(loc, Sound.BLOCK_ANVIL_USE, 1.0f, 0.6f);
        instance.showTitle(
                Component.text("Self-Repair", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true),
                Component.text("Cut the repair line first — then three at once", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Every seam splits at once and the grafts drop off it, still firing.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.LARGE_SMOKE, 50, 0.8);
        Fx.coloredRing(loc, DECAY_GREY, 1.8f, 4.5, 26, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.ELECTRIC_SPARK, 7.0, 4, 2L);
        Fx.sound(loc, Sound.BLOCK_PISTON_EXTEND, 1.4f, 0.4f);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        instance.showTitle(
                Component.text("⚚ SEAMS OPEN ⚚", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every module you left running is a turret now", NamedTextColor.GRAY));
    }

    private static ItemStack graftedHide(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Grafted Hide", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack donorRemains() {
        Material[] remains = {Material.ROTTEN_FLESH, Material.SPIDER_EYE, Material.BLAZE_POWDER, Material.BONE};
        Material picked = remains[ThreadLocalRandom.current().nextInt(remains.length)];
        return new ItemStack(picked, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack trophyOfTheMenagerie() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Trophy of the Menagerie", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Every beast it ever wore, mounted at last.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
