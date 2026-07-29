package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CinderNovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.EruptionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FireTrailAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FirestormAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FlameBreathAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.LavaWaveAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MagmaThrowAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MeteorRainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MoltenOverloadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.inferno.InfernoPhases;
import dev.rbm72.weaponsplugin.boss.events.ConvergenceNukeEvent;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Inferno Warlord — a foundry deliberately overloaded until it melts (design doc
 * {@code docs/superpowers/specs/2026-07-27-boss-rework-batch-2.md} §1). The roster's player-authored
 * terrain boss: lava rises in stages and the counter is the most Minecraft thing possible — pour water
 * on lava to make stone and obsidian and build your own footing — while he lights real crawling fuse
 * lines toward real stacked TNT and drops burning logs from the ceiling.
 * <p>
 * Four phases, wired through {@link InfernoPhases}, each with its own non-health exit condition
 * (§0.2 rule 2): The Forge exits on a fuse cut, Flood the Foundry on constructed ground held, Powder Keg
 * on three clusters neutralised, and Meltdown — the roster's mandatory ungated phase — is a pure damage
 * race on whatever platform network the group built. Every collaborating system (the Burning meter,
 * cauldrons, rising lava, fire trails, TNT clusters, magma hazards, burning logs, Cinder Nova) lives in
 * {@code dev.rbm72.weaponsplugin.boss.bosses.inferno}; this class only wires phases, attacks and flavour.
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

        this.phases = List.of(
                // P1 — The Forge: baseline. Fire Trails and the first fuse teach the rules the rest of
                // the fight is built on before anything else is layered over them.
                new BossPhase("The Forge", 1.0,
                        List.of(flameBreath, fireTrail, magmaThrow, eruption),
                        false, InfernoWarlord::onEnterForge,
                        instance -> InfernoPhases.forge(instance, 0.72)),
                // P2 — Flood the Foundry: rising lava turns bridging from optional to mandatory.
                new BossPhase("Flood the Foundry", 0.72,
                        List.of(lavaWave, meteorRain, eruption, magmaThrow),
                        false, InfernoWarlord::onEnterFloodTheFoundry,
                        instance -> InfernoPhases.floodTheFoundry(instance, 0.48)),
                // P3 — Powder Keg: the highest-pressure phase — several live fuses at once, over a floor
                // that is still rising underneath the scramble.
                new BossPhase("Powder Keg", 0.48,
                        List.of(firestorm, lavaWave, meteorRain, cinderNova, moltenOverload),
                        false, InfernoWarlord::onEnterPowderKeg,
                        instance -> InfernoPhases.powderKeg(instance, 0.20)),
                // P4 — Meltdown: the roster's mandatory ungated phase. Almost everything is lava; burst
                // him down on whatever ground the group earned.
                new BossPhase("Meltdown", 0.20,
                        List.of(flameBreath, firestorm, moltenOverload, meteorRain, cinderNova, eruption, lavaWave),
                        true, InfernoWarlord::onEnterMeltdown,
                        InfernoPhases::meltdown));

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

    /** Offset from his phase boundaries (0.72 / 0.48 / 0.20) so milestones land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.85, 0.30}),
                new ConvergenceNukeEvent(plugin, id(), new double[] {0.60},
                        "MOLTEN OVERLOAD", "Get away from him — everything nearby melts"));
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
        return Component.text("The room will kill you before he does — find a bucket", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("🔥 THE INFERNO WARLORD IS EXTINGUISHED 🔥", NamedTextColor.RED).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The last ember fades to ash", NamedTextColor.GRAY);
    }

    private static void onEnterForge(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
            equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        }
        // He burns perpetually — bosses are immune to fire damage via the damage listener.
        instance.entity().setFireTicks(Integer.MAX_VALUE);
        Fx.expandingRings(instance.plugin(), loc, Particle.FLAME, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), EMBER, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.LAVA, 20, 0.6);
        Fx.sound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.5f);
    }

    private static void onEnterFloodTheFoundry(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.FLAME, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DEEP_FIRE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.LAVA, 7.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_LAVA_AMBIENT, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);
        instance.showTitle(
                Component.text("Flood the Foundry", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("The floor is rising — build your own or lose it", NamedTextColor.GRAY));
    }

    private static void onEnterPowderKeg(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.LAVA, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), EMBER, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, DEEP_FIRE, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_BURN, 0.7f, 0.4f);
        instance.showTitle(
                Component.text("Powder Keg", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("He stacks TNT and lights the fuses — split up", NamedTextColor.GRAY));
    }

    private static void onEnterMeltdown(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1, 0), Particle.FLAME, 55, 0.8);
        Fx.coloredRing(loc, EMBER, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.MAGMA_BLOCK, 1.4f, 100, 12.0);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        // Title/particles for the flood itself are handled by MeltdownPhase.onArm — this cinematic is
        // purely the boss's own escalation, running alongside it.
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
