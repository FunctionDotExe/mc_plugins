package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.DecayNovaAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.HealingAddsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SkullVolleyAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TripleGazeAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Soulcrown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wither;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Threefold Bane — a wither given three ages at once. It spawns already deadly and only gets
 * bigger the longer the fight drags: each phase it swells further (bone plates cracking to make
 * room), never smaller, until Full Coronation makes it a screen-filling giant for its last stand.
 */
public final class ThreefoldBane extends Boss {

    private static final Color SOUL_BLUE = Color.fromRGB(30, 200, 210);
    private static final Color DECAY_GREY = Color.fromRGB(50, 50, 55);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public ThreefoldBane(WeaponsPlugin plugin) {
        super(plugin);

        SkullVolleyAttack skullVolley = new SkullVolleyAttack(plugin);
        DecayNovaAttack decayNova = new DecayNovaAttack(plugin);
        TripleGazeAttack tripleGaze = new TripleGazeAttack(plugin);
        HealingAddsAttack boneChoir = new HealingAddsAttack(plugin, "threefold_bane", "Bone Choir",
                EntityType.WITHER_SKELETON, DECAY_GREY);

        this.phases = List.of(
                new BossPhase("Awakening", 1.0,
                        List.of(skullVolley, decayNova),
                        false, ThreefoldBane::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Bone Plating", NamedTextColor.AQUA),
                                Material.BONE_BLOCK, SOUL_BLUE, 0, false)),
                new BossPhase("First Swelling", 0.75,
                        List.of(skullVolley, decayNova, tripleGaze),
                        false, ThreefoldBane::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Cracked Ribcage", NamedTextColor.AQUA),
                                Material.WITHER_SKELETON_SKULL, SOUL_BLUE, 1, false)),
                new BossPhase("Second Swelling", 0.40,
                        List.of(skullVolley, decayNova, tripleGaze, boneChoir),
                        false, ThreefoldBane::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Broken Choir"), Material.SOUL_LANTERN,
                                DECAY_GREY, 2, false)),
                new BossPhase("Full Coronation", 0.15,
                        List.of(skullVolley, decayNova, tripleGaze, boneChoir),
                        true, ThreefoldBane::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Crown of Bane", NamedTextColor.RED),
                                Material.NETHER_STAR, Color.fromRGB(180, 20, 20), 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Soulcrown(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> banehideArmor(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> banehideArmor(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), ThreefoldBane::boneMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), ThreefoldBane::crownOfTheBane);
    }

    @Override
    public String id() {
        return "threefold_bane";
    }

    @Override
    public Component displayName() {
        return Component.text("The Threefold Bane", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.WITHER;
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
        return BossAmbiance.of(Particle.SOUL, "boss.threefold_bane.ambient", Sound.ENTITY_WITHER_AMBIENT,
                false, null);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("☠ THE THREEFOLD BANE ☠", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("Three ages of ruin, awake in one body", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("☠ THE THREEFOLD BANE COLLAPSES ☠", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("All three ages fall still together", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        // Real Withers spawn with a vanilla invulnerability window meant for the totem summon
        // ritual — skip it here so the fight starts the instant the title finishes.
        if (instance.entity() instanceof Wither wither) {
            wither.setInvulnerableTicks(0);
        }
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), SOUL_BLUE, 1.8f, 40, 0.7);
        Fx.point(loc.clone().add(0, 1, 0), Particle.SOUL, 20);
        Fx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.7f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.2, 1.05);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SOUL_BLUE, 2.0f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL, 6.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_WITHER_HURT, 1.1f, 0.6f);
        instance.showTitle(
                Component.text("First Swelling", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("Its ribcage cracks wider to make room", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.2, 1.1);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), DECAY_GREY, 2.2f, 40, 0.9);
        Fx.point(loc.clone().add(0, 1.4, 0), Particle.SOUL, 24);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 0.9f, 0.6f);
        instance.showTitle(
                Component.text("Second Swelling", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("A choir of the dead rises to bolster it", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(1.15, 1.0);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SOUL, 60, 0.9);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 2.0f, 5.0, 30, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL_FIRE_FLAME, 7.5, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.7f);
        instance.showTitle(
                Component.text("☠ FULL CORONATION ☠", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every age it has lived crowns it at once", NamedTextColor.GRAY));
    }

    private static ItemStack banehideArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Banehide", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack boneMaterials() {
        Material[] picks = {Material.BONE, Material.BONE_BLOCK, Material.SOUL_SOIL, Material.COAL_BLOCK};
        Material picked = picks[ThreadLocalRandom.current().nextInt(picks.length)];
        return new ItemStack(picked, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfTheBane() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Crown of the Bane", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Three ages of ruin, worn as one.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
