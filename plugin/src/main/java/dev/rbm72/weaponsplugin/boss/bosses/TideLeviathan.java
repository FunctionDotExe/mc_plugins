package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.events.ConvergenceNukeEvent;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BubbleTrapAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.HealingAddsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MaelstromAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TridentThrowAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TsunamiAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WaterJetAttack;
import dev.rbm72.weaponsplugin.boss.bosses.leviathan.MaelstromPhase;
import dev.rbm72.weaponsplugin.boss.bosses.leviathan.LowTidePhase;
import dev.rbm72.weaponsplugin.boss.bosses.leviathan.RisingTidePhase;
import dev.rbm72.weaponsplugin.boss.bosses.leviathan.TheDeepPhase;
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
 * The Tide Leviathan — an arena that becomes an ocean, then drains.
 * <p>
 * The roster's breath-and-3D-space boss: the only fight where the arena becomes fully volumetric. Water
 * rises in stages ({@code leviathan.WaterLevel}) until the whole arena is submerged, and from that point
 * the master resource is air — real vanilla drowning, held off only by real conduits ({@code
 * leviathan.Conduits}) the group must place, defend, and replace whenever the Leviathan smashes one. Real
 * soul-sand and magma-block bubble columns ({@code leviathan.BubbleColumns}) turn the vertical axis into
 * both a hazard and a highway, real guardians patrol with their own unmistakable beam telegraph, and a
 * directional pull field ({@code leviathan.Whirlpool}) drags the fight toward the centre once in P2 and
 * permanently in P3. Then P4 drains the arena in one dramatic event and inverts the whole skillset: he is
 * beached, slower, heavier, and finally reachable by the melee he could always outswim.
 * <p>
 * <b>Four phases, three of which end on something other than health</b> (batch-2 §3.3):
 * <ol>
 *   <li><b>Rising Tide</b> (100-75%) — exits on one conduit placed and currently held.</li>
 *   <li><b>The Deep</b> (75-48%) — exits on the P2 one-shot Whirlpool having run its course.</li>
 *   <li><b>Maelstrom</b> (48-20%) — exits on three Whirlpool "breaks" (soul-sand-column groundings).</li>
 *   <li><b>Low Tide</b> (&lt;20%) — the roster's mandatory ungated phase (design rule #2): no mechanic,
 *       just a straight, dangerous DPS race against a beached, slowed, wounded animal.</li>
 * </ol>
 * <b>What gates him is water, never a shield.</b> No phase here ever sets a damage multiplier or forces
 * invulnerability — he is hittable every second of the fight. What the group has to manage is air,
 * position against the pull, and a conduit network under active attack.
 */
public final class TideLeviathan extends Boss {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);
    private static final Color PALE = Color.fromRGB(160, 240, 255);
    private static final Color DEEP_TEAL = Color.fromRGB(10, 90, 130);

    /** Health-fraction seams, shared between the phase list and the milestones the events land between. */
    private static final double THE_DEEP_ENTRY = 0.75;
    private static final double MAELSTROM_ENTRY = 0.48;
    private static final double LOW_TIDE_ENTRY = 0.20;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public TideLeviathan(WeaponsPlugin plugin) {
        super(plugin);

        WaterJetAttack waterJet = new WaterJetAttack(plugin);
        TridentThrowAttack tridentThrow = new TridentThrowAttack(plugin);
        BubbleTrapAttack bubbleTrap = new BubbleTrapAttack(plugin);
        TsunamiAttack tsunami = new TsunamiAttack(plugin);
        MaelstromAttack maelstrom = new MaelstromAttack(plugin);
        HealingAddsAttack abyssalPriests = new HealingAddsAttack(plugin, "tide_leviathan", "Abyssal Priest",
                EntityType.DROWNED, TEAL);

        this.phases = List.of(
                // Rising Tide: still partly a ground fight, with the shoreline moving under everyone's
                // feet. Learn where the air is and get the first conduit down.
                new BossPhase("Rising Tide", 1.0,
                        List.of(waterJet, tridentThrow, bubbleTrap),
                        false, TideLeviathan::onEnterRisingTide,
                        instance -> new RisingTidePhase(instance, THE_DEEP_ENTRY)),
                // The Deep: full submersion, guardians, bubble columns, and a boss who actively hunts the
                // conduit network instead of just the nearest player.
                new BossPhase("The Deep", THE_DEEP_ENTRY,
                        List.of(waterJet, tridentThrow, bubbleTrap, tsunami, abyssalPriests),
                        false, TideLeviathan::onEnterTheDeep,
                        instance -> new TheDeepPhase(instance, MAELSTROM_ENTRY)),
                // Maelstrom: the pull never turns off again. Position is something you maintain, not
                // choose — drift and the current chews you up.
                new BossPhase("Maelstrom", MAELSTROM_ENTRY,
                        List.of(waterJet, tridentThrow, bubbleTrap, tsunami, abyssalPriests, maelstrom),
                        false, TideLeviathan::onEnterMaelstrom,
                        instance -> new MaelstromPhase(instance, LOW_TIDE_ENTRY)),
                // Low Tide: ungated on purpose (design rule #2) — the arena drains in one dramatic event
                // and it becomes a straight, grounded brawl with a wounded animal.
                new BossPhase("Low Tide", LOW_TIDE_ENTRY,
                        List.of(tridentThrow, waterJet),
                        true, TideLeviathan::onEnterLowTide));

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

    /**
     * §3.6 anti-cheese, expressed as events at milestones offset from the phase boundaries (0.75/0.48/
     * 0.20) so none of them land on a transition that already has its own cinematic (Pitfall 9).
     * <p>
     * {@code HitCountShieldEvent} rewards attack speed over raw damage per swing at two points in the
     * fight; {@code ConvergenceNukeEvent} is the mid-fight "everyone out, now" punctuation beat, themed
     * here as a riptide slam through the flooded arena.
     */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.86, 0.30}),
                new ConvergenceNukeEvent(plugin, id(), new double[] {0.60},
                        "RIPTIDE SLAM", "Get to the edge — the whole basin is about to move"));
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
        return Component.text("A drowned sovereign surges from the deep — the ocean rises with it", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("🌊 THE TIDE LEVIATHAN IS DROWNED 🌊", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The waters fall still once more", NamedTextColor.GRAY);
    }

    private static void onEnterRisingTide(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.TRIDENT));
        }
        // A geyser of water erupts as the leviathan rises to claim the arena — the first hint the floor
        // is about to become the sea.
        Fx.expandingRings(instance.plugin(), loc, Particle.SPLASH, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), TEAL, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.BUBBLE_COLUMN_UP, 30, 0.6);
        Fx.sound(loc, Sound.AMBIENT_UNDERWATER_LOOP, 1.0f, 0.7f);
        Fx.sound(loc, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.8f, 0.5f);
    }

    private static void onEnterTheDeep(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SPLASH, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), PALE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.BUBBLE, 7.0, 3, 2L);
        Fx.sound(loc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.8f, 0.8f);
        instance.showTitle(
                Component.text("The Deep", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("The last air is gone — hold the conduits", NamedTextColor.GRAY));
    }

    private static void onEnterMaelstrom(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.BUBBLE, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DEEP_TEAL, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, TEAL, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
        Fx.sound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.7f, 0.6f);
        instance.showTitle(
                Component.text("Maelstrom", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("The pull never stops now — ride the current or fight it", NamedTextColor.GRAY));
    }

    private static void onEnterLowTide(BossInstance instance) {
        LowTidePhase.beach(instance);
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
