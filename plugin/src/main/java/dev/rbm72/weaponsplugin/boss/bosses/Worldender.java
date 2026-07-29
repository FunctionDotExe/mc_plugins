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
import dev.rbm72.weaponsplugin.boss.events.LineOfSightEvent;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AbsoluteZeroAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlinkStrikeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlizzardAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CollapseAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CorruptionSpreadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DashSlashAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FireTrailAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FirestormAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GlacierSpikesAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MeteorRainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SingularityAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SoulDrainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.StormcallAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SummonUndeadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SupernovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ThunderstrikeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TornadoAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.VoidRiftAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.VoidSlamAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WardenSonicBoomAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WorldenderFinaleAttack;
import dev.rbm72.weaponsplugin.boss.bosses.worldender.WorldenderPhases;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Apotheosis;
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
 * The Worldender — the 8-phase capstone, and the final piece of the 2026-07-27 boss rework (design doc:
 * {@code docs/superpowers/specs/2026-07-27-boss-rework-batch-5.md}). Three rules keep an 8-phase boss from
 * being a highlight reel of worse re-runs: every phase removes a rule the group relies on rather than
 * adding a spell, phases are short and single-idea (~12% HP each), and it uses its own props to channel
 * an earlier boss's <em>verb</em> — never that boss's set dressing. One constant threads all eight
 * phases: it is blind, a real Warden, and hunts by vibration for the whole fight — sneak to go quiet,
 * fight to become the target. Each phase drops the tool that survives it, and those tools persist, so by
 * P8 the group is carrying a kit assembled from seven survivals and the finale is unwinnable without it.
 */
public final class Worldender extends Boss {

