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
import dev.rbm72.weaponsplugin.boss.mechanics.BlinkSnareMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.DecoyMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.ForceFieldMechanic;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ArcaneMissilesAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BanishAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlinkStrikeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CollapseAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GravityFlipAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TwinRiftsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SingularityAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.VoidRiftAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.VoidZoneAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Nullblade;
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
 * A void-touched enderman sovereign: four phases (100-75 / 75-40 / 40-15 /
 * enrage &lt;15) of blink-strikes, arcane volleys, and reality-tearing rifts.
 */
public final class VoidSovereign extends Boss {

    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);
    private static final Color VOID_BLACK = Color.fromRGB(25, 0, 40);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public VoidSovereign(WeaponsPlugin plugin) {
        super(plugin);

        // Reused across phases -> single shared instance each (own cooldown state).
        BlinkStrikeAttack blinkStrike = new BlinkStrikeAttack(plugin);
        ArcaneMissilesAttack arcaneMissiles = new ArcaneMissilesAttack(plugin);
        VoidRiftAttack voidRift = new VoidRiftAttack(plugin);
        BanishAttack banish = new BanishAttack(plugin);
        SingularityAttack singularity = new SingularityAttack(plugin);

        GravityFlipAttack gravityFlip = new GravityFlipAttack(plugin);
        VoidZoneAttack voidZone = new VoidZoneAttack(plugin);
        CollapseAttack collapse = new CollapseAttack(plugin);
        TwinRiftsAttack twinRifts = new TwinRiftsAttack(plugin);

        this.phases = List.of(
                // Gravity Well: the floor pulls toward the middle for the whole band. Nothing is
                // blocked — you simply cannot stand still, and every approach costs ground.
                new BossPhase("Gravity Well", 1.0,
                        List.of(blinkStrike, arcaneMissiles, voidRift, banish),
                        false, VoidSovereign::onEnterPhase1,
                        this::gravityWell),
                // Ungated: reality is coming apart and he is not hiding behind any of it. Pure race.
                new BossPhase("Reality Fractures", 0.75,
                        List.of(gravityFlip, singularity, blinkStrike, arcaneMissiles),
                        false, VoidSovereign::onEnterPhase2),
                // Mirrorflesh: his signature. He stops being one target, shuffles with his reflections,
                // and swinging at the wrong one throws you across the room. Costs tempo, never damage.
                new BossPhase("The Void Beckons", 0.40,
                        List.of(voidZone, singularity, voidRift, banish, twinRifts),
                        false, VoidSovereign::onEnterPhase3,
                        this::mirrorflesh),
                // Blink Snare: he folds one of you out of the fight entirely. Nobody can go and help —
                // the group is simply a member down until they solve their own way back.
                new BossPhase("Unmaking", 0.15,
                        List.of(blinkStrike, arcaneMissiles, voidRift, gravityFlip, singularity, collapse, twinRifts),
                        true, VoidSovereign::onEnterEnrage,
                        this::blinkSnare));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Nullblade(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> voidwovenArmorPiece(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> voidwovenArmorPiece(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), () -> stack(Material.ENDER_PEARL))
                .weighted(configDouble("loot-materials-chance", 0.6), () -> stack(Material.CHORUS_FRUIT))
                .weighted(configDouble("loot-cosmetic-chance", 0.008), VoidSovereign::crownOfTheVoid);
    }

    @Override
    public String id() {
        return "void_sovereign";
    }

    @Override
    public Component displayName() {
        return Component.text("The Void Sovereign", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.ENDERMAN;
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
     * Gravity Well: a standing inward current for the whole band, with a lethal core at the middle.
     * It re-tunes everything else happening at the same time — his blinks and rifts are far harder to
     * sidestep when the floor is moving under you — without adding a single extra rule.
     */
    private PhaseMechanic gravityWell(BossInstance instance) {
        return new ForceFieldMechanic(instance, "Gravity Well", VOID_PURPLE,
                ForceFieldMechanic.Direction.INWARD, ForceFieldMechanic.Focus.ARENA_CENTRE,
                configDouble("gravity-well-pull-per-tick", 0.055),
                configDouble("gravity-well-core-radius", 5.0),
                configDouble("gravity-well-core-damage-per-second", 8.0),
                null);
    }

    /**
     * Mirrorflesh: his signature. Reflections stand among him and they trade places on a clock; a
     * swing at a copy blinks the attacker across the arena. He stays fully hittable throughout, so
     * being fooled costs tempo and position rather than access.
     */
    private PhaseMechanic mirrorflesh(BossInstance instance) {
        return new DecoyMechanic(instance, "Mirrorflesh", VOID_PURPLE, EntityType.ENDERMAN,
                configInt("mirrorflesh-decoys", 3),
                configInt("mirrorflesh-shuffle-ticks", 120),
                configDouble("mirrorflesh-blink-distance", 18.0),
                configDouble("mirrorflesh-blink-damage", 6.0),
                configDouble("mirrorflesh-spawn-radius", 5.0),
                configInt("mirrorflesh-refresh-ticks", 400));
    }

    /**
     * Blink Snare: one player is folded into a bubble nobody else can reach, with a circling rift as
     * their only way back. The group is briefly and genuinely a member down, which is a different
     * pressure from any rescue mechanic — and it is solo-safe by construction.
     */
    private PhaseMechanic blinkSnare(BossInstance instance) {
        return new BlinkSnareMechanic(instance, "Blink Snare", VOID_BLACK,
                configDouble("blink-snare-bubble-radius", 6.0),
                configInt("blink-snare-ticks", 160),
                configInt("blink-snare-recast-ticks", 160),
                configDouble("blink-snare-rift-degrees-per-second", 55.0),
                configDouble("blink-snare-fail-damage", 20.0),
                configInt("blink-snare-fail-disorient-ticks", 70));
    }

    /** Offset from his phase boundaries (0.75 / 0.40 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.86, 0.30}),
                new ConvergenceNukeEvent(plugin, id(), new double[] {0.58, 0.24},
                        "COLLAPSE", "Get away from him before it folds"));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.PORTAL, "boss.void_sovereign.ambient", Sound.BLOCK_PORTAL_AMBIENT,
                true, Biome.THE_END);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("✦ THE VOID SOVEREIGN ✦", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A ruler of the space between worlds awakens", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("✦ THE VOID SOVEREIGN UNMADE ✦", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("Reality knits itself back together", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The enderman keeps its vanilla behaviour, including teleporting away when struck. That is
        // accepted for this boss: the framework's per-tick leash pulls it back to the arena each tick,
        // so a stray blink never lets it escape the fight.
        instance.entity().setAI(true);
        // Rising void column: a swirling portal helix and a purple burst as the Sovereign manifests.
        Fx.expandingRings(instance.plugin(), loc, Particle.PORTAL, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), VOID_PURPLE, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 25, 0.6);
        Fx.sound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.6f);
        Fx.sound(loc, Sound.BLOCK_PORTAL_TRIGGER, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Reality fractures: shattered-portal shrapnel and a collapsing void ring.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.PORTAL, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), VOID_PURPLE, 1.6f, 30, 0.6);
        Fx.expandingRings(instance.plugin(), loc, Particle.REVERSE_PORTAL, 6.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 0.8f, 0.7f);
        instance.showTitle(
                Component.text("Reality Fractures", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("The world splinters around the Sovereign", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The void beckons: inward-swirling rings collapsing onto the boss.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SQUID_INK, 35, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), VOID_BLACK, 2.0f, 30, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.ring(loc, Particle.REVERSE_PORTAL, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.4f);
        Fx.sound(loc, Sound.ENTITY_ENDERMAN_STARE, 0.8f, 0.5f);
        instance.showTitle(
                Component.text("The Void Beckons", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("The emptiness reaches out to claim the arena", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Final unmaking: a void helix erupts around the Sovereign with a spinning shard overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.PORTAL, 90, 1.1);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), VOID_PURPLE, 2.4f, 60, 1.1);
        for (int ring = 0; ring < 4; ring++) {
            Fx.ring(loc, Particle.REVERSE_PORTAL, 4.5 - ring * 0.9, 28 - ring * 4, ring * 0.5);
        }
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1.2, 0), 9, 0.96, 1.44, 0.96, 0);
        Fx.flash(loc.clone().add(0, 1.2, 0), 3);
        // Real debris, not just particles: shattered echo shards and ender pearls fly outward as reality tears open.
        Fx.shatterDebris(instance.plugin(), loc.clone().add(0, 1.2, 0), Material.ECHO_SHARD, 8, 1.2, 40).forEach(instance::trackEntity);
        Fx.shatterDebris(instance.plugin(), loc.clone().add(0, 1.2, 0), Material.ENDER_PEARL, 8, 1.0, 40).forEach(instance::trackEntity);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.ECHO_SHARD, 1.8f, 140, 20.0);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40 || !instance.entity().isValid()) {
                    cancel();
                    return;
                }
                Location base = instance.entity().getLocation();
                Fx.helixFrame(base, Particle.PORTAL, 2.0, 8, ticks * 0.5, ticks * 0.15);
                Fx.helixFrame(base, Particle.REVERSE_PORTAL, 1.4, 6, -ticks * 0.7, ticks * 0.12);
                ticks++;
            }
        }.runTaskTimer(instance.plugin(), 0L, 1L);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 0.7f);
        Fx.sound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1.4f, 0.4f);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.9f);
        instance.showTitle(
                Component.text("✦ UNMAKING ✦", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("The Void Sovereign will drag everything into the dark", NamedTextColor.GRAY));
    }

    private static ItemStack voidwovenArmorPiece(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Voidwoven Regalia", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack stack(Material material) {
        return new ItemStack(material, ThreadLocalRandom.current().nextInt(1, 4));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfTheVoid() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of the Void", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("A trophy from the ruler of the space between worlds.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
