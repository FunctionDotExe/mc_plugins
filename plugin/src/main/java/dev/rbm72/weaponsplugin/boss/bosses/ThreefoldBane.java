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
import dev.rbm72.weaponsplugin.boss.bosses.bane.BanePhases;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.HealingAddsAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Soulcrown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wither;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Threefold Bane — three minds on three different clocks, and the roster's <b>tempo</b> boss. Its
 * heads fire on three real redstone loops at the arena edge, each with its own note block, so the whole
 * fight is rhythm: you learn the beat, you act between beats, and you watch the three clocks drift
 * toward alignment, because a <b>Convergence</b> — two or three striking together — is unsurvivable in
 * the open (batch-3 spec §2).
 * <ol>
 *   <li><b>Three Clocks</b> — learn the beats and survive the first Convergence.</li>
 *   <li><b>Swelling</b> — it grows, the gaps shrink, and sabotaging a loop becomes mandatory.</li>
 *   <li><b>Coronation</b> — it re-tunes its own clocks behind you; break two alignments to pass.</li>
 *   <li><b>All Three Speak</b> — the machinery locks at whatever tempo the group engineered.</li>
 * </ol>
 * The clocks are the schedule, not a picture of it: {@code bane.Clocks} reads each head's period off the
 * repeaters physically sitting in its loop every pulse, which is §2.8's non-negotiable constraint.
 */
public final class ThreefoldBane extends Boss {

    private static final Color SOUL_BLUE = Color.fromRGB(30, 200, 210);
    private static final Color DECAY_GREY = Color.fromRGB(50, 50, 55);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public ThreefoldBane(WeaponsPlugin plugin) {
        super(plugin);

        // The three heads' attacks are fired by the clocks, not by the attack selector — a head that
        // also attacked off-beat would make the note blocks a decoration instead of a schedule. What is
        // left in the pool is the one thing that has no clock of its own.
        HealingAddsAttack boneChoir = new HealingAddsAttack(plugin, "threefold_bane", "Bone Choir",
                EntityType.WITHER_SKELETON, DECAY_GREY);

        this.phases = List.of(
                // Three Clocks: the beats, the note blocks, and one Convergence to survive.
                new BossPhase("Three Clocks", 1.0,
                        List.of(boneChoir),
                        false, ThreefoldBane::onEnterPhase1,
                        instance -> BanePhases.threeClocks(instance, 0.76)),
                // Swelling: bigger body, smaller gaps, and a loop that has to be lengthened.
                new BossPhase("Swelling", 0.76,
                        List.of(boneChoir),
                        false, ThreefoldBane::onEnterPhase2,
                        instance -> BanePhases.swelling(instance, 0.52)),
                // Coronation: an active tug-of-war over the machinery, timed against the beat.
                new BossPhase("Coronation", 0.52,
                        List.of(boneChoir),
                        false, ThreefoldBane::onEnterPhase3,
                        instance -> BanePhases.coronation(instance, 0.26)),
                // All Three Speak: locked tempo, no sabotage left, pure execution.
                new BossPhase("All Three Speak", 0.26,
                        List.of(boneChoir),
                        true, ThreefoldBane::onEnterEnrage,
                        BanePhases::allThreeSpeak));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Soulcrown(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> banehideArmor(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> banehideArmor(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), ThreefoldBane::boneMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), ThreefoldBane::crownOfTheBane);
    }

    @Override
    public String id() {
        return "threefold_bane";
    }

    @Override
    public Component displayName() {
        return Component.text("The Threefold Bane", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.WITHER;
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
     * Four times the roster default. Walking out to a loop, placing a repeater and getting back before
     * the next beat is a slow physical act under fire, and {@code BanePhaseMechanic#progressSignal}
     * counts repeaters, desyncs and Convergences so a group doing the engineering never reads as stalled.
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /** Offset from its phase boundaries (0.76 / 0.52 / 0.26) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.88, 0.38}),
                new ConvergenceNukeEvent(plugin, id(), new double[] {0.64, 0.32}));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.SOUL, "boss.threefold_bane.ambient", Sound.ENTITY_WITHER_AMBIENT,
                false, null);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("☠ THE THREEFOLD BANE ☠", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("Three ages of ruin, awake in one body", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("☠ THE THREEFOLD BANE COLLAPSES ☠", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("All three ages fall still together", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        // Real Withers spawn with a vanilla invulnerability window meant for the totem summon
        // ritual — skip it here so the fight starts the instant the title finishes.
        if (instance.entity() instanceof Wither wither) {
            wither.setInvulnerableTicks(0);
        }
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), SOUL_BLUE, 1.8f, 40, 0.7);
        Fx.point(loc.clone().add(0, 1, 0), Particle.SOUL, 20);
        Fx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.7f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.2, 1.05);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SOUL_BLUE, 2.0f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL, 6.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_WITHER_HURT, 1.1f, 0.6f);
        instance.showTitle(
                Component.text("Swelling", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("Less room between beats — go lengthen a loop", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.2, 1.1);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DECAY_GREY, 2.2f, 40, 0.9);
        Fx.point(loc.clone().add(0, 1.4, 0), Particle.SOUL, 24);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 0.9f, 0.6f);
        instance.showTitle(
                Component.text("Coronation", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("It is re-tuning its own clocks — break the alignments", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.15, 1.0);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SOUL, 60, 0.9);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 2.0f, 5.0, 30, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL_FIRE_FLAME, 7.5, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.7f);
        instance.showTitle(
                Component.text("☠ ALL THREE SPEAK ☠", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Locked at the tempo you left it — count it out", NamedTextColor.GRAY));
    }

    private static ItemStack banehideArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Banehide", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack boneMaterials() {
        Material[] picks = {Material.BONE, Material.BONE_BLOCK, Material.SOUL_SOIL, Material.COAL_BLOCK};
        Material picked = picks[ThreadLocalRandom.current().nextInt(picks.length)];
        return new ItemStack(picked, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfTheBane() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of the Bane", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Three ages of ruin, worn as one.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
