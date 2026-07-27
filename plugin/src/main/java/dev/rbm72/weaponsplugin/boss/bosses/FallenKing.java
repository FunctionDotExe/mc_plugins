package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlackSwordRainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DarkExplosionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.EnrageSlashAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GroundFissureAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.JumpSlamAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SiegeHurlAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SpinningSlashAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SwordRainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TeleportStrikeAttack;
import dev.rbm72.weaponsplugin.boss.bosses.king.KingPhases;
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
 * The Fallen King — an undead sovereign holding court in a collapsing throne room.
 * <p>
 * He is the roster's <b>target-priority</b> boss, and the only one where <i>who is allowed to hurt him</i>
 * is itself a mechanic. He recognises one Challenger at a time and duels them with three readable melee
 * combos; everybody else fights his court, runs his crown across the floor, and rings the bell on his
 * dais. The mantle passes on events rather than on a timer — a riposte lands it on somebody new, and so
 * does a Wound — so the group is permanently re-forming around a moving focal point and no one player
 * tanks the fight.
 * <p>
 * <b>He is never invulnerable.</b> Not once, in any phase. What changes is the rule about how you are
 * fighting: an outsider's blow is cut to a tenth while the duel stands, only rear hits count once he
 * abandons it, and nothing at all is filtered in the last band. That distinction is deliberate — "the
 * boss is immune while you break an objective" was taken out of four bosses in this roster after five of
 * them independently arrived at it.
 * <p>
 * <b>Four phases, none of which ends on health.</b> Each pins its health seam until its objective has
 * actually been played (see {@code KingPhaseMechanic}):
 * <ol>
 *   <li><b>The Court</b> (100–70%) — exits on a full Challenger rotation through the group.</li>
 *   <li><b>Regicide</b> (70–40%) — exits on all three Crown Shards seated on the throne.</li>
 *   <li><b>The Broken Oath</b> (40–15%) — exits on the bell rung at least twice.</li>
 *   <li><b>Last Stand</b> (&lt;15%) — the throne room is a field of anvils and he starts executing.</li>
 * </ol>
 * Terrain accretes throughout: every Judgment leaves real anvils embedded in the floor, and by P4 the
 * wreckage the fight created is the cover that saves the marked player. All of it is in the arena ledger
 * and all of it is put back when the fight ends (§0.3) — which is exactly what licenses it.
 */
public final class FallenKing extends Boss {

    private static final Color KING_GOLD = Color.fromRGB(212, 175, 55);
    private static final Color KING_SHADOW = Color.fromRGB(60, 0, 90);

