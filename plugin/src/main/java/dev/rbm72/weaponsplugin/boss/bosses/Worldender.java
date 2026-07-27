package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.boss.events.ConvergenceNukeEvent;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.events.LineOfSightEvent;
import dev.rbm72.weaponsplugin.boss.mechanics.ChainTagMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.ContagionLedgerMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.DecoyMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.DeepSilenceMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.ExpandingRingMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.GroundingRodsMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.ShrinkingFloesMechanic;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AbsoluteZeroAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlinkStrikeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlizzardAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CollapseAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CorruptionSpreadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DashSlashAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FireTrailAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FirestormAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GazeTrialAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GlacierSpikesAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MarkedTargetTrialAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MeteorRainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SanctuaryTrialAttack;
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
 * The Worldender — the 8-phase capstone boss. It channels the power of every
 * fallen boss in turn: after an Awakening phase of its own Warden-born attacks
 * it cycles through frost, storm, fire, plague, and void kits (reusing those
 * bosses' attack classes) before The Unmaking — an enrage finale that unleashes
 * every signature grief move at once. Drops the Mythic Apotheosis.
 */
public final class Worldender extends Boss {

    private static final Color VOID_PURPLE = Color.fromRGB(140, 40, 200);
    private static final Color FROST = Color.fromRGB(150, 230, 255);
    private static final Color STORM = Color.fromRGB(255, 245, 120);
    private static final Color INFERNO = Color.fromRGB(255, 120, 40);
    private static final Color PLAGUE = Color.fromRGB(120, 200, 60);
    private static final Color UNMAKING = Color.fromRGB(220, 40, 80);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public Worldender(WeaponsPlugin plugin) {
        super(plugin);

        // Phase 1 — Awakening (new Worldender-only attacks + a dash).
        WardenSonicBoomAttack sonicBoom = new WardenSonicBoomAttack(plugin);
        VoidSlamAttack voidSlam = new VoidSlamAttack(plugin);
        DashSlashAttack dashSlash = new DashSlashAttack(plugin);

        // Shared instances reused across multiple phases.
        AbsoluteZeroAttack absoluteZero = new AbsoluteZeroAttack(plugin);
        ThunderstrikeAttack thunderstrike = new ThunderstrikeAttack(plugin);
        StormcallAttack stormcall = new StormcallAttack(plugin);
        MeteorRainAttack meteorRain = new MeteorRainAttack(plugin);
        FirestormAttack firestorm = new FirestormAttack(plugin);
        VoidRiftAttack voidRift = new VoidRiftAttack(plugin);

        // Phase-specific instances.
        BlizzardAttack blizzard = new BlizzardAttack(plugin);
        GlacierSpikesAttack glacierSpikes = new GlacierSpikesAttack(plugin);
        TornadoAttack tornado = new TornadoAttack(plugin);
        FireTrailAttack fireTrail = new FireTrailAttack(plugin);
        CorruptionSpreadAttack corruptionSpread = new CorruptionSpreadAttack(plugin);
        SummonUndeadAttack summonUndead = new SummonUndeadAttack(plugin);
        SoulDrainAttack soulDrain = new SoulDrainAttack(plugin);
        SingularityAttack singularity = new SingularityAttack(plugin);
        BlinkStrikeAttack blinkStrike = new BlinkStrikeAttack(plugin);

        // Phase 8 — The Unmaking (enrage).
        WorldenderFinaleAttack finale = new WorldenderFinaleAttack(plugin);
        SupernovaAttack supernova = new SupernovaAttack(plugin);
        CollapseAttack collapse = new CollapseAttack(plugin);

        // Forced-action trials — one per elemental kit it channels, all three stacked into the finale.
        SanctuaryTrialAttack sanctuaryTrial = new SanctuaryTrialAttack(plugin, "worldender");
        GazeTrialAttack gazeTrial = new GazeTrialAttack(plugin, "worldender");
        MarkedTargetTrialAttack markedTarget = new MarkedTargetTrialAttack(plugin, "worldender");

        this.phases = List.of(
                // Resonance Cascade: one of its own three mechanics. A shriek that jumps to whoever is
                // nearest and grows with every jump, so the fight opens by forbidding the group's
                // default resting shape — a clump at its feet.
                new BossPhase("Awakening", 1.0,
                        List.of(sonicBoom, voidSlam, dashSlash),
                        false, Worldender::onEnterAwakening,
                        this::resonanceCascade),
                // From here it is an amalgamation, replaying the roster's signatures in order. Frost
                // Queen's breaking floes.
                new BossPhase("Frostbound", 0.87,
                        List.of(blizzard, glacierSpikes, absoluteZero, sanctuaryTrial),
                        false, Worldender::onEnterFrostbound,
                        this::frostboundFloes),
                // Storm Tyrant's grounding rods.
                new BossPhase("Stormforged", 0.75,
                        List.of(thunderstrike, tornado, stormcall, gazeTrial),
                        false, Worldender::onEnterStormforged,
                        this::stormforgedRods),
                // Inferno Warlord's expanding moat.
                new BossPhase("Infernal", 0.62,
                        List.of(meteorRain, fireTrail, firestorm),
                        false, Worldender::onEnterInfernal,
                        this::infernalMoat),
                // Plague Warden's ledger.
                new BossPhase("Plaguebound", 0.50,
                        List.of(corruptionSpread, summonUndead, soulDrain),
                        false, Worldender::onEnterPlaguebound,
                        this::plagueboundLedger),
                // Void Sovereign's reflections.
                new BossPhase("Voidtouched", 0.37,
                        List.of(voidRift, singularity, blinkStrike, markedTarget),
                        false, Worldender::onEnterVoidtouched,
                        this::voidtouchedReflections),
                // Ungated, and the only place in eight phases the group gets one: everything it has
                // learned, with nothing new to solve. The palate cleanser before the end.
                new BossPhase("Cataclysm", 0.25,
                        List.of(meteorRain, thunderstrike, absoluteZero, voidRift, sanctuaryTrial, gazeTrial),
                        false, Worldender::onEnterCataclysm),
                // The Deep Silence: its last mechanic, and the roster's payoff. The lights go out, the
                // fight stops making noise, and a single expanding pulse on the floor is the only
                // warning anyone gets.
                new BossPhase("The Unmaking", 0.12,
                        List.of(finale, supernova, collapse, stormcall, firestorm, sanctuaryTrial, gazeTrial, markedTarget),
                        true, Worldender::onEnterUnmaking,
                        this::theDeepSilence));

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

    @Override
    public List<BossPhase> phases() {
        return phases;
    }

    @Override
    public LootTable lootTable() {
        return lootTable;
    }

    /**
     * Resonance Cascade: a shriek that chains between players standing too close and hits harder with
     * every hop. It only dies when it cannot reach anyone, so the opening phase is a standing order to
     * spread out and stay spread out.
     */
    private PhaseMechanic resonanceCascade(BossInstance instance) {
        return new ChainTagMechanic(instance, "Resonance Cascade", UNMAKING,
                configDouble("resonance-hop-range", 9.0),
                configInt("resonance-hop-interval-ticks", 26),
                configDouble("resonance-base-damage", 7.0),
                configDouble("resonance-growth-per-hop", 6.0),
                configInt("resonance-max-hops", 7),
                configInt("resonance-respawn-ticks", 100));
    }

    /** Frost Queen's Thin Ice, replayed. */
    private PhaseMechanic frostboundFloes(BossInstance instance) {
        return new ShrinkingFloesMechanic(instance, "Thin Ice", FROST,
                configInt("frostbound-floe-count", 3),
                configDouble("frostbound-start-radius", 7.0),
                configDouble("frostbound-end-radius", 2.8),
                configInt("frostbound-cycle-ticks", 200),
                configDouble("frostbound-plunge-damage", 18.0),
                configInt("frostbound-freeze-ticks", 60),
                configDouble("frostbound-placement-fraction", 0.55));
    }

    /** Storm Tyrant's Grounding Rods, replayed. */
    private PhaseMechanic stormforgedRods(BossInstance instance) {
        return new GroundingRodsMechanic(instance, "Grounding Rods", STORM, Material.LIGHTNING_ROD,
                configInt("stormforged-rods-max", 4),
                configDouble("stormforged-rods-radius", 3.5),
                configInt("stormforged-charge-ticks", 160),
                configDouble("stormforged-earthed-damage", 5.0),
                configDouble("stormforged-unrouted-damage", 11.0),
                configDouble("stormforged-placement-fraction", 0.6));
    }

    /** Inferno Warlord's Moat Eruption, replayed. */
    private PhaseMechanic infernalMoat(BossInstance instance) {
        return new ExpandingRingMechanic(instance, "Moat Eruption", INFERNO,
                configInt("infernal-wave-interval-ticks", 140),
                configDouble("infernal-wave-speed", 0.45),
                configDouble("infernal-band-thickness", 2.6),
                configInt("infernal-starting-gaps", 4),
                configInt("infernal-minimum-gaps", 2),
                configDouble("infernal-gap-half-width-degrees", 18.0),
                configDouble("infernal-gap-drift-degrees-per-second", 16.0),
                configDouble("infernal-wave-damage", 16.0),
                configInt("infernal-burn-ticks", 40));
    }

    /** Plague Warden's Contagion Ledger, replayed. */
    private PhaseMechanic plagueboundLedger(BossInstance instance) {
        return new ContagionLedgerMechanic(instance, PLAGUE,
                configDouble("plaguebound-stacks-per-damage", 1.0),
                configDouble("plaguebound-decay-per-second", 9.0),
                configDouble("plaguebound-ledger-cap", 320.0),
                configDouble("plaguebound-burst-base-damage", 5.0),
                configDouble("plaguebound-burst-damage-per-stack", 0.16),
                configInt("plaguebound-sickness-ticks", 100),
                configDouble("plaguebound-heal-per-stack", 0.05));
    }

    /** Void Sovereign's Mirrorflesh, replayed. */
    private PhaseMechanic voidtouchedReflections(BossInstance instance) {
        return new DecoyMechanic(instance, "Mirrorflesh", VOID_PURPLE, EntityType.WARDEN,
                configInt("voidtouched-decoys", 3),
                configInt("voidtouched-shuffle-ticks", 110),
                configDouble("voidtouched-blink-distance", 20.0),
                configDouble("voidtouched-blink-damage", 9.0),
                configDouble("voidtouched-spawn-radius", 6.0),
                configInt("voidtouched-refresh-ticks", 400));
    }

    /**
     * The Deep Silence: its last mechanic and the roster's payoff. Darkness plus a fight that stops
     * telegraphing with sound — every other boss doubles its cues, and this one takes half of them
     * away. Deliberately not blinding, so the pulse on the floor is always readable.
     */
    private PhaseMechanic theDeepSilence(BossInstance instance) {
        return new DeepSilenceMechanic(instance, "The Deep Silence", UNMAKING,
                configInt("silence-pulse-interval-ticks", 130),
                configInt("silence-telegraph-ticks", 45),
                configDouble("silence-safe-radius", 11.0),
                configDouble("silence-pulse-damage", 26.0));
    }

    /**
     * Three interruptions layered over eight phases, every milestone offset from the phase boundaries
     * (0.87 / 0.75 / 0.62 / 0.50 / 0.37 / 0.25 / 0.12) so none of them lands on a transition that is
     * already busy with a title and a cinematic.
     */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.93, 0.55}),
                new LineOfSightEvent(plugin, id(), new double[] {0.68, 0.30},
                        "TOTAL ECLIPSE", "Break line of sight — nothing else will save you"),
                new ConvergenceNukeEvent(plugin, id(), new double[] {0.44, 0.18},
                        "THE UNMAKING GATHERS", "Get away from it — all of you"));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.PORTAL, "boss.worldender.ambient", Sound.BLOCK_PORTAL_AMBIENT,
                true, Biome.THE_END);
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
     * Per-phase arena cinematic: a themed burst, expanding rings, an orbiting
     * ring around the boss, and layered sound — the visible "arena warp" as the
     * Worldender channels a new element. Kept to particle/sound theatrics (no
     * block or biome edits) so it never leaves the arena permanently changed.
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
        warpArena(instance, VOID_PURPLE, Particle.PORTAL, Sound.ENTITY_WARDEN_EMERGE);
        instance.showTitle(
                Component.text("The Worldender Awakens", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("It draws breath for the first time in an age", NamedTextColor.GRAY));
    }

    private static void onEnterFrostbound(BossInstance instance) {
        warpArena(instance, FROST, Particle.SNOWFLAKE, Sound.ENTITY_PLAYER_HURT_FREEZE);
        instance.showTitle(
                Component.text("Frostbound", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("It channels the Frost Queen's winter", NamedTextColor.GRAY));
    }

    private static void onEnterStormforged(BossInstance instance) {
        warpArena(instance, STORM, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);
        instance.showTitle(
                Component.text("Stormforged", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("The Storm Tyrant's fury crackles around it", NamedTextColor.GRAY));
    }

    private static void onEnterInfernal(BossInstance instance) {
        warpArena(instance, INFERNO, Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT);
        instance.showTitle(
                Component.text("Infernal", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("The Inferno Warlord's fire wreathes it", NamedTextColor.GRAY));
    }

    private static void onEnterPlaguebound(BossInstance instance) {
        warpArena(instance, PLAGUE, Particle.SPORE_BLOSSOM_AIR, Sound.BLOCK_SCULK_SPREAD);
        instance.showTitle(
                Component.text("Plaguebound", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The Plague Warden's rot spreads from it", NamedTextColor.GRAY));
    }

    private static void onEnterVoidtouched(BossInstance instance) {
        warpArena(instance, VOID_PURPLE, Particle.REVERSE_PORTAL, Sound.BLOCK_PORTAL_TRIGGER);
        instance.showTitle(
                Component.text("Voidtouched", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("The Void Sovereign's rifts open around it", NamedTextColor.GRAY));
    }

    private static void onEnterCataclysm(BossInstance instance) {
        warpArena(instance, UNMAKING, Particle.EXPLOSION, Sound.ENTITY_GENERIC_EXPLODE);
        instance.showTitle(
                Component.text("Cataclysm", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every element tears at the arena at once", NamedTextColor.GRAY));
    }

    private static void onEnterUnmaking(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        warpArena(instance, UNMAKING, Particle.SOUL_FIRE_FLAME, Sound.ENTITY_WITHER_SPAWN);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.8, 0), Material.NETHERITE_SWORD, 1.5f, 120, 14.0);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.2f, 0.6f);

        // Overkill capstone cinematic: layered rings/columns at every radius plus a rising double helix.
        for (int ring = 0; ring < 5; ring++) {
            Fx.coloredRing(loc, UNMAKING, 2.0f, 3.0 + ring * 2.2, 32 + ring * 4, ring * 0.4);
        }
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), UNMAKING, 2.4f, 90, 1.4);
        Fx.burst(loc.clone().add(0, 1.5, 0), Particle.SOUL_FIRE_FLAME, 60, 1.2);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1.5, 0), 12, 1.6, 1.6, 1.6, 0);
        Fx.flash(loc.clone().add(0, 1.5, 0), 4);
        // Real debris, not just particles: shattered netherite and crying obsidian fly outward as the world itself starts coming apart.
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
                Component.text("It will drag the world down with it", NamedTextColor.GRAY));
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
