package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AfflictionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ArmyOfTheDeadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BoneSpikesAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BoneStormAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DeathBoltAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GraveGraspAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MirrorOfTheDamnedAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.RaiseUndeadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SoulAnchorsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SoulDrainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WitherCloudAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Soulharvester;
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
 * The Necro Overlord — a lich-king of bone and soul-fire. Four phases
 * (100-75 / 75-40 / 40-15 / enrage &lt;15) built on a necromantic kit: raising
 * the dead, erupting bone spikes as its signature terrain grief, draining
 * souls to heal, and unleashing an entire undead legion in enrage.
 */
public final class NecroOverlord extends Boss {

    private static final Color BONE = Color.fromRGB(235, 235, 210);
    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public NecroOverlord(WeaponsPlugin plugin) {
        super(plugin);

        RaiseUndeadAttack raiseUndead = new RaiseUndeadAttack(plugin);
        BoneSpikesAttack boneSpikes = new BoneSpikesAttack(plugin);
        DeathBoltAttack deathBolt = new DeathBoltAttack(plugin);
        GraveGraspAttack graveGrasp = new GraveGraspAttack(plugin);
        SoulDrainAttack soulDrain = new SoulDrainAttack(plugin);
        WitherCloudAttack witherCloud = new WitherCloudAttack(plugin);
        BoneStormAttack boneStorm = new BoneStormAttack(plugin);
        ArmyOfTheDeadAttack armyOfTheDead = new ArmyOfTheDeadAttack(plugin);
        SoulAnchorsAttack soulAnchors = new SoulAnchorsAttack(plugin);
        MirrorOfTheDamnedAttack mirror = new MirrorOfTheDamnedAttack(plugin);
        AfflictionAttack necroticDecay = new AfflictionAttack(plugin, "necro_overlord", "Necrotic Decay", NECROTIC,
                Sound.ENTITY_WITHER_AMBIENT);

        this.phases = List.of(
                new BossPhase("Phase 1", 1.0,
                        List.of(raiseUndead, boneSpikes, deathBolt, graveGrasp),
                        false, NecroOverlord::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Bone Reliquaries", NamedTextColor.DARK_GREEN),
                                Material.SOUL_LANTERN, NECROTIC, 0, false)),
                new BossPhase("The Dead Rise", 0.75,
                        List.of(soulDrain, witherCloud, raiseUndead, boneSpikes, soulAnchors, necroticDecay),
                        false, NecroOverlord::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Withered Reliquaries", NamedTextColor.DARK_GREEN),
                                Material.SOUL_LANTERN, NECROTIC, 1, false)),
                new BossPhase("Necropolis", 0.40,
                        List.of(boneStorm, soulDrain, witherCloud, graveGrasp, soulAnchors, mirror, necroticDecay),
                        false, NecroOverlord::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Grave Sigils", NamedTextColor.GREEN),
                                Material.BONE_BLOCK, BONE, 2, false)),
                new BossPhase("Army of the Dead", 0.15,
                        List.of(boneSpikes, deathBolt, graveGrasp, soulDrain, boneStorm, armyOfTheDead, soulAnchors,
                                mirror),
                        true, NecroOverlord::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Lich's Anchor", NamedTextColor.DARK_GREEN),
                                Material.NETHER_STAR, NECROTIC, 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Soulharvester(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> graveboundArmor(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> graveboundArmor(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), NecroOverlord::craftingMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), NecroOverlord::lichsCrown);
    }

    @Override
    public String id() {
        return "necro_overlord";
    }

    @Override
    public Component displayName() {
        return Component.text("The Necro Overlord", NamedTextColor.DARK_GREEN)
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

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.SCULK_SOUL, "boss.necro_overlord.ambient", Sound.PARTICLE_SOUL_ESCAPE,
                true, Biome.DARK_FOREST);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("☠ THE NECRO OVERLORD ☠", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A lich-king rises to command the dead", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("☠ THE NECRO OVERLORD IS UNMADE ☠", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The dead return to their rest", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        }
        // The overlord takes the field wreathed in rising soul-fire and a crown of bone-white light.
        Fx.expandingRings(instance.plugin(), loc, Particle.SCULK_SOUL, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), NECROTIC, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SOUL, 25, 0.6);
        Fx.sound(loc, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The Dead Rise: a swelling burst of souls erupts from the ground around the overlord.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SCULK_SOUL, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), BONE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL, 7.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_SKELETON_AMBIENT, 0.8f, 0.5f);
        instance.showTitle(
                Component.text("The Dead Rise", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The overlord's legion stirs beneath the soil", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Necropolis: an inward-collapsing ring of soul-fire hardens around the overlord.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SOUL_FIRE_FLAME, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), NECROTIC, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, BONE, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.BLOCK_SOUL_SAND_STEP, 0.8f, 0.4f);
        instance.showTitle(
                Component.text("Necropolis", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The very ground becomes a graveyard", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Army of the Dead: a towering soul helix and a spinning skull icon overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SCULK_SOUL, 55, 0.8);
        Fx.coloredRing(loc, NECROTIC, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.WITHER_SKELETON_SKULL, 1.4f, 100, 12.0);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !instance.entity().isValid()) {
                    cancel();
                    return;
                }
                Fx.helixFrame(instance.entity().getLocation(), Particle.SOUL, 1.3, 3, ticks * 0.5, ticks * 0.15);
                ticks++;
            }
        }.runTaskTimer(instance.plugin(), 0L, 1L);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.7f);
        instance.showTitle(
                Component.text("☠ ARMY OF THE DEAD ☠", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Rise, my legion — leave nothing living", NamedTextColor.GRAY));
    }

    private static ItemStack graveboundArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Gravebound Regalia", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack craftingMaterials() {
        return ThreadLocalRandom.current().nextBoolean()
                ? new ItemStack(Material.BONE, ThreadLocalRandom.current().nextInt(4, 12))
                : new ItemStack(Material.SOUL_SAND, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack lichsCrown() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Lich's Crown", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("It hums with the whispers of the dead.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