    /** Health-fraction seams, shared between the phase list and the mechanics that must not cross them early. */
    private static final double REGICIDE_ENTRY = 0.70;
    private static final double OATH_ENTRY = 0.40;
    private static final double LAST_STAND_ENTRY = 0.15;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public FallenKing(WeaponsPlugin plugin) {
        super(plugin);

        SiegeHurlAttack siegeHurl = new SiegeHurlAttack(plugin);
        GroundFissureAttack groundFissure = new GroundFissureAttack(plugin);
        SwordRainAttack swordRain = new SwordRainAttack(plugin);
        TeleportStrikeAttack teleportStrike = new TeleportStrikeAttack(plugin);
        BlackSwordRainAttack blackSwordRain = new BlackSwordRainAttack(plugin);
        DarkExplosionAttack darkExplosion = new DarkExplosionAttack(plugin);
        JumpSlamAttack jumpSlam = new JumpSlamAttack(plugin);
        SpinningSlashAttack spinningSlash = new SpinningSlashAttack(plugin);
        EnrageSlashAttack enrageSlash = new EnrageSlashAttack(plugin);

        // The attack pools are deliberately thin. His melee is the duel — three authored combos with
        // distinct poses, distinct floor shapes and distinct correct answers, driven by the phase
        // mechanic so the riposte window can exist at all. What the pool adds on top is reach: pressure
        // that lands on the players who are *not* duelling him, so no role in the fight is ever safe.
        this.phases = List.of(
                // The Court: the duel is taught and his knights walk in behind it. Ranged pressure only,
                // so the non-challengers clearing knights are still being fought.
                new BossPhase("The Court", 1.0,
                        List.of(siegeHurl, groundFissure),
                        false, FallenKing::onEnterCourt,
                        instance -> KingPhases.court(instance, REGICIDE_ENTRY)),
                // Regicide: he chases carriers, so a blink and a rain of blades are what he reaches them
                // with when the Challenger is doing their job and pulling him the other way.
                new BossPhase("Regicide", REGICIDE_ENTRY,
                        List.of(siegeHurl, swordRain, teleportStrike),
                        false, FallenKing::onEnterRegicide,
                        instance -> KingPhases.regicide(instance, OATH_ENTRY)),
                // The Broken Oath: the ceiling is already failing on its own (Judgment), so the pool
                // adds the things that punish standing still while you wait for the bell.
                new BossPhase("The Broken Oath", OATH_ENTRY,
                        List.of(blackSwordRain, darkExplosion, jumpSlam),
                        false, FallenKing::onEnterOath,
                        instance -> KingPhases.brokenOath(instance, LAST_STAND_ENTRY)),
                // Last Stand: everything, plus the Execution charge the mechanic itself drives.
                new BossPhase("Last Stand", LAST_STAND_ENTRY,
                        List.of(spinningSlash, blackSwordRain, darkExplosion, teleportStrike, enrageSlash, jumpSlam),
                        true, FallenKing::onEnterLastStand,
                        KingPhases::lastStand));

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
     * <b>No scripted events.</b> He used to carry five, three of which were shields and auctions that
     * stopped the fight and made him briefly untouchable — the exact archetype this rework exists to
     * remove. Everything they were reaching for is now permanent structure instead: the duel is the
     * target-priority interruption, the shard relay is the escort interruption, and the bell is the
     * timing window. Adding an absorbing wall on top would be the sixth version of a beat the fight
     * already plays three times, better.
     */
    @Override
    public List<dev.rbm72.weaponsplugin.boss.BossEvent> events() {
        return List.of();
    }

    /**
     * Four times the roster default, because every objective he has is a sequence of physical acts under
     * pressure rather than a burst window.
     * <p>
     * The framework's floor-lock valve releases a phase's seam once its objective has gone untouched for
     * this long, as a guard against an unreachable weak point. At the default 45 seconds it would fire on
     * every phase of this fight while the objectives were still standing: a full Challenger rotation, a
     * three-shard relay across the arena under pursuit, and two trips to a bell on the far side are all
     * minute-scale jobs. {@code KingPhaseMechanic#progressSignal} resets the clock on every rotation,
     * seated shard, cut chain and ring, so the valve can now only fire on a fight making no headway at
     * all.
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /**
     * The arena must stay buildable and breakable for him.
     * <p>
     * Two of his mechanics are block work the players do with their own hands: cutting a chained
     * teammate loose, and stacking a column of the arena's own cobble to break an Execution charge's line
     * when there is no anvil where you need one. The default WorldGuard build-lock exists to stop players
     * quarrying an escape route mid-fight, and here it would silently delete both.
     */
    @Override
    public boolean worldGuardProtectionEnabled() {
        return configBoolean("worldguard-protection", false);
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
        return Component.text("He will name one of you, and duel only them", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("👑 THE FALLEN KING HAS FALLEN 👑", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The throne is silent once more", NamedTextColor.GRAY);
    }

    private static void onEnterCourt(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            equipment.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            // The crown is a real worn item, because P2 physically takes it off him.
            equipment.setHelmetDropChance(0f);
            equipment.setItemInMainHandDropChance(0f);
        }
        Fx.expandingRings(instance.plugin(), loc, Particle.END_ROD, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), KING_GOLD, 1.6f, 40, 0.7);
        Fx.sound(loc, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 0.5f);
    }

    /** The crown physically leaves his head — the phase's own cinematic is the helmet coming off. */
    private static void onEnterRegicide(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setHelmet(null);
        }
        Fx.burst(loc.clone().add(0, 2.2, 0), Particle.END_ROD, 60, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 2.2, 0), KING_GOLD, 2.4f, 60, 1.0);
        Fx.expandingRings(instance.plugin(), loc, Particle.CRIT, 7.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_ANVIL_DESTROY, 1.2f, 1.3f);
        instance.showTitle(
                Component.text("REGICIDE", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("His crown is on the floor — get it to the throne", NamedTextColor.GRAY));
    }

    private static void onEnterOath(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            // He throws his own breastplate away: the exposed spine is a visible change, not a text line.
            equipment.setChestplate(null);
        }
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SQUID_INK, 40, 0.8);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), KING_SHADOW, 2.0f, 40, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.ring(loc, Particle.REVERSE_PORTAL, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ITEM_SHIELD_BREAK, 1.2f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_WITHER_HURT, 0.8f, 0.4f);
        instance.showTitle(
                Component.text("THE BROKEN OATH", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("No more duel — strike his back, ring the bell", NamedTextColor.GRAY));
    }

    private static void onEnterLastStand(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1, 0), Particle.FLAME, 55, 0.8);
        Fx.coloredRing(loc, KING_SHADOW, 1.6f, 4.5, 24, 0);
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
                Component.text("⚔ LAST STAND ⚔", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("He will run down whoever is closest to death", NamedTextColor.GRAY));
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
