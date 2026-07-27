package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.events.PullNukeEvent;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ArmyOfTheDeadAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BoneSpikesAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DeathBoltAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GraveGraspAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SoulDrainAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WitherCloudAttack;
import dev.rbm72.weaponsplugin.boss.bosses.necro.ArmyPhase;
import dev.rbm72.weaponsplugin.boss.bosses.necro.HordePhase;
import dev.rbm72.weaponsplugin.boss.bosses.necro.ReanimationPhase;
import dev.rbm72.weaponsplugin.boss.bosses.necro.ShroudPhase;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Necro Overlord — a necromancer holding an army together under an artificial night.
 * <p>
 * He is the roster's horde-control and denial boss, and he is built on the best rule vanilla Minecraft
 * offers: <b>undead burn in daylight</b>. He blots the sky out with a real block canopy; the group tears
 * the canopy's anchors down and lets the sun finish his army for them. No other boss here is solved by
 * changing the weather over your own head.
 * <p>
 * The loop is never "kill the adds". The horde regenerates from grave markers he plants and from corpse
 * piles — real bone blocks left where each undead fell, which get back up unless somebody mines them out.
 * So the group holds a chokepoint (the arena hands out the cobble and scaffolding to build one), breaks the
 * graves, mines the dead, and opens the sky. Killing faster makes the corpse problem worse, which is why a
 * five-player group buries its own arena and a solo player never does.
 * <p>
 * <b>Four phases, none of which ends on health.</b> Each one pins its health seam until its objective has
 * actually been played — see {@code NecroPhaseMechanic} for how the two framework levers combine:
 * <ol>
 *   <li><b>The Horde</b> (100–76%) — exits on two grave markers destroyed.</li>
 *   <li><b>The Shroud</b> (76–50%) — exits on the shroud broken at least once.</li>
 *   <li><b>Reanimation</b> (50–22%) — exits on the corpse floor cleared below a threshold.</li>
 *   <li><b>Army of the Dead</b> (&lt;22%) — he steps out of the back line and the sun stays up for good.</li>
 * </ol>
 * <b>What gates him is where he stands, not a shield.</b> He is hittable for every second of this fight:
 * no phase sets a damage multiplier, no phase filters a hit, and nothing here is ever invulnerable. Until
 * P4 he simply keeps stepping back to the far side of the arena whenever the group closes, so reaching him
 * costs ground rather than permission. That distinction is deliberate — "the boss is immune while you break
 * an objective" was removed from four bosses in this roster after five of them independently arrived at it.
 */
public final class NecroOverlord extends Boss {

