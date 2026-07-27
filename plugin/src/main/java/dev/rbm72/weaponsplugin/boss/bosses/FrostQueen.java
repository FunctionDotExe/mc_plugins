package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.boss.events.BeaconEvent;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.mechanics.DriftingDiscField;
import dev.rbm72.weaponsplugin.boss.mechanics.MechanicField;
import dev.rbm72.weaponsplugin.boss.mechanics.ShrinkingFloesMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.StackMeterMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.TrappedAlliesMechanic;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AbsoluteZeroAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AfflictionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AvalancheAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlizzardAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ChillingTouchAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrostArmorAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrostNovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrozenPrisonAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GlacierSpikesAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.IceLanceAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.GlacialScepter;
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
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Frost Queen — winter's cruel sovereign. Four phases
 * (100-75 / 75-40 / 40-15 / enrage &lt;15) built on a shared frost kit, with
 * Glacier Spikes as the signature terrain-wrecking move and an arena-wide
 * Absolute Zero flash-freeze in enrage.
 * <p>
 * Her phases deliberately do not share a {@link dev.rbm72.weaponsplugin.boss.PhaseMechanic}, and none
 * of them ever stops her being hittable. Every phase asks the group for something structurally
 * different <em>while</em> they fight, so the encounter never settles into one loop:
 * <ol>
 *   <li><b>Thin Ice</b> — the floor. Shrinking floes, and a plunge for anyone standing on what gives
 *       way. It teaches the arena before anything else is asked.</li>
 *   <li><b>Winter Deepens</b> — <em>ungated</em>. She is hittable for the entire band and simply
 *       tries very hard to kill you through a blizzard. No mechanic at all: pure race.</li>
 *   <li><b>Frozen Court</b> — her signature. She takes two of you at a time and they suffocate on a
 *       clock, while she keeps fighting: damage or your friends, pick.</li>
 *   <li><b>Absolute Winter</b> — a positional meter. The cold wins everywhere except a pair of
 *       drifting warm pockets, so her last stand is fought permanently on the move.</li>
 * </ol>
 * As the roster's second-easiest fight, every failure here punishes the players directly and none of
 * it feeds her — hardening and self-healing consequences start later (design rule 4).
 */
public final class FrostQueen extends Boss {

