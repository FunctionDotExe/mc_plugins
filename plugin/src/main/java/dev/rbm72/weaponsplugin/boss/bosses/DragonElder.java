package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CataclysmAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DiveBombAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FireballBarrageAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FirestormBreathAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GrabAndDropAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.HoverRepositionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MeteorWingsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SkyboundAssaultAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TailSweepAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WingGustAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.WyrmscaleBow;
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
 * The Dragon Elder — an ancient aerial wyrm. It hovers ~8 blocks above its
 * target and periodically dive-bombs to the ground (its vulnerable window)
 * before relifting. 400 HP across four phases (100-75 / 75-40 / 40-15 /
 * enrage &lt;15), with Dive Bomb as the terrain-wrecking signature and a
 * strafing Cataclysm in enrage. Flight is handled purely through the
 * short-cooldown Hover attack, so no long-lived task is needed.
 */
public final class DragonElder extends Boss {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);
    private static final Color ELDER_BLACK = Color.fromRGB(30, 5, 5);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public DragonElder(WeaponsPlugin plugin) {
        super(plugin);

        HoverRepositionAttack hover = new HoverRepositionAttack(plugin);
        FireballBarrageAttack fireballBarrage = new FireballBarrageAttack(plugin);
        WingGustAttack wingGust = new WingGustAttack(plugin);
        DiveBombAttack diveBomb = new DiveBombAttack(plugin);
        TailSweepAttack tailSweep = new TailSweepAttack(plugin);
        FirestormBreathAttack firestormBreath = new FirestormBreathAttack(plugin);
        GrabAndDropAttack grabAndDrop = new GrabAndDropAttack(plugin);
        MeteorWingsAttack meteorWings = new MeteorWingsAttack(plugin);
        CataclysmAttack cataclysm = new CataclysmAttack(plugin);
        SkyboundAssaultAttack skyboundAssault = new SkyboundAssaultAttack(plugin);

        this.phases = List.of(
                new BossPhase("Phase 1", 1.0,
                        List.of(hover, fireballBarrage, wingGust, diveBomb, tailSweep),
                        false, DragonElder::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Ossified Scales", NamedTextColor.DARK_RED),
                                Material.BONE_BLOCK, DRAGON_RED, 0, false)),
                new BossPhase("Wings of Ruin", 0.75,
                        List.of(hover, firestormBreath, grabAndDrop, fireballBarrage, diveBomb),
                        false, DragonElder::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Ossified Scales", NamedTextColor.DARK_RED),
                                Material.BONE_BLOCK, DRAGON_RED, 1, false)),
                new BossPhase("Elder Fury", 0.40,
                        List.of(hover, meteorWings, firestormBreath, grabAndDrop, tailSweep, skyboundAssault),
                        false, DragonElder::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Ancient Wardbones", NamedTextColor.RED),
                                Material.DRAGON_HEAD, ELDER_BLACK, 2, false)),
                new BossPhase("Cataclysm", 0.15,
                        List.of(hover, fireballBarrage, wingGust, diveBomb, firestormBreath, tailSweep, cataclysm,
                                skyboundAssault),
                        true, DragonElder::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Heart of the Wyrm", NamedTextColor.DARK_RED),
                                Material.NETHER_STAR, DRAGON_RED, 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new WyrmscaleBow(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> wyrmscaleArmor(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> wyrmscaleArmor(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), DragonElder::dragonBreath)
                .weighted(configDouble("loot-materials-chance", 0.6), DragonElder::blazeRods)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), DragonElder::elderDragonSkull);
    }

    @Override
    public String id() {
        return "dragon_elder";
    }

    @Override
    public Component displayName() {
        return Component.text("The Dragon Elder", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.PHANTOM;
    }

    @Override
    public double maxHealth() {
        return configDouble("max-health", 400.0);
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
        return BossAmbiance.of(Particle.FLAME, "boss.dragon_elder.ambient", Sound.ENTITY_ENDER_DRAGON_AMBIENT,
                true, Biome.WINDSWEPT_HILLS);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("🐉 THE DRAGON ELDER 🐉", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("An ancient wyrm blots out the sky", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("🐉 THE DRAGON ELDER FALLS 🐉", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The last of the elder wyrms comes crashing down", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The elder unfurls its wings: a roaring corona of flame and a downdraft ring.
        Fx.expandingRings(instance.plugin(), loc, Particle.FLAME, 6.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), DRAGON_RED, 1.8f, 45, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.LAVA, 20, 0.6);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Wings of Ruin: the sky catches fire around the wyrm.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.FLAME, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DRAGON_RED, 1.7f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.LAVA, 7.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.5f);
        instance.showTitle(
                Component.text("Wings of Ruin", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Its wings scatter embers across the sky", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Elder Fury: an inward-collapsing firestorm gathers around the wyrm.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.LAVA, 35, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), ELDER_BLACK, 1.8f, 30, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, DRAGON_RED, 1.6f, 6.0 - ring * 1.5, 22, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_DEATH, 0.6f, 0.4f);
        instance.showTitle(
                Component.text("Elder Fury", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Ancient rage boils over", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Cataclysm: the wyrm blazes from within, a spinning ember icon overhead, a fire helix climbing.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.FLAME, 45, 0.7);
        Fx.coloredRing(loc, DRAGON_RED, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.MAGMA_BLOCK, 1.4f, 100, 12.0);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !instance.entity().isValid()) {
                    cancel();
                    return;
                }
                Fx.helixFrame(instance.entity().getLocation(), Particle.FLAME, 1.3, 3, ticks * 0.5, ticks * 0.15);
                ticks++;
            }
        }.runTaskTimer(instance.plugin(), 0L, 1L);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.0f);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f);
        instance.showTitle(
                Component.text("🔥 CATACLYSM 🔥", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("The Dragon Elder will burn the world down with it", NamedTextColor.GRAY));
    }

    private static ItemStack wyrmscaleArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Wyrmscale", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack dragonBreath() {
        return new ItemStack(Material.DRAGON_BREATH, ThreadLocalRandom.current().nextInt(1, 4));
    }

    private static ItemStack blazeRods() {
        return new ItemStack(Material.BLAZE_ROD, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack elderDragonSkull() {
        ItemStack item = new ItemStack(Material.DRAGON_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Elder Dragon Skull", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("The skull of an elder wyrm, still warm with dying fire.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
