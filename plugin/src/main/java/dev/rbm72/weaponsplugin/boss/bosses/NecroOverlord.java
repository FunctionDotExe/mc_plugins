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
import dev.rbm72.weaponsplugin.boss.mechanics.ParasiteAddMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.SharedPoolMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.TelegraphedEruptionMechanic;
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
                // Grave Bloom: gravestones sprout under whoever stands still and the hands beneath them
                // grab. A footwork opener — nothing is gated, the ground simply objects to camping.
                new BossPhase("Grave Bloom", 1.0,
                        List.of(raiseUndead, boneSpikes, deathBolt, graveGrasp),
                        false, NecroOverlord::onEnterPhase1,
                        this::graveBloom),
                // Bound Trio: his signature, and the roster's one true coordination check. Three wraiths
                // share one life and the binding drains only by damage all three took together, so no
                // amount of focused damage from one player moves it at all.
                new BossPhase("The Dead Rise", 0.75,
                        List.of(soulDrain, witherCloud, raiseUndead, boneSpikes, soulAnchors, necroticDecay),
                        false, NecroOverlord::onEnterPhase2,
                        this::boundTrio),
                // Soul Harvest: an orb latches onto one of you, drains them and feeds him while it
                // lives. He never stops being hittable — the group is choosing where damage goes.
                new BossPhase("Necropolis", 0.40,
                        List.of(boneStorm, soulDrain, witherCloud, graveGrasp, soulAnchors, mirror, necroticDecay),
                        false, NecroOverlord::onEnterPhase3,
                        this::soulHarvest),
                // Ungated: nothing left to hide behind, just the whole army and the lich swinging.
                new BossPhase("Army of the Dead", 0.15,
                        List.of(boneSpikes, deathBolt, graveGrasp, soulDrain, boneStorm, armyOfTheDead, soulAnchors,
                                mirror),
                        true, NecroOverlord::onEnterEnrage));

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

    /**
     * Grave Bloom: headstones break the surface under anyone standing still, and the hands beneath
     * them root whoever is slow to move off. The snare matters more than the damage — his whole kit
     * is still firing while you are held.
     */
    private PhaseMechanic graveBloom(BossInstance instance) {
        return new TelegraphedEruptionMechanic(instance, "Grave Bloom", NECROTIC,
                Particle.SOUL, Sound.BLOCK_BONE_BLOCK_BREAK, Sound.ENTITY_ZOMBIE_AMBIENT,
                configInt("grave-bloom-interval-ticks", 75),
                configInt("grave-bloom-telegraph-ticks", 34),
                configDouble("grave-bloom-radius", 2.8),
                configDouble("grave-bloom-damage", 11.0),
                configInt("grave-bloom-snare-ticks", 45),
                configInt("grave-bloom-marks-per-volley", 3));
    }

    /**
     * Bound Trio: three wraiths, one shared life, and a binding that only gives ground to damage all
     * three of them took inside the same window. He is blunted rather than immune while it stands, so
     * a group that cannot split its damage still finishes — just much later.
     */
    private PhaseMechanic boundTrio(BossInstance instance) {
        return new SharedPoolMechanic(instance, "Bound Trio", "Bound Wraith", NECROTIC,
                EntityType.WITHER_SKELETON,
                configInt("bound-trio-count", 3),
                configDouble("bound-trio-pool", 120.0),
                configDouble("bound-trio-display-health", 60.0),
                configInt("bound-trio-sync-window-ticks", 80),
                configInt("bound-trio-exposed-ticks", 170),
                configDouble("bound-trio-exposed-multiplier", 2.2),
                configInt("bound-trio-stagger-ticks", 60),
                configInt("bound-trio-respawn-delay-ticks", 140),
                configDouble("bound-trio-fail-heal-per-survivor", 12.0),
                configDouble("bound-trio-spawn-radius", 6.0));
    }

    /**
     * Soul Harvest: a tethered orb drains one player and feeds him the whole time it lives, and hands
     * him a far bigger meal if it survives its window. Late roster, so failing it hardens him
     * permanently as well as healing him (design rule 4).
     */
    private PhaseMechanic soulHarvest(BossInstance instance) {
        return new ParasiteAddMechanic(instance, "Soul Harvest", "Soul Tether", BONE,
                EntityType.VEX,
                configDouble("soul-harvest-health", 40.0),
                true,
                configInt("soul-harvest-window-ticks", 200),
                configInt("soul-harvest-respawn-ticks", 140),
                configDouble("soul-harvest-heal-per-second", 2.5),
                configDouble("soul-harvest-drain-per-second", 2.0),
                configDouble("soul-harvest-reattach-heal", 45.0),
                configDouble("soul-harvest-reattach-hardening", 0.05),
                configInt("soul-harvest-exposed-ticks", 150),
                configDouble("soul-harvest-exposed-multiplier", 1.8));
    }

    /** Offset from his phase boundaries (0.75 / 0.40 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.88, 0.30}),
                new PullNukeEvent(plugin, id(), new double[] {0.60, 0.24},
                        "GRASP OF THE GRAVE", "The dead are pulling you in — break away"));
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