    private static final Color BONE = Color.fromRGB(235, 235, 210);
    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    /** Health-fraction seams, shared between the phase list and the mechanics that must not cross them early. */
    private static final double SHROUD_ENTRY = 0.76;
    private static final double REANIMATION_ENTRY = 0.50;
    private static final double ARMY_ENTRY = 0.22;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public NecroOverlord(WeaponsPlugin plugin) {
        super(plugin);

        BoneSpikesAttack boneSpikes = new BoneSpikesAttack(plugin);
        DeathBoltAttack deathBolt = new DeathBoltAttack(plugin);
        GraveGraspAttack graveGrasp = new GraveGraspAttack(plugin);
        SoulDrainAttack soulDrain = new SoulDrainAttack(plugin);
        WitherCloudAttack witherCloud = new WitherCloudAttack(plugin);
        ArmyOfTheDeadAttack armyOfTheDead = new ArmyOfTheDeadAttack(plugin);

        this.phases = List.of(
                // The Horde: waves walk in from lit lanes while he plants graves behind them. Bone Spikes
                // and Death Bolt are what a boss standing at the back of his own army can still reach you
                // with; Grave Grasp is the rent on standing still inside the chokepoint you just built.
                new BossPhase("The Horde", 1.0,
                        List.of(boneSpikes, deathBolt, graveGrasp),
                        false, NecroOverlord::onEnterHorde,
                        instance -> new HordePhase(instance, SHROUD_ENTRY)),
                // The Shroud: the canopy goes up and the fight splits between the ground and the anchors.
                // Soul Drain joins here because the horde is now dense enough for him to feed off it.
                new BossPhase("The Shroud", SHROUD_ENTRY,
                        List.of(deathBolt, graveGrasp, witherCloud, soulDrain),
                        false, NecroOverlord::onEnterShroud,
                        instance -> new ShroudPhase(instance, REANIMATION_ENTRY)),
                // Reanimation: the arena clogs with the group's own kills. Wither Cloud matters most here —
                // it takes away floor at exactly the point the group needs somewhere to stand and mine.
                new BossPhase("Reanimation", REANIMATION_ENTRY,
                        List.of(boneSpikes, deathBolt, graveGrasp, witherCloud, soulDrain),
                        false, NecroOverlord::onEnterReanimation,
                        instance -> new ReanimationPhase(instance, ARMY_ENTRY)),
                // Army of the Dead: he commits everything and finally comes forward himself.
                new BossPhase("Army of the Dead", ARMY_ENTRY,
                        List.of(boneSpikes, deathBolt, graveGrasp, witherCloud, soulDrain, armyOfTheDead),
                        true, NecroOverlord::onEnterArmy,
                        ArmyPhase::new));

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

    /**
     * A wither skeleton, and that is load-bearing rather than flavour: vanilla exempts wither skeletons from
     * sunlight while burning every rank in his army under it. When the shroud comes down, the sun takes his
     * legion apart and leaves him standing in it.
     */
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
     * Four times the roster default, because every objective he has is block work rather than a burst
     * window.
     * <p>
     * The framework's floor-lock valve releases a phase's health seam once its objective has gone
     * untouched for this long, as a guard against an unreachable weak point. At the default 45 seconds
     * this boss did not gate anything at all: breaking two grave markers while a horde walks in, climbing
     * to four anchors, and mining a corpse floor out are all minute-scale jobs, so all four phases handed
     * themselves over on the timer with their objectives still standing — and swinging at him was strictly
     * faster than playing the fight. The valve now waits three minutes, and
     * {@code NecroPhaseMechanic#progressSignal} resets that clock on every grave, anchor and pile, so it
     * can only ever fire on a fight making no headway whatsoever.
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /**
     * The one boss in the roster whose arena must stay buildable and breakable.
     * <p>
     * Every objective he has is block work the players do with their own hands: mining corpse piles out
     * before they rise, walling a lane off to funnel the horde, towering up to the shroud anchors. The
     * default WorldGuard build-lock exists to stop players quarrying an escape route mid-fight, and it
     * would silently delete three of this boss's four phases — the mechanics would still run and the group
     * would simply be unable to interact with any of them.
     */
    @Override
    public boolean worldGuardProtectionEnabled() {
        return configBoolean("worldguard-protection", false);
    }

    /**
     * Just the pull — the dead hauling the group off its chokepoint and into the open, which is the exact
     * failure state this boss punishes. Deliberately no hit-count shield: an absorbing wall that ignores
     * damage for a window is the archetype this roster spent a design pass removing, and it has nothing to
     * do with a boss whose pressure is positional.
     */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new PullNukeEvent(plugin, id(), new double[] {0.62, 0.34},
                        "GRASP OF THE GRAVE", "The dead are pulling you off your line — break away"));
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
        return Component.text("He holds the night, and the night holds his army", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("☠ THE NECRO OVERLORD IS UNMADE ☠", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The sky is yours again", NamedTextColor.GRAY);
    }

    private static void onEnterHorde(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        }
        Fx.expandingRings(instance.plugin(), loc, Particle.SCULK_SOUL, 5.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), NECROTIC, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SOUL, 25, 0.6);
        Fx.sound(loc, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.5f);
    }

    private static void onEnterShroud(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Reads upward on purpose: the phase's new thing is happening over the players' heads, and the
        // canopy itself takes a few seconds to spread. The title confirms what the sky is already doing.
        Fx.helixFrame(loc, Particle.SCULK_SOUL, 1.6, 6, 0, 4.0);
        Fx.coloredBurst(loc.clone().add(0, 2.0, 0), BONE, 1.6f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL, 7.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.4f);
        Fx.sound(loc, Sound.BLOCK_SCULK_SPREAD, 1.2f, 0.5f);
    }

    private static void onEnterReanimation(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.SOUL_FIRE_FLAME, 40, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), NECROTIC, 2.0f, 34, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, BONE, 1.4f, 6.0 - ring * 1.5, 20, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);
        Fx.sound(loc, Sound.BLOCK_BONE_BLOCK_BREAK, 1.0f, 0.4f);
    }

    private static void onEnterArmy(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SCULK_SOUL, 55, 0.8);
        Fx.coloredRing(loc, NECROTIC, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.WITHER_SKELETON_SKULL, 1.4f, 100, 12.0);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.7f);
        instance.showTitle(
                Component.text("☠ ARMY OF THE DEAD ☠", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("He steps forward himself — the sun has the rest", NamedTextColor.GRAY));
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
