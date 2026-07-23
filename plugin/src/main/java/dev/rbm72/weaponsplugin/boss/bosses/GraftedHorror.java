package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.BlazeGraftAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.EndermanGraftAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FrenziedGraftAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.RendingClawAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SpiderGraftAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WitherGraftAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Spinelash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Grafted Horror — a failed alchemical experiment stitched together from the remains of every
 * beast that ever fell in its arena. Four phases, each waking up one more graft it never had before
 * (spider, blaze marrow, wither bone, warp eyes) while keeping every earlier one; enrage tears the
 * seams wide open and every donor crawls out to fight one last time at its side.
 */
public final class GraftedHorror extends Boss {

    private static final Color RUST = Color.fromRGB(140, 60, 30);
    private static final Color SICKLY_GREEN = Color.fromRGB(90, 130, 60);
    private static final Color EMBER = Color.fromRGB(255, 120, 0);
    private static final Color DECAY_GREY = Color.fromRGB(60, 60, 60);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public GraftedHorror(WeaponsPlugin plugin) {
        super(plugin);

        RendingClawAttack rendingClaw = new RendingClawAttack(plugin);
        SpiderGraftAttack spiderGraft = new SpiderGraftAttack(plugin);
        BlazeGraftAttack blazeGraft = new BlazeGraftAttack(plugin);
        WitherGraftAttack witherGraft = new WitherGraftAttack(plugin);
        EndermanGraftAttack endermanGraft = new EndermanGraftAttack(plugin);
        FrenziedGraftAttack frenziedGraft = new FrenziedGraftAttack(plugin);

        this.phases = List.of(
                new BossPhase("The First Graft", 1.0,
                        List.of(rendingClaw, spiderGraft),
                        false, GraftedHorror::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Grafted Plates", NamedTextColor.GOLD),
                                Material.BONE, RUST, 0, false)),
                new BossPhase("Marrow Ignition", 0.75,
                        List.of(rendingClaw, spiderGraft, blazeGraft),
                        false, GraftedHorror::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Fused Carapace", NamedTextColor.GOLD),
                                Material.BLAZE_ROD, EMBER, 1, false)),
                new BossPhase("Wither Sinew", 0.40,
                        List.of(rendingClaw, spiderGraft, blazeGraft, witherGraft, endermanGraft),
                        false, GraftedHorror::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Sinew Knots", NamedTextColor.DARK_GRAY),
                                Material.WITHER_SKELETON_SKULL, DECAY_GREY, 2, false)),
                new BossPhase("Full Metamorphosis", 0.15,
                        List.of(rendingClaw, spiderGraft, blazeGraft, witherGraft, endermanGraft, frenziedGraft),
                        true, GraftedHorror::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Core of the Menagerie", NamedTextColor.RED),
                                Material.NETHER_STAR, Color.fromRGB(180, 20, 20), 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Spinelash(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> graftedHide(Material.DIAMOND_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> graftedHide(Material.DIAMOND_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), GraftedHorror::donorRemains)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), GraftedHorror::trophyOfTheMenagerie);
    }

    @Override
    public String id() {
        return "grafted_horror";
    }

    @Override
    public Component displayName() {
        return Component.text("The Grafted Horror", NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.ZOMBIE;
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
        return BossAmbiance.of(Particle.ITEM_COBWEB, "boss.grafted_horror.ambient", Sound.ENTITY_ZOMBIE_AMBIENT,
                false, null);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("⚚ THE GRAFTED HORROR ⚚", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("Stitched from every beast that fell before it", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("⚚ THE GRAFTED HORROR UNRAVELS ⚚", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("Every graft falls still at once", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            equipment.setItemInOffHand(new ItemStack(Material.COBWEB));
        }
        // The first graft twitches to life — a spider's limbs bolted onto rotting flesh.
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SICKLY_GREEN, 1.6f, 34, 0.6);
        Fx.point(loc.clone().add(0, 1, 0), Particle.ITEM_COBWEB, 12);
        Fx.sound(loc, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_SPIDER_AMBIENT, 0.9f, 0.8f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Marrow ignition: fire licks up through the seams where the blaze graft took hold.
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), EMBER, 1.8f, 40, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.FLAME, 30, 0.6);
        Fx.expandingRings(instance.plugin(), loc, Particle.FLAME, 6.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_BLAZE_AMBIENT, 1.1f, 0.7f);
        instance.showTitle(
                Component.text("Marrow Ignition", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Fire wakes in its stolen bones", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        EntityEquipment equipment = instance.entity().getEquipment();
        if (equipment != null) {
            ItemStack skull = new ItemStack(Material.WITHER_SKELETON_SKULL);
            if (skull.getItemMeta() instanceof SkullMeta meta) {
                meta.displayName(Component.text("Grafted Skull", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
                skull.setItemMeta(meta);
            }
            equipment.setHelmet(skull);
        }
        // Wither sinew: a donated skull settles onto its shoulders, brittle and rattling.
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DECAY_GREY, 2.0f, 34, 0.8);
        Fx.point(loc.clone().add(0, 1.4, 0), Particle.SMOKE, 20);
        Fx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.6f);
        instance.showTitle(
                Component.text("Wither Sinew", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true),
                Component.text("A donor's skull settles onto its shoulders", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // Full metamorphosis: every seam splits at once as the last graft — a warp-eyed thing — takes hold.
        Fx.burst(loc.clone().add(0, 1, 0), Particle.PORTAL, 50, 0.8);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 1.8f, 4.5, 26, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL_FIRE_FLAME, 7.0, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1.2f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        instance.showTitle(
                Component.text("⚚ FULL METAMORPHOSIS ⚚", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every graft it ever stole answers at once", NamedTextColor.GRAY));
    }

    private static ItemStack graftedHide(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Grafted Hide", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack donorRemains() {
        Material[] remains = {Material.ROTTEN_FLESH, Material.SPIDER_EYE, Material.BLAZE_POWDER, Material.BONE};
        Material picked = remains[ThreadLocalRandom.current().nextInt(remains.length)];
        return new ItemStack(picked, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack trophyOfTheMenagerie() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Trophy of the Menagerie", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Every beast it ever wore, mounted at last.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
