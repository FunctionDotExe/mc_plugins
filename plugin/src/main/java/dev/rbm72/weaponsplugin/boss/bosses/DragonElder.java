package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.events.SpreadCheckEvent;
import dev.rbm72.weaponsplugin.boss.mechanics.EscalatingDoomMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.ForceFieldMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.MechanicField;
import dev.rbm72.weaponsplugin.boss.mechanics.ShadowBombardmentMechanic;
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
                // Shadow Bombardment: its signature. The shadow crosses the floor first and the strike
                // follows the same line, so the information always arrives before the danger does — for
                // anyone actually watching the ground.
                new BossPhase("Shadow Bombardment", 1.0,
                        List.of(hover, fireballBarrage, wingGust, diveBomb, tailSweep),
                        false, DragonElder::onEnterPhase1,
                        this::shadowBombardment),
                // Wing Cyclone: a permanent outward gale. Bracing holds you in place and costs you all
                // your mobility; not bracing costs you the platform.
                new BossPhase("Wings of Ruin", 0.75,
                        List.of(hover, firestormBreath, grabAndDrop, fireballBarrage, diveBomb),
                        false, DragonElder::onEnterPhase2,
                        this::wingCyclone),
                // Ungated: no mechanic at all, just an elder dragon with its whole kit. Pure race.
                new BossPhase("Elder Fury", 0.40,
                        List.of(hover, meteorWings, firestormBreath, grabAndDrop, tailSweep, skyboundAssault),
                        false, DragonElder::onEnterPhase3),
                // Its last stand runs on a clock only sustained damage holds back. Mid-roster, so
                // failure compounds: it grows, speeds up and hardens permanently (design rule 4).
                new BossPhase("Cataclysm", 0.15,
                        List.of(hover, fireballBarrage, wingGust, diveBomb, firestormBreath, tailSweep, cataclysm,
                                skyboundAssault),
                        true, DragonElder::onEnterEnrage,
                        this::cataclysmClock));

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

    /**
     * Shadow Bombardment: a shadow sweeps the floor and the impacts follow along the same line a beat
     * later. Nothing marks the impact point — only the shadow — so the phase rewards extrapolating
     * where a moving line is going rather than reacting to a circle appearing underfoot.
     */
    private PhaseMechanic shadowBombardment(BossInstance instance) {
        return new ShadowBombardmentMechanic(instance, "Shadow Bombardment", ELDER_BLACK, DRAGON_RED,
                configInt("bombardment-run-interval-ticks", 150),
                configInt("bombardment-impact-delay-ticks", 34),
                configDouble("bombardment-shadow-speed", 0.55),
                configDouble("bombardment-impact-radius", 3.4),
                configDouble("bombardment-damage", 15.0),
                configInt("bombardment-impacts-per-run", 5));
    }

    /**
     * Wing Cyclone: a standing outward gale that will walk you off the edge if you let it. Crouching
     * braces you against it completely — a verb nothing else in the roster uses — but a braced player
     * is a stationary one, which is exactly what its bombardment and breath want you to be.
     */
    private PhaseMechanic wingCyclone(BossInstance instance) {
        MechanicField bracing = (inst, player) -> player.isSneaking();
        return new ForceFieldMechanic(instance, "Wing Cyclone", DRAGON_RED,
                ForceFieldMechanic.Direction.OUTWARD, ForceFieldMechanic.Focus.BOSS,
                configDouble("cyclone-push-per-tick", 0.06),
                configDouble("cyclone-danger-radius", 22.0),
                configDouble("cyclone-edge-damage-per-second", 9.0),
                bracing);
    }

    /**
     * Cataclysm: the finish is a clock the group can only hold down with sustained damage. Every time
     * it tops out the dragon is permanently larger, faster and harder to hurt, so falling behind
     * compounds rather than simply costing health.
     */
    private PhaseMechanic cataclysmClock(BossInstance instance) {
        return new EscalatingDoomMechanic(instance, "Cataclysm", DRAGON_RED,
                configDouble("cataclysm-fill-per-second", 4.5),
                configDouble("cataclysm-damage-per-point", 5.5),
                configDouble("cataclysm-cap", 100.0),
                configDouble("cataclysm-scale-step", 1.09),
                configDouble("cataclysm-speed-step", 1.07),
                configDouble("cataclysm-harden-step", 0.07),
                configDouble("cataclysm-overflow-damage", 14.0));
    }

    /** Offset from its phase boundaries (0.75 / 0.40 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.88, 0.34}),
                new SpreadCheckEvent(plugin, id(), new double[] {0.62, 0.28}));
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