    private static final Color FROST_BLUE = Color.fromRGB(150, 220, 255);
    private static final Color PALE_ICE = Color.fromRGB(210, 240, 255);
    private static final Color ARMOR_BLUE = Color.fromRGB(140, 205, 245);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public FrostQueen(WeaponsPlugin plugin) {
        super(plugin);

        FrostNovaAttack frostNova = new FrostNovaAttack(plugin);
        IceLanceAttack iceLance = new IceLanceAttack(plugin);
        GlacierSpikesAttack glacierSpikes = new GlacierSpikesAttack(plugin);
        ChillingTouchAttack chillingTouch = new ChillingTouchAttack(plugin);
        FrostArmorAttack frostArmor = new FrostArmorAttack(plugin);
        BlizzardAttack blizzard = new BlizzardAttack(plugin);
        AvalancheAttack avalanche = new AvalancheAttack(plugin);
        FrozenPrisonAttack frozenPrison = new FrozenPrisonAttack(plugin);
        AbsoluteZeroAttack absoluteZero = new AbsoluteZeroAttack(plugin);
        AfflictionAttack deepFrostbite = new AfflictionAttack(plugin, "frost_queen", "Deep Frostbite", FROST_BLUE,
                Sound.BLOCK_GLASS_BREAK);

        this.phases = List.of(
                // Thin Ice: the floor itself is the mechanic. She is hittable from the first second —
                // the ask is that you keep relocating onto ground that is still there while you fight.
                new BossPhase("Thin Ice", 1.0,
                        List.of(frostNova, iceLance, glacierSpikes, chillingTouch, frostArmor),
                        false, FrostQueen::onEnterPhase1,
                        this::thinIce),
                // Ungated on purpose. The one phase with no mechanic to solve — she is hittable start to
                // finish and the entire band is a damage race through a blizzard. Her heaviest attack
                // pool carries it, and the contrast is what makes the other phases read as deliberate.
                new BossPhase("Winter Deepens", 0.75,
                        List.of(blizzard, avalanche, iceLance, glacierSpikes, frostNova, deepFrostbite),
                        false, FrostQueen::onEnterPhase2),
                // Frozen Court: she takes two of you at once and starts killing them slowly. She never
                // stops being hittable, so every second spent cutting people out is a second of damage
                // the group is choosing not to deal — which is the whole decision.
                new BossPhase("Frozen Court", 0.40,
                        List.of(frozenPrison, frostNova, iceLance, blizzard, deepFrostbite),
                        false, FrostQueen::onEnterPhase3,
                        this::frozenCourt),
                // Absolute Zero: the cold is now everywhere and always winning. The only ground that
                // does not kill you drifts, so her last stand is fought on the move, permanently.
                new BossPhase("Absolute Winter", 0.15,
                        List.of(frostNova, iceLance, glacierSpikes, blizzard, avalanche, absoluteZero),
                        true, FrostQueen::onEnterEnrage,
                        this::absoluteZeroNova));

        this.lootTable = new LootTable()
                .guaranteed(() -> new GlacialScepter(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> frostforgedRegalia(Material.LEATHER_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> frostforgedRegalia(Material.LEATHER_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), FrostQueen::craftingMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), FrostQueen::crownOfWinter);
    }

    /**
     * Thin Ice: the arena breaks into floes that shrink for as long as they exist, and when the cycle
     * ends everything that is not a floe gives way under whoever is on it. Her opener teaches the room
     * before it teaches anything else — no lockout and no chore, just a floor that keeps getting
     * smaller while she novas into it.
     */
    private PhaseMechanic thinIce(BossInstance instance) {
        return new ShrinkingFloesMechanic(instance, "Thin Ice", PALE_ICE,
                configInt("thin-ice-floe-count", 3),
                configDouble("thin-ice-start-radius", 7.0),
                configDouble("thin-ice-end-radius", 3.0),
                configInt("thin-ice-cycle-ticks", 220),
                configDouble("thin-ice-plunge-damage", 14.0),
                configInt("thin-ice-plunge-freeze-ticks", 60),
                configDouble("thin-ice-placement-fraction", 0.55));
    }

    /**
     * Frozen Court: she encases two players solid and they suffocate on a clock. Unlike the rescue
     * gate this replaces, she stays fully hittable throughout — so the group is trading damage for
     * their friends' lives rather than waiting for permission to resume.
     */
    private PhaseMechanic frozenCourt(BossInstance instance) {
        return new TrappedAlliesMechanic(instance, "FROZEN COURT", FROST_BLUE, Material.PACKED_ICE,
                Sound.BLOCK_GLASS_PLACE,
                configInt("frozen-court-captives", 2),
                configInt("frozen-court-first-delay-ticks", 90),
                configInt("frozen-court-recapture-delay-ticks", 140),
                configInt("frozen-court-suffocate-ticks", 160),
                configInt("frozen-court-rescue-ticks", 70),
                configDouble("frozen-court-drain-per-second", 1.6),
                configDouble("frozen-court-fail-damage", 20.0),
                configDouble("frozen-court-fail-heal", 0.0));
    }

    /**
     * Absolute Winter: Winter's Ward seals her completely and no amount of damage wears it down. It
     * drops on a telegraphed rhythm, and one hit inside that opening shatters it. Her last stand is a
     * reaction test rather than another damage check — the enrage tuning goes into a shorter window
     * and a harsher miss, not into a bigger health wall.
     */
    private PhaseMechanic absoluteZeroNova(BossInstance instance) {
        MechanicField heatPockets = new DriftingDiscField(
                configInt("absolute-zero-pockets", 2),
                configDouble("absolute-zero-pocket-radius", 4.5),
                configDouble("absolute-zero-pocket-drift", 2.2),
                Color.fromRGB(255, 170, 90),
                configDouble("absolute-zero-pocket-spread", 0.5));
        return new StackMeterMechanic(instance, "Freezing", FROST_BLUE, heatPockets,
                configDouble("absolute-zero-gain-per-second", 9.0),
                configDouble("absolute-zero-drain-per-second", 22.0),
                configDouble("absolute-zero-cap", 100.0),
                StackMeterMechanic.crippling(
                        configDouble("absolute-zero-damage", 16.0),
                        configInt("absolute-zero-freeze-ticks", 70), 3),
                Component.text("❄ ABSOLUTE ZERO ❄", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true),
                Component.text("Find the warmth — it moves", NamedTextColor.GRAY),
                "warming", "FREEZING — find a warm pocket");
    }

    @Override
    public String id() {
        return "frost_queen";
    }

    @Override
    public Component displayName() {
        return Component.text("The Frost Queen", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.STRAY;
    }

    @Override
    public List<BossPhase> phases() {
        return phases;
    }

    @Override
    public LootTable lootTable() {
        return lootTable;
    }

    /** Offset from her phase boundaries (0.75 / 0.40 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.85, 0.55, 0.25}),
                new BeaconEvent(plugin, id(), new double[] {0.65, 0.32}));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.SNOWFLAKE, "boss.frost_queen.ambient", Sound.WEATHER_RAIN,
                true, Biome.FROZEN_PEAKS);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("❄ THE FROST QUEEN ❄", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("Winter's cruel sovereign descends", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("❄ THE FROST QUEEN IS SHATTERED ❄", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The endless winter finally thaws", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            equipment.setHelmet(dyedArmor(Material.LEATHER_HELMET));
            equipment.setChestplate(dyedArmor(Material.LEATHER_CHESTPLATE));
            equipment.setLeggings(dyedArmor(Material.LEATHER_LEGGINGS));
            equipment.setBoots(dyedArmor(Material.LEATHER_BOOTS));
        }
        // A rising column of snow + a spinning crown of frost as the queen takes her throne.
        Fx.expandingRings(instance.plugin(), loc, Particle.SNOWFLAKE, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), FROST_BLUE, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SNOWBALL, 25, 0.6);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.6f);
        Fx.sound(loc, Sound.BLOCK_GLASS_PLACE, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Winter deepens: a swirling snow-storm burst engulfs the arena.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SNOWFLAKE, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), PALE_ICE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.SNOWFLAKE, 7.0, 3, 2L);
        Fx.sound(loc, Sound.WEATHER_RAIN, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 0.8f);
        instance.showTitle(
                Component.text("Winter Deepens", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("The queen calls the storm to her side", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Heart of Ice: an inward-collapsing ring of frost hardening around the queen.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.ITEM_SNOWBALL, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), FROST_BLUE, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, PALE_ICE, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 0.4f);
        instance.showTitle(
                Component.text("Heart of Ice", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("No warmth remains in her frozen heart", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Absolute Winter: a towering frost helix and a spinning ice crown overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SNOWFLAKE, 55, 0.8);
        Fx.coloredRing(loc, FROST_BLUE, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.BLUE_ICE, 1.4f, 100, 12.0);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.4f);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        instance.showTitle(
                Component.text("❄ ABSOLUTE WINTER ❄", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true),
                Component.text("She will bury this world in ice", NamedTextColor.GRAY));
    }

    private static ItemStack dyedArmor(Material material) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(ARMOR_BLUE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack frostforgedRegalia(Material material) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(ARMOR_BLUE);
            meta.displayName(Component.text("Frostforged Regalia", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack craftingMaterials() {
        return new ItemStack(Material.BLUE_ICE, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfWinter() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of Winter", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Frozen for a thousand years, and colder still.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
