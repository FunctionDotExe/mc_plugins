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
import dev.rbm72.weaponsplugin.boss.bosses.plague.PlaguePhases;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Plague Warden — rot and sculk consuming an arena, and each other's bodies as the vector. Four
 * phases (100-70 / 70-45 / 45-20 / &lt;20), all built on {@code plague.PlaguePhases}: the Infection meter
 * and his pyres run the whole fight, and each phase adds one more structural demand (batch-1 spec §4.3):
 * <ol>
 *   <li><b>Contagion</b> — the Infection meter and pyres are taught; Bloated Carriers burst into a real
 *       cleanse-or-punish cloud on death.</li>
 *   <li><b>The Bloom</b> — real Spore Nodes corrupt the ground outward; destroy them or the whole floor
 *       eventually turns hostile.</li>
 *   <li><b>Host</b> — he burrows into a real sculk growth. He never goes invulnerable: only the
 *       currently-cleansed land real hits while it stands, forcing a clean/infected role rotation — and
 *       its sensors punish sprinting and jumping with a shriek.</li>
 *   <li><b>Pandemic</b> — Infection rises everywhere regardless of position, and the pyres burn out one
 *       by one.</li>
 * </ol>
 * He is never invulnerable in any of the four bands; see {@code PlaguePhaseMechanic}'s header.
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
                // Contagion: the Infection meter and pyres are taught, and Bloated Carriers walk in.
                // He is hittable the entire time — the ask is spacing and managed add-killing.
                new BossPhase("Contagion", 1.0,
                        List.of(poisonCloud, summonUndead, plagueBolt, corruptionSpread),
                        false, PlagueWarden::onEnterPhase1,
                        instance -> PlaguePhases.contagion(instance, 0.70)),
                // The Bloom: real Spore Nodes corrupt the ground outward. Still fully hittable — the
                // group splits between boss pressure and clearing nodes before the floor turns hostile.
                new BossPhase("The Bloom", 0.70,
                        List.of(miasma, rottingGrasp, summonUndead, poisonCloud, blightSigils, virulentRot),
                        false, PlagueWarden::onEnterPhase2,
                        instance -> PlaguePhases.theBloom(instance, 0.45)),
                // Host: he burrows into a real sculk growth. Never invulnerable — only the cleansed
                // land real hits while it stands, and its sensors punish frantic play with a shriek.
                new BossPhase("Host", 0.45,
                        List.of(plagueSwarm, corruptionSpread, miasma, summonUndead, blightSigils, pandemicSurge, virulentRot),
                        false, PlagueWarden::onEnterPhase3,
                        instance -> PlaguePhases.host(instance, 0.20)),
                // Pandemic: Infection rises everywhere regardless of position, and the pyres burn out
                // one at a time — every wasted charge earlier in the fight is felt here.
                new BossPhase("Pandemic", 0.20,
                        List.of(poisonCloud, plagueBolt, corruptionSpread, rottingGrasp, plagueSwarm, pandemic,
                                blightSigils, pandemicSurge),
                        true, PlagueWarden::onEnterEnrage,
                        PlaguePhases::pandemic));

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

    /** Offset from his phase boundaries (0.70 / 0.45 / 0.20) so they land mid-phase, not on transitions. */
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
        // The Bloom: a swelling cloud of decay engulfs the warden as the ground starts to corrupt.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SPORE_BLOSSOM_AIR, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SICKLY, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.ITEM_SLIME, 7.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_SCULK_SPREAD, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_ZOMBIE_INFECT, 0.8f, 0.8f);
        instance.showTitle(
                Component.text("The Bloom", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Decay creeps across the battlefield", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Host: an inward-collapsing ring of spores as he burrows into the sculk.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.ITEM_SLIME, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), TOXIC, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, SICKLY, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.7f, 0.4f);
        instance.showTitle(
                Component.text("Host", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Only the cleansed can touch him now — stay quiet", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Pandemic: a towering spore helix and a spinning fermented-eye icon overhead.
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
                Component.text("☣ PANDEMIC ☣", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The pyres are burning out — spend them wisely", NamedTextColor.GRAY));
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
