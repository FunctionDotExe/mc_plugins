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
import dev.rbm72.weaponsplugin.boss.mechanics.ContagionLedgerMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.HazardPatchMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.TelegraphedEruptionMechanic;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AfflictionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlightSigilsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.CorruptionSpreadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MiasmaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.PandemicAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.PlagueBoltAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.PlagueSwarmAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.PoisonCloudAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.RottingGraspAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.PandemicSurgeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SummonUndeadAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Rotscourge;
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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Plague Warden — a rotting harbinger of pestilence. Four phases
 * (100-75 / 75-40 / 40-15 / enrage &lt;15) built on a shared poison kit, with
 * Corruption Spread as the signature terrain-rotting move and an arena-wide
 * Pandemic outbreak in enrage.
 */
public final class PlagueWarden extends Boss {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);
    private static final Color SICKLY = Color.fromRGB(150, 200, 80);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public PlagueWarden(WeaponsPlugin plugin) {
        super(plugin);

        PoisonCloudAttack poisonCloud = new PoisonCloudAttack(plugin);
        SummonUndeadAttack summonUndead = new SummonUndeadAttack(plugin);
        PlagueBoltAttack plagueBolt = new PlagueBoltAttack(plugin);
        CorruptionSpreadAttack corruptionSpread = new CorruptionSpreadAttack(plugin);
        MiasmaAttack miasma = new MiasmaAttack(plugin);
        RottingGraspAttack rottingGrasp = new RottingGraspAttack(plugin);
        PlagueSwarmAttack plagueSwarm = new PlagueSwarmAttack(plugin);
        PandemicAttack pandemic = new PandemicAttack(plugin);
        BlightSigilsAttack blightSigils = new BlightSigilsAttack(plugin);
        PandemicSurgeAttack pandemicSurge = new PandemicSurgeAttack(plugin);
        AfflictionAttack virulentRot = new AfflictionAttack(plugin, "plague_warden", "Virulent Rot", TOXIC,
                Sound.ENTITY_ZOMBIE_VILLAGER_CURE);

        this.phases = List.of(
                // Spore Cloud Maze: the floor keeps closing. Nothing is gated — the arena simply gets
                // smaller and more poisonous while he fights, so standing still stops being an option.
                new BossPhase("Spore Bloom", 1.0,
                        List.of(poisonCloud, summonUndead, plagueBolt, corruptionSpread),
                        false, PlagueWarden::onEnterPhase1,
                        this::sporeCloudMaze),
                // Ungated: no mechanic at all, just the whole rot kit trying to kill you. His one
                // stretch of straight fighting, and the contrast that makes the rest read as designed.
                new BossPhase("Rot Takes Hold", 0.75,
                        List.of(miasma, rottingGrasp, summonUndead, poisonCloud, blightSigils, virulentRot),
                        false, PlagueWarden::onEnterPhase2),
                // Contagion Ledger: his signature. Every wound you deal is written against your name
                // and the room settles the account together — so the group has to take turns.
                new BossPhase("Pestilence", 0.40,
                        List.of(plagueSwarm, corruptionSpread, miasma, summonUndead, blightSigils, pandemicSurge, virulentRot),
                        false, PlagueWarden::onEnterPhase3,
                        this::contagionLedger),
                // Withering Roots: the ground marks whoever stands on it and then grabs them. A pure
                // footwork check to finish, and the thing that finally forbids camping his feet.
                new BossPhase("The Great Plague", 0.15,
                        List.of(poisonCloud, plagueBolt, corruptionSpread, rottingGrasp, plagueSwarm, pandemic,
                                blightSigils, pandemicSurge),
                        true, PlagueWarden::onEnterEnrage,
                        this::witheringRoots));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Rotscourge(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> blightplateArmor(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> blightplateArmor(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), PlagueWarden::craftingMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), PlagueWarden::plaguebearersCrown);
    }

    @Override
    public String id() {
        return "plague_warden";
    }

    @Override
    public Component displayName() {
        return Component.text("The Plague Warden", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.HUSK;
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
     * Spore Cloud Maze: clouds keep sprouting, keep growing, and only leave when they are finished —
     * so the usable floor shrinks steadily. Capped well short of sealing the arena, and patches never
     * appear directly under a player, so it denies space rather than dealing unavoidable damage.
     */
    private PhaseMechanic sporeCloudMaze(BossInstance instance) {
        return new HazardPatchMechanic(instance, "Spore Maze", TOXIC, Particle.SPORE_BLOSSOM_AIR,
                configInt("spore-spawn-interval-ticks", 70),
                configInt("spore-max-patches", 7),
                configDouble("spore-start-radius", 2.5),
                configDouble("spore-max-radius", 5.0),
                configDouble("spore-growth-per-second", 0.25),
                configInt("spore-lifetime-ticks", 320),
                configDouble("spore-damage-per-second", 3.0),
                PotionEffectType.POISON, 0,
                configDouble("spore-placement-fraction", 0.75));
    }

    /**
     * Contagion Ledger: his signature, and the reason this fight cannot be carried by one player's
     * damage. Personal debt only bleeds off while you are not swinging, and the room's burst hits
     * each player for what they personally owe.
     */
    private PhaseMechanic contagionLedger(BossInstance instance) {
        return new ContagionLedgerMechanic(instance, SICKLY,
                configDouble("contagion-stacks-per-damage", 1.0),
                configDouble("contagion-decay-per-second", 9.0),
                configDouble("contagion-ledger-cap", 260.0),
                configDouble("contagion-burst-base-damage", 4.0),
                configDouble("contagion-burst-damage-per-stack", 0.16),
                configInt("contagion-sickness-ticks", 100),
                configDouble("contagion-heal-per-stack", 0.05));
    }

    /**
     * Withering Roots: the ground telegraphs under whoever is standing still and then snares them.
     * Damage is modest; the root is the real cost, since the rest of his kit is still firing.
     */
    private PhaseMechanic witheringRoots(BossInstance instance) {
        return new TelegraphedEruptionMechanic(instance, "Withering Roots", TOXIC,
                Particle.SPORE_BLOSSOM_AIR, Sound.BLOCK_GRASS_BREAK, Sound.ENTITY_ZOMBIE_INFECT,
                configInt("roots-volley-interval-ticks", 70),
                configInt("roots-telegraph-ticks", 34),
                configDouble("roots-radius", 2.6),
                configDouble("roots-damage", 10.0),
                configInt("roots-snare-ticks", 45),
                configInt("roots-marks-per-volley", 3));
    }

    /** Offset from his phase boundaries (0.75 / 0.40 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.88, 0.26}),
                new ConvergenceNukeEvent(plugin, id(), new double[] {0.60, 0.32},
                        "PANDEMIC BLOOM", "Get clear of him — everything within reach dies"));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.SPORE_BLOSSOM_AIR, "boss.plague_warden.ambient", Sound.BLOCK_SCULK_SPREAD,
                true, Biome.SWAMP);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("☣ THE PLAGUE WARDEN ☣", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A rotting harbinger of pestilence rises", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("☣ THE PLAGUE WARDEN HAS ROTTED AWAY ☣", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The sickness recedes at last", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // No equipment — the warden is a festering, rotting thing. A rising bloom of spores as it takes the field.
        Fx.expandingRings(instance.plugin(), loc, Particle.SPORE_BLOSSOM_AIR, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), TOXIC, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SLIME, 20, 0.6);
        Fx.sound(loc, Sound.ENTITY_HUSK_AMBIENT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 0.6f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Rot Takes Hold: a swelling cloud of decay engulfs the warden.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SPORE_BLOSSOM_AIR, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SICKLY, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.ITEM_SLIME, 7.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_SCULK_SPREAD, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_ZOMBIE_INFECT, 0.8f, 0.8f);
        instance.showTitle(
                Component.text("Rot Takes Hold", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Decay creeps across the battlefield", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Pestilence: an inward-collapsing ring of spores hardens around the warden.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.ITEM_SLIME, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), TOXIC, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, SICKLY, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.7f, 0.4f);
        instance.showTitle(
                Component.text("Pestilence", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The air itself turns to poison", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The Great Plague: a towering spore helix and a spinning fermented-eye icon overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SPORE_BLOSSOM_AIR, 55, 0.8);
        Fx.coloredRing(loc, TOXIC, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.FERMENTED_SPIDER_EYE, 1.4f, 100, 12.0);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !instance.entity().isValid()) {
                    cancel();
                    return;
                }
                Fx.helixFrame(instance.entity().getLocation(), Particle.SPORE_BLOSSOM_AIR, 1.3, 3, ticks * 0.5, ticks * 0.15);
                ticks++;
            }
        }.runTaskTimer(instance.plugin(), 0L, 1L);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.7f);
        Fx.sound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 0.5f);
        instance.showTitle(
                Component.text("☣ THE GREAT PLAGUE ☣", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Let the whole world rot", NamedTextColor.GRAY));
    }

    private static ItemStack blightplateArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Blightplate", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack craftingMaterials() {
        return ThreadLocalRandom.current().nextBoolean()
                ? new ItemStack(Material.ROTTEN_FLESH, ThreadLocalRandom.current().nextInt(4, 12))
                : new ItemStack(Material.FERMENTED_SPIDER_EYE, ThreadLocalRandom.current().nextInt(1, 4));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack plaguebearersCrown() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Plaguebearer's Crown", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("It still smells of rot.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
