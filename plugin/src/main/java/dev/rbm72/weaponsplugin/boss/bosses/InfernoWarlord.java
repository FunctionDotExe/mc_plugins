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
import dev.rbm72.weaponsplugin.boss.events.PullNukeEvent;
import dev.rbm72.weaponsplugin.boss.mechanics.EscalatingDoomMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.ExpandingRingMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.MechanicField;
import dev.rbm72.weaponsplugin.boss.mechanics.StackMeterMechanic;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AfflictionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CinderNovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.EruptionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FireTrailAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FirestormAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FlameBreathAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MoltenOverloadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.LavaWaveAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MagmaThrowAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MeteorRainAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.CinderCleaver;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Inferno Warlord — a burning conqueror wreathed in perpetual fire. Four
 * phases (100-75 / 75-40 / 40-15 / enrage &lt;15) built on a shared fire kit,
 * with Meteor as the signature terrain-wrecking move and an arena-wide
 * Firestorm meteor barrage in enrage.
 */
public final class InfernoWarlord extends Boss {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);
    private static final Color DEEP_FIRE = Color.fromRGB(200, 40, 0);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public InfernoWarlord(WeaponsPlugin plugin) {
        super(plugin);

        FlameBreathAttack flameBreath = new FlameBreathAttack(plugin);
        FireTrailAttack fireTrail = new FireTrailAttack(plugin);
        MeteorRainAttack meteorRain = new MeteorRainAttack(plugin);
        MagmaThrowAttack magmaThrow = new MagmaThrowAttack(plugin);
        EruptionAttack eruption = new EruptionAttack(plugin);
        CinderNovaAttack cinderNova = new CinderNovaAttack(plugin);
        LavaWaveAttack lavaWave = new LavaWaveAttack(plugin);
        FirestormAttack firestorm = new FirestormAttack(plugin);
        MoltenOverloadAttack moltenOverload = new MoltenOverloadAttack(plugin);
        AfflictionAttack searingBrand = new AfflictionAttack(plugin, "inferno_warlord", "Searing Brand", EMBER,
                Sound.BLOCK_FIRE_AMBIENT);

        this.phases = List.of(
                // Moat Eruption: the lava moat throws a wall outward, and each wave leaves fewer ways
                // through than the last. A timing problem rather than a standing-still problem.
                new BossPhase("Moat Eruption", 1.0,
                        List.of(flameBreath, fireTrail, meteorRain, magmaThrow),
                        false, InfernoWarlord::onEnterPhase1,
                        this::moatEruption),
                // Molten Brand: standing in his heat brands you, and the brand only cools at range. A
                // melee group has to keep breaking off and coming back rather than parking on him.
                new BossPhase("Molten Fury", 0.75,
                        List.of(eruption, cinderNova, flameBreath, meteorRain, searingBrand),
                        false, InfernoWarlord::onEnterPhase2,
                        this::moltenBrand),
                // Ungated: the floor is lava and he is not hiding behind anything. Pure survival race.
                new BossPhase("Hellfire Awakens", 0.40,
                        List.of(lavaWave, meteorRain, eruption, magmaThrow, moltenOverload, searingBrand),
                        false, InfernoWarlord::onEnterPhase3),
                // The forge runs hotter the longer he is allowed to live. First boss on the roster whose
                // failure state feeds him rather than only hurting you (design rule 4).
                new BossPhase("Infernal Cataclysm", 0.15,
                        List.of(flameBreath, meteorRain, eruption, cinderNova, magmaThrow, firestorm, moltenOverload),
                        true, InfernoWarlord::onEnterEnrage,
                        this::risingForge));

        this.lootTable = new LootTable()
                .guaranteed(() -> new CinderCleaver(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> emberforgedArmor(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> emberforgedArmor(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), InfernoWarlord::craftingMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), InfernoWarlord::crownOfCinders);
    }

    @Override
    public String id() {
        return "inferno_warlord";
    }

    @Override
    public Component displayName() {
        return Component.text("The Inferno Warlord", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.WITHER_SKELETON;
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
     * Moat Eruption: the moat throws a wall of fire outward on a clock, torn by a few gaps that drift
     * as the wave travels. Each successive wave has one fewer gap, down to a floor that always leaves
     * a way out — escalation without an unsolvable state.
     */
    private PhaseMechanic moatEruption(BossInstance instance) {
        return new ExpandingRingMechanic(instance, "Moat Eruption", EMBER,
                configInt("moat-wave-interval-ticks", 150),
                configDouble("moat-wave-speed", 0.42),
                configDouble("moat-band-thickness", 2.6),
                configInt("moat-starting-gaps", 4),
                configInt("moat-minimum-gaps", 2),
                configDouble("moat-gap-half-width-degrees", 20.0),
                configDouble("moat-gap-drift-degrees-per-second", 14.0),
                configDouble("moat-wave-damage", 13.0),
                configInt("moat-burn-ticks", 40));
    }

    /**
     * Molten Brand: proximity to him brands you, and the brand only cools at range. When it maxes out
     * it goes off on the carrier and everyone stacked with them — so a melee group has to rotate out
     * and back rather than parking in his lap for the whole band.
     */
    private PhaseMechanic moltenBrand(BossInstance instance) {
        MechanicField coolAir = MechanicField.beyondBoss(configDouble("molten-brand-cool-distance", 11.0));
        return new StackMeterMechanic(instance, "Molten Brand", DEEP_FIRE, coolAir,
                configDouble("molten-brand-gain-per-second", 8.0),
                configDouble("molten-brand-vent-per-second", 16.0),
                configDouble("molten-brand-cap", 100.0),
                StackMeterMechanic.selfDetonate(
                        configDouble("molten-brand-blast-radius", 5.0),
                        configDouble("molten-brand-centre-damage", 18.0),
                        configDouble("molten-brand-splash-damage", 9.0)),
                Component.text("MOLTEN BRAND", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("His heat marks you — back off to vent it", NamedTextColor.GRAY),
                "cooling", "BRANDED — get away from him");
    }

    /**
     * The Rising Forge: his last stand runs on a clock the group can only hold back with sustained
     * damage. Let it top out and he permanently grows, speeds up and hardens — the first boss on the
     * roster where ignoring a mechanic compounds instead of merely hurting.
     */
    private PhaseMechanic risingForge(BossInstance instance) {
        return new EscalatingDoomMechanic(instance, "The Forge Rises", DEEP_FIRE,
                configDouble("forge-fill-per-second", 4.0),
                configDouble("forge-damage-per-point", 5.0),
                configDouble("forge-cap", 100.0),
                configDouble("forge-scale-step", 1.08),
                configDouble("forge-speed-step", 1.06),
                configDouble("forge-harden-step", 0.06),
                configDouble("forge-overflow-damage", 12.0));
    }

    /** Offset from his phase boundaries (0.75 / 0.40 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.88, 0.34}),
                new PullNukeEvent(plugin, id(), new double[] {0.58, 0.22},
                        "WARLORD'S CRUCIBLE", "He is dragging you into the forge — break away"));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.FLAME, "boss.inferno_warlord.ambient", Sound.BLOCK_FIRE_AMBIENT,
                true, Biome.BASALT_DELTAS);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("🔥 THE INFERNO WARLORD 🔥", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A conqueror wreathed in living flame", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("🔥 THE INFERNO WARLORD IS EXTINGUISHED 🔥", NamedTextColor.RED).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The last ember fades to ash", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
            equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        }
        // The warlord burns perpetually — bosses are immune to fire damage via the damage listener.
        instance.entity().setFireTicks(Integer.MAX_VALUE);
        // A rising pillar of flame + a crown of embers as he takes the field.
        Fx.expandingRings(instance.plugin(), loc, Particle.FLAME, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), EMBER, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.LAVA, 20, 0.6);
        Fx.sound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Molten Fury: a swelling burst of magma engulfs the arena.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.FLAME, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DEEP_FIRE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.LAVA, 7.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_LAVA_AMBIENT, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);
        instance.showTitle(
                Component.text("Molten Fury", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("His rage boils over into molten rock", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Hellfire Awakens: an inward-collapsing ring of hellfire hardens around him.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.LAVA, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), EMBER, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, DEEP_FIRE, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_BURN, 0.7f, 0.4f);
        instance.showTitle(
                Component.text("Hellfire Awakens", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("The fires of the abyss answer his call", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Infernal Cataclysm: a towering fire helix and a spinning magma icon overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.FLAME, 55, 0.8);
        Fx.coloredRing(loc, EMBER, 1.6f, 4.5, 24, 0);
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
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        instance.showTitle(
                Component.text("🔥 INFERNAL CATACLYSM 🔥", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("He will burn this world to cinders", NamedTextColor.GRAY));
    }

    private static ItemStack emberforgedArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Emberforged Plate", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack craftingMaterials() {
        return new ItemStack(Material.BLAZE_POWDER, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfCinders() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of Cinders", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Still warm, and always will be.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
