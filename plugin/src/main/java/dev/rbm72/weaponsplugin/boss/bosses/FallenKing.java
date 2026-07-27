package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BindingSigilsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.HealingAddsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlackSwordRainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DarkExplosionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DashSlashAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.EnrageSlashAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.EnvironmentalHazardAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.RoyalGuardStandAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GroundFissureAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.HeavySwingAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.JumpSlamAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ShockwaveSlamAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SiegeHurlAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SpinningSlashAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SummonRoyalGuardsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SwordRainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TeleportStrikeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TripleComboAttack;
import dev.rbm72.weaponsplugin.boss.events.BeaconEvent;
import dev.rbm72.weaponsplugin.boss.events.CourtJudgmentEvent;
import dev.rbm72.weaponsplugin.boss.events.CursedCoinTossEvent;
import dev.rbm72.weaponsplugin.boss.events.WardplateAuctionEvent;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.mechanics.DuelLockMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.EmpoweringAddsMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.RegicideMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.WrathMeterMechanic;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.KingsJudgment;
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
 * Reference boss for the boss framework: a cursed greatsword king with four
 * phases (100-75 / 75-40 / 40-15 / enrage &lt;15), reconciling the "final
 * phase at 10%" language in the source spec with the framework's general
 * "enrage under 15%" rule.
 */
public final class FallenKing extends Boss {