    private static final Color VOID_PURPLE = Color.fromRGB(140, 40, 200);
    private static final Color FROST = Color.fromRGB(150, 230, 255);
    private static final Color STORM = Color.fromRGB(255, 240, 120);
    private static final Color INFERNO = Color.fromRGB(255, 120, 40);
    private static final Color PLAGUE = Color.fromRGB(120, 200, 60);
    private static final Color VOID_RIFT = Color.fromRGB(90, 30, 150);
    private static final Color UNMAKING = Color.fromRGB(220, 40, 80);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public Worldender(WeaponsPlugin plugin) {
        super(plugin);

        // P1 — Awakening: its own kit, nobody else's.
        WardenSonicBoomAttack sonicBoom = new WardenSonicBoomAttack(plugin);
        VoidSlamAttack voidSlam = new VoidSlamAttack(plugin);
        DashSlashAttack dashSlash = new DashSlashAttack(plugin);

        // P2 — The Freezing: Frost Queen's traversal physics, no Chill meter, no campfires.
        BlizzardAttack blizzard = new BlizzardAttack(plugin);
        GlacierSpikesAttack glacierSpikes = new GlacierSpikesAttack(plugin);
        AbsoluteZeroAttack absoluteZero = new AbsoluteZeroAttack(plugin);

        // P3 — The Charge: Storm Tyrant's conduction, inverted into a social problem.
        ThunderstrikeAttack thunderstrike = new ThunderstrikeAttack(plugin);
        TornadoAttack tornado = new TornadoAttack(plugin);
        StormcallAttack stormcall = new StormcallAttack(plugin);

        // P4 — The Burning: Inferno Warlord's player-authored terrain.
        MeteorRainAttack meteorRain = new MeteorRainAttack(plugin);
        FireTrailAttack fireTrail = new FireTrailAttack(plugin);
        FirestormAttack firestorm = new FirestormAttack(plugin);

        // P5 — The Rot: Plague Warden's attrition, compressed to one idea.
        CorruptionSpreadAttack corruptionSpread = new CorruptionSpreadAttack(plugin);
        SummonUndeadAttack summonUndead = new SummonUndeadAttack(plugin);
        SoulDrainAttack soulDrain = new SoulDrainAttack(plugin);

        // P6 — The Unmooring: Void Sovereign's floor loss, plus the P4 callback.
        VoidRiftAttack voidRift = new VoidRiftAttack(plugin);
        SingularityAttack singularity = new SingularityAttack(plugin);
        BlinkStrikeAttack blinkStrike = new BlinkStrikeAttack(plugin);

        // P7 — Convergence: Threefold Bane's tempo, scheduling P2/P3/P4's verbs. A mixed elemental pool.
        // (thunderstrike, meteorRain, voidRift shared with earlier phases — cooldown carryover is capped
        // by BossInstance#capCarryoverCooldowns, so reuse never opens the phase with a dead kit.)

        // P8 — The Unmaking: the finale, all of it at once.
        WorldenderFinaleAttack finale = new WorldenderFinaleAttack(plugin);
        SupernovaAttack supernova = new SupernovaAttack(plugin);
        CollapseAttack collapse = new CollapseAttack(plugin);

        this.phases = List.of(
                new BossPhase("Awakening", 1.0,
                        List.of(sonicBoom, voidSlam, dashSlash),
                        false, Worldender::onEnterAwakening,
                        WorldenderPhases::awakening),
                new BossPhase("The Freezing", 0.88,
                        List.of(blizzard, glacierSpikes, absoluteZero, sonicBoom),
                        false, Worldender::onEnterFreezing,
                        WorldenderPhases::theFreezing),
                new BossPhase("The Charge", 0.76,
                        List.of(thunderstrike, tornado, stormcall),
                        false, Worldender::onEnterCharge,
                        WorldenderPhases::theCharge),
                new BossPhase("The Burning", 0.64,
                        List.of(meteorRain, fireTrail, firestorm),
                        false, Worldender::onEnterBurning,
                        WorldenderPhases::theBurning),
                new BossPhase("The Rot", 0.52,
                        List.of(corruptionSpread, summonUndead, soulDrain),
                        false, Worldender::onEnterRot,
                        WorldenderPhases::theRot),
                new BossPhase("The Unmooring", 0.40,
                        List.of(voidRift, singularity, blinkStrike),
                        false, Worldender::onEnterUnmooring,
                        WorldenderPhases::theUnmooring),
                new BossPhase("Convergence", 0.28,
                        List.of(thunderstrike, meteorRain, voidRift, blizzard),
                        false, Worldender::onEnterConvergence,
                        WorldenderPhases::convergence),
                new BossPhase("The Unmaking", 0.14,
                        List.of(finale, supernova, collapse, sonicBoom),
                        true, Worldender::onEnterUnmaking,
                        WorldenderPhases::theUnmaking));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Apotheosis(plugin).createItem())
                .weighted(configDouble("loot-materials-chance", 0.6), Worldender::netheriteIngots)
                .weighted(configDouble("loot-materials-chance", 0.6), Worldender::netherStars)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), Worldender::crownOfCreation);
    }

    @Override
    public String id() {
        return "worldender";
    }

    @Override
    public Component displayName() {
        return Component.text("The Worldender", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.WARDEN;
    }

    @Override
    public double maxHealth() {
        return configDouble("max-health", 1000.0);
    }

    @Override
    public double arenaRadius() {
        return configDouble("arena-radius", 45.0);
    }

    /**
     * Twice the framework default. This is the eight-phase final boss and it was standing the same
     * height as every four-phase boss in the roster — a warden-sized silhouette for something meant
     * to read as the thing that ends the world. Enrage still multiplies on top of this.
     */
    @Override
    public double baseScale() {
        return configDouble("scale", 3.0);
    }

    /**
     * The whole rest of the roster. Sixteen distinct bosses beaten — every other one — before this
     * realm will open, which is the point of a capstone whose eight phases are each built out of an
     * earlier boss's verb: a group that has not met those verbs cannot read a single telegraph here.
     * <p>
     * Distinct clears, not kills: farming one boss sixteen times teaches none of the eight vocabularies
     * this fight assumes. Keep this in step with the registered roster size minus one if bosses are
     * added — it is a config key ({@code bosses.worldender.required-clears}) precisely so that does not
     * need a recompile.
     */
    @Override
    public int requiredClears() {
        return configInt("required-clears", 16);
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
     * Three interruptions layered over eight phases, offset from the phase boundaries (0.88 / 0.76 / 0.64
     * / 0.52 / 0.40 / 0.28 / 0.14) so none of them lands on a transition that is already busy with a title
     * and an arena warp.
     */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.94, 0.58}),
                new LineOfSightEvent(plugin, id(), new double[] {0.82, 0.46},
                        "TOTAL ECLIPSE", "Break line of sight — nothing else will save you"),
                new ConvergenceNukeEvent(plugin, id(), new double[] {0.34, 0.16},
                        "THE UNMAKING GATHERS", "Get away from it — all of you"));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.SCULK_SOUL, "boss.worldender.ambient", Sound.ENTITY_WARDEN_AMBIENT,
                true, Biome.THE_END);
    }

    @Override
    public String themeMusicKey() {
        return "boss.worldender.theme";
    }

    /** The shipped track's actual length (3:52.03) — see {@code resourcepack/assets/weaponsplugin/sounds/boss/worldender/theme.ogg}. */
    @Override
    public double themeMusicLoopSeconds() {
        return configDouble("music-loop-seconds", 232.03);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("☄ THE WORLDENDER AWAKENS ☄", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("The end of all things has come", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("✦ THE WORLDENDER IS UNMADE ✦", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The world endures", NamedTextColor.GRAY);
    }

    /**
     * Per-phase arena cinematic: a themed burst, expanding rings, an orbiting ring around the boss, and
     * layered sound — the visible "arena warp" as the Worldender takes something else away. Kept to
     * particle/sound theatrics here; the real terrain warp (ice, lava, rifts) is the phase mechanic's own
     * job, done through {@code Grief} so it restores with everything else when the fight ends.
     */
    private static void warpArena(BossInstance instance, Color color, Particle particle, Sound sound) {
        Location loc = instance.entity().getLocation();
        Fx.expandingRings(instance.plugin(), loc, particle, 8.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), color, 1.8f, 50, 0.9);
        Fx.burst(loc.clone().add(0, 1, 0), particle, 40, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, color, 1.4f, 6.0 - ring * 1.5, 24, ring * 0.6);
        }
        Fx.sound(loc, sound, 1.2f, 0.7f);
        Fx.sound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
    }

    private static void onEnterAwakening(BossInstance instance) {
        warpArena(instance, VOID_PURPLE, Particle.SCULK_SOUL, Sound.ENTITY_WARDEN_EMERGE);
        instance.showTitle(
                Component.text("The Worldender Awakens", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("It draws breath, and it is listening", NamedTextColor.GRAY));
    }

    private static void onEnterFreezing(BossInstance instance) {
        warpArena(instance, FROST, Particle.SNOWFLAKE, Sound.ENTITY_PLAYER_HURT_FREEZE);
        instance.showTitle(
                Component.text("The Freezing", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("The floor is no longer yours to trust", NamedTextColor.GRAY));
    }

    private static void onEnterCharge(BossInstance instance) {
        warpArena(instance, STORM, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);
        instance.showTitle(
                Component.text("The Charge", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("Standing together is how it finds you", NamedTextColor.GRAY));
    }

    private static void onEnterBurning(BossInstance instance) {
        warpArena(instance, INFERNO, Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT);
        instance.showTitle(
                Component.text("The Burning", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Ground is something you make now", NamedTextColor.GRAY));
    }

    private static void onEnterRot(BossInstance instance) {
        warpArena(instance, PLAGUE, Particle.SPORE_BLOSSOM_AIR, Sound.BLOCK_SCULK_SPREAD);
        instance.showTitle(
                Component.text("The Rot", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Healing is not free anymore", NamedTextColor.GRAY));
    }

    private static void onEnterUnmooring(BossInstance instance) {
        warpArena(instance, VOID_RIFT, Particle.SQUID_INK, Sound.BLOCK_PORTAL_TRIGGER);
        instance.showTitle(
                Component.text("The Unmooring", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("The arena itself is going", NamedTextColor.GRAY));
    }

    private static void onEnterConvergence(BossInstance instance) {
        warpArena(instance, UNMAKING, Particle.END_ROD, Sound.BLOCK_NOTE_BLOCK_BELL);
        instance.showTitle(
                Component.text("Convergence", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Everything you learned, all at once, on a beat", NamedTextColor.GRAY));
    }

    private static void onEnterUnmaking(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        warpArena(instance, UNMAKING, Particle.SOUL_FIRE_FLAME, Sound.ENTITY_WITHER_SPAWN);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.8, 0), Material.NETHERITE_SWORD, 1.5f, 120, 14.0);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.2f, 0.6f);

        for (int ring = 0; ring < 5; ring++) {
            Fx.coloredRing(loc, UNMAKING, 2.0f, 3.0 + ring * 2.2, 32 + ring * 4, ring * 0.4);
        }
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), UNMAKING, 2.4f, 90, 1.4);
        Fx.burst(loc.clone().add(0, 1.5, 0), Particle.SOUL_FIRE_FLAME, 60, 1.2);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1.5, 0), 12, 1.6, 1.6, 1.6, 0);
        Fx.flash(loc.clone().add(0, 1.5, 0), 4);
        Fx.shatterDebris(instance.plugin(), loc.clone().add(0, 1.2, 0), Material.NETHERITE_SCRAP, 10, 1.3, 40).forEach(instance::trackEntity);
        Fx.shatterDebris(instance.plugin(), loc.clone().add(0, 1.2, 0), Material.CRYING_OBSIDIAN, 8, 1.1, 40).forEach(instance::trackEntity);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= 40 || !instance.entity().isValid()) {
                    cancel();
                    return;
                }
                angle += Math.PI / 6;
                double height = ticks * 0.3;
                Location base = instance.entity().getLocation();
                Fx.helixFrame(base, Particle.SOUL_FIRE_FLAME, 2.2, 16, angle, height);
                Fx.helixFrame(base, Particle.FLAME, 1.6, 12, -angle * 1.4, height * 0.85);
                ticks++;
            }
        }.runTaskTimer(instance.plugin(), 0L, 1L);

        Fx.sound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.4f);
        instance.showTitle(
                Component.text("☠ THE UNMAKING ☠", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every rule, live at once — read the pattern", NamedTextColor.GRAY));
    }

    private static ItemStack netheriteIngots() {
        return new ItemStack(Material.NETHERITE_INGOT, ThreadLocalRandom.current().nextInt(1, 4));
    }

    private static ItemStack netherStars() {
        return new ItemStack(Material.NETHER_STAR, ThreadLocalRandom.current().nextInt(1, 3));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfCreation() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of Creation", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Worn by the being that would have unmade everything.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        } else {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Crown of Creation", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }
}
