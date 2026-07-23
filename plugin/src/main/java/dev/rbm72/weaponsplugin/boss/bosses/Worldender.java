package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
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
                new BossPhase("Awakening", 1.0,
                        List.of(sonicBoom, voidSlam, dashSlash),
                        false, Worldender::onEnterAwakening,
                        VulnerabilitySpec.scaled(Component.text("Warden Nodes", NamedTextColor.DARK_PURPLE),
                                Material.SCULK_CATALYST, VOID_PURPLE, 0, false)),
                new BossPhase("Frostbound", 0.87,
                        List.of(blizzard, glacierSpikes, absoluteZero, sanctuaryTrial),
                        false, Worldender::onEnterFrostbound,
                        VulnerabilitySpec.scaled(Component.text("Frost Nodes", NamedTextColor.AQUA),
                                Material.BLUE_ICE, FROST, 1, false)),
                new BossPhase("Stormforged", 0.75,
                        List.of(thunderstrike, tornado, stormcall, gazeTrial),
                        false, Worldender::onEnterStormforged,
                        VulnerabilitySpec.scaled(Component.text("Storm Nodes", NamedTextColor.YELLOW),
                                Material.LIGHTNING_ROD, STORM, 2, false)),
                new BossPhase("Infernal", 0.62,
                        List.of(meteorRain, fireTrail, firestorm),
                        false, Worldender::onEnterInfernal,
                        VulnerabilitySpec.scaled(Component.text("Inferno Nodes", NamedTextColor.GOLD),
                                Material.MAGMA_BLOCK, INFERNO, 3, false)),
                new BossPhase("Plaguebound", 0.50,
                        List.of(corruptionSpread, summonUndead, soulDrain),
                        false, Worldender::onEnterPlaguebound,
                        VulnerabilitySpec.scaled(Component.text("Plague Nodes", NamedTextColor.DARK_GREEN),
                                Material.SPORE_BLOSSOM, PLAGUE, 3, false)),
                new BossPhase("Voidtouched", 0.37,
                        List.of(voidRift, singularity, blinkStrike, markedTarget),
                        false, Worldender::onEnterVoidtouched,
                        VulnerabilitySpec.scaled(Component.text("Void Nodes", NamedTextColor.LIGHT_PURPLE),
                                Material.ECHO_SHARD, VOID_PURPLE, 3, false)),
                new BossPhase("Cataclysm", 0.25,
                        List.of(meteorRain, thunderstrike, absoluteZero, voidRift, sanctuaryTrial, gazeTrial),
                        false, Worldender::onEnterCataclysm,
                        VulnerabilitySpec.scaled(Component.text("Cataclysm Nodes", NamedTextColor.RED),
                                Material.NETHERITE_SCRAP, UNMAKING, 3, false)),
                new BossPhase("The Unmaking", 0.12,
                        List.of(finale, supernova, collapse, stormcall, firestorm, sanctuaryTrial, gazeTrial, markedTarget),
                        true, Worldender::onEnterUnmaking,
                        VulnerabilitySpec.scaled(Component.text("Core Fragments", NamedTextColor.DARK_RED),
                                Material.NETHER_STAR, UNMAKING, 3, true)));

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
        return configDouble("arena-radius", 25.0);
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