    /**
     * The king's wardplates were reforming faster than the fight could breathe — the exposed window
     * would close and the next set was already standing, so the encounter read as one unbroken
     * "break plates" loop with no stretch of open melee in between. Well above the framework default
     * on purpose; this is the boss the pacing complaint was actually about.
     */
    private static final double WARDPLATE_REFORM_SCALE = 1.6;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public FallenKing(WeaponsPlugin plugin) {
        super(plugin);

        HeavySwingAttack heavySwing = new HeavySwingAttack(plugin);
        TripleComboAttack tripleCombo = new TripleComboAttack(plugin);
        DashSlashAttack dashSlash = new DashSlashAttack(plugin);
        ShockwaveSlamAttack shockwaveSlam = new ShockwaveSlamAttack(plugin);
        SummonRoyalGuardsAttack summonRoyalGuards = new SummonRoyalGuardsAttack(plugin);
        SiegeHurlAttack siegeHurl = new SiegeHurlAttack(plugin);
        EnvironmentalHazardAttack environmentalHazard = new EnvironmentalHazardAttack(plugin, "fallen_king");

        SpinningSlashAttack spinningSlash = new SpinningSlashAttack(plugin);
        SwordRainAttack swordRain = new SwordRainAttack(plugin);
        GroundFissureAttack groundFissure = new GroundFissureAttack(plugin);
        JumpSlamAttack jumpSlam = new JumpSlamAttack(plugin);

        BindingSigilsAttack bindingSigils = new BindingSigilsAttack(plugin);
        BlackSwordRainAttack blackSwordRain = new BlackSwordRainAttack(plugin);
        DarkExplosionAttack darkExplosion = new DarkExplosionAttack(plugin);
        TeleportStrikeAttack teleportStrike = new TeleportStrikeAttack(plugin);

        EnrageSlashAttack enrageSlash = new EnrageSlashAttack(plugin);
        RoyalGuardStandAttack royalGuard = new RoyalGuardStandAttack(plugin);
        HealingAddsAttack royalClerics = new HealingAddsAttack(plugin, "fallen_king", "Royal Cleric",
                EntityType.EVOKER, KING_GOLD);

        this.phases = List.of(
                // He is a king, so he duels. Only the opponent he names can touch him; everyone else
                // handles the adds and stays alive until the mantle passes to them.
                new BossPhase("The Duel", 1.0,
                        List.of(heavySwing, tripleCombo, dashSlash, shockwaveSlam, summonRoyalGuards, siegeHurl,
                                environmentalHazard),
                        false, FallenKing::onEnterPhase1,
                        instance -> new DuelLockMechanic(instance, KING_GOLD,
                                configDouble("duel-rotate-below-health", 0.4),
                                configInt("duel-max-ticks", 600),
                                configDouble("duel-outsider-damage-fraction", 0.0))),
                // Broken Oath: he is hittable throughout, but his clerics mend him faster than a
                // careless group can cut. Kill them or out-race them — both are real answers, and the
                // choice of where to spend damage is the phase.
                new BossPhase("Broken Oath", 0.75,
                        List.of(spinningSlash, swordRain, groundFissure, jumpSlam, royalClerics),
                        false, FallenKing::onEnterPhase2,
                        instance -> new EmpoweringAddsMechanic(instance,
                                Component.text("Royal Cleric", NamedTextColor.GRAY), KING_GOLD,
                                EntityType.EVOKER,
                                configInt("cleric-count", 3),
                                configDouble("cleric-health", 24.0),
                                configDouble("cleric-heal-per-second", 4.0),
                                configDouble("cleric-damage-reduction-each", 0.25),
                                configInt("cleric-respawn-delay-ticks", 300))),
                // The Crown's Weight: still no lockout, but damage feeds his fury and filling it wipes
                // the arena. The only phase in the game where the right move is sometimes to stop
                // swinging — a group running on "burst the window" reflexes will detonate it twice
                // before working that out.
                new BossPhase("The Crown's Weight", 0.40,
                        List.of(bindingSigils, blackSwordRain, darkExplosion, teleportStrike, royalGuard),
                        false, FallenKing::onEnterPhase3,
                        instance -> new WrathMeterMechanic(instance, KING_SHADOW,
                                configDouble("wrath-cap", 220.0),
                                configDouble("wrath-decay-per-second", 12.0),
                                configDouble("wrath-nova-damage", 16.0),
                                configDouble("wrath-nova-radius", 14.0),
                                configInt("wrath-calm-ticks", 120))),
                // Last Stand: health stops being the win condition. He cannot die on the dais, so the
                // finish is about driving him off it and holding him there, not about more damage.
                // Summon attacks stay excluded — this is the king himself, at close range.
                new BossPhase("Last Stand", 0.15,
                        List.of(heavySwing, tripleCombo, dashSlash, shockwaveSlam, spinningSlash, swordRain,
                                groundFissure, jumpSlam, blackSwordRain, darkExplosion, teleportStrike, enrageSlash,
                                siegeHurl, royalGuard, environmentalHazard),
                        true, FallenKing::onEnterEnrage,
                        instance -> new RegicideMechanic(instance, KING_SHADOW,
                                configDouble("throne-radius", 7.0),
                                configDouble("throne-reclaim-pull", 0.06))));

        this.lootTable = new LootTable()
                .guaranteed(() -> new KingsJudgment(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> royalArmorPiece(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> royalArmorPiece(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), FallenKing::craftingMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), FallenKing::fallenCrown);
    }

    @Override
    public String id() {
        return "fallen_king";
    }

    @Override
    public Component displayName() {
        return Component.text("The Fallen King", NamedTextColor.DARK_RED)
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
     * Two scripted interruptions layered over the phase structure, deliberately offset from the phase
     * boundaries (0.75 / 0.40 / 0.15) so they land mid-phase and break up a settled rhythm rather than
     * piling onto a transition that is already busy with a title and a cinematic.
     */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.88, 0.28}),
                new CourtJudgmentEvent(plugin, id(), new double[] {0.66, 0.34}),
                new WardplateAuctionEvent(plugin, id(), new double[] {0.80, 0.50}),
                new CursedCoinTossEvent(plugin, id(), new double[] {0.58, 0.22}),
                new BeaconEvent(plugin, id(), new double[] {0.42}));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.SOUL, "boss.fallen_king.ambient", Sound.BLOCK_SOUL_SAND_STEP,
                true, Biome.SOUL_SAND_VALLEY);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("☠ THE FALLEN KING ☠", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A cursed ruler rises to reclaim his throne", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("👑 THE FALLEN KING HAS FALLEN 👑", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The throne is silent once more", NamedTextColor.GRAY);
    }

    private static final Color KING_GOLD = Color.fromRGB(212, 175, 55);
    private static final Color KING_SHADOW = Color.fromRGB(60, 0, 90);

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        }
        // Rising column of light + a spinning crown-like ring of end rods as the king takes his stance.
        Fx.expandingRings(instance.plugin(), loc, Particle.END_ROD, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), KING_GOLD, 1.6f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.END_ROD, 25, 0.6);
        Fx.sound(loc, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setChestplate(null);
            equipment.setLeggings(null);
        }
        // Armor visibly shatters: a burst of "shrapnel" particles plus a shockwave ring at ground level.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.ITEM_SNOWBALL, 35, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), Color.fromRGB(150, 150, 160), 1.3f, 30, 0.6);
        Fx.expandingRings(instance.plugin(), loc, Particle.CRIT, 6.0, 3, 2L);
        Fx.sound(loc, Sound.ITEM_SHIELD_BREAK, 1.0f, 0.7f);
        Fx.sound(loc, Sound.BLOCK_ANVIL_BREAK, 0.8f, 0.9f);
        instance.showTitle(
                Component.text("Armor Shattered", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("The king casts aside his broken plate", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Dark magic awakens: an inward-swirling void ring (reverse portal) collapsing onto the king.
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SQUID_INK, 35, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), KING_SHADOW, 1.8f, 30, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.ring(loc, Particle.REVERSE_PORTAL, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_ILLUSIONER_AMBIENT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_WITHER_HURT, 0.6f, 0.4f);
        instance.showTitle(
                Component.text("Dark Awakening", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Something ancient stirs within the king", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The king's final stand: fire helix rising around him, an ominous ring, a floating blade overhead.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.FLAME, 45, 0.7);
        Fx.ring(loc, Particle.RAID_OMEN, 4.5, 24);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.NETHERITE_SWORD, 1.4f, 100, 12.0);
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
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 1.0f);
        Fx.sound(loc, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.6f);
        instance.showTitle(
                Component.text("⚔ ENRAGED ⚔", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("The Fallen King will not fall quietly", NamedTextColor.GRAY));
    }

    private static ItemStack royalArmorPiece(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Royal Regalia", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack craftingMaterials() {
        return new ItemStack(Material.NETHERITE_SCRAP, ThreadLocalRandom.current().nextInt(1, 4));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack fallenCrown() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Fallen Crown", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("A trophy from the king who would not stay dead.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
