package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.VulnerabilitySpec;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.ChoirWailAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FangLineAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GrandIllusionAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SpectralHoundsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.VexSwarmAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Mournsong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Hollow Choir — an evoker who sang so many souls into service that it forgot which voice was
 * its own. Every phase it channels a different memory of something that used to hunt: fanged
 * illusions, a flickering wraith swarm, a pack of spectral hounds, then all of them at once.
 */
public final class HollowChoir extends Boss {

    private static final Color PALE_VIOLET = Color.fromRGB(160, 130, 220);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public HollowChoir(WeaponsPlugin plugin) {
        super(plugin);

        FangLineAttack fangLine = new FangLineAttack(plugin);
        VexSwarmAttack vexSwarm = new VexSwarmAttack(plugin);
        SpectralHoundsAttack spectralHounds = new SpectralHoundsAttack(plugin);
        ChoirWailAttack choirWail = new ChoirWailAttack(plugin);
        GrandIllusionAttack grandIllusion = new GrandIllusionAttack(plugin);

        this.phases = List.of(
                new BossPhase("The First Voice", 1.0,
                        List.of(fangLine, vexSwarm),
                        false, HollowChoir::onEnterPhase1,
                        VulnerabilitySpec.scaled(Component.text("Warded Robes", NamedTextColor.LIGHT_PURPLE),
                                Material.AMETHYST_SHARD, PALE_VIOLET, 0, false)),
                new BossPhase("The Hunting Memory", 0.75,
                        List.of(fangLine, vexSwarm, spectralHounds),
                        false, HollowChoir::onEnterPhase2,
                        VulnerabilitySpec.scaled(Component.text("Woven Sigils", NamedTextColor.LIGHT_PURPLE),
                                Material.AMETHYST_CLUSTER, PALE_VIOLET, 1, false)),
                new BossPhase("The Dissonant Choir", 0.40,
                        List.of(fangLine, vexSwarm, spectralHounds, choirWail),
                        false, HollowChoir::onEnterPhase3,
                        VulnerabilitySpec.scaled(Component.text("Frayed Chorus", NamedTextColor.LIGHT_PURPLE),
                                Material.SCULK_SHRIEKER, PALE_VIOLET, 2, false)),
                new BossPhase("Full Requiem", 0.15,
                        List.of(fangLine, vexSwarm, spectralHounds, choirWail, grandIllusion),
                        true, HollowChoir::onEnterEnrage,
                        VulnerabilitySpec.scaled(Component.text("Heart of the Choir", NamedTextColor.RED),
                                Material.NETHER_STAR, Color.fromRGB(180, 20, 20), 3, true)));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Mournsong(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> choirRobes(Material.DIAMOND_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> choirRobes(Material.DIAMOND_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), HollowChoir::choirMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), HollowChoir::maskOfTheChoir);
    }

    @Override
    public String id() {
        return "hollow_choir";
    }

    @Override
    public Component displayName() {
        return Component.text("The Hollow Choir", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.EVOKER;
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
        return BossAmbiance.of(Particle.SOUL, "boss.hollow_choir.ambient", Sound.ENTITY_VEX_AMBIENT,
                false, null);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("♫ THE HOLLOW CHOIR ♫", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("So many voices, it forgot which was its own", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("♫ THE CHOIR FALLS SILENT ♫", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("Every voice it carried finally rests", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), PALE_VIOLET, 1.6f, 34, 0.6);
        Fx.point(loc.clone().add(0, 1, 0), Particle.WITCH, 14);
        Fx.sound(loc, Sound.ENTITY_EVOKER_AMBIENT, 1.0f, 0.7f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), PALE_VIOLET, 1.8f, 38, 0.7);
        Fx.expandingRings(instance.plugin(), loc, Particle.SOUL, 6.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.1f, 0.8f);
        instance.showTitle(
                Component.text("The Hunting Memory", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Something that used to hunt stirs in its voice", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), PALE_VIOLET, 2.0f, 42, 0.8);
        Fx.point(loc.clone().add(0, 1.6, 0), Particle.SOUL, 24);
        Fx.sound(loc, Sound.ENTITY_ILLUSIONER_AMBIENT, 1.0f, 0.7f);
        instance.showTitle(
                Component.text("The Dissonant Choir", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Every stolen voice sings out of tune together", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SOUL, 60, 0.9);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 2.0f, 5.0, 30, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.WITCH, 7.5, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.4f, 0.5f);
        instance.showTitle(
                Component.text("♫ FULL REQUIEM ♫", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every voice it ever stole sings at once", NamedTextColor.GRAY));
    }

    private static ItemStack choirRobes(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Choir Robes", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack choirMaterials() {
        Material[] picks = {Material.AMETHYST_SHARD, Material.PHANTOM_MEMBRANE, Material.ECHO_SHARD};
        Material picked = picks[ThreadLocalRandom.current().nextInt(picks.length)];
        return new ItemStack(picked, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack maskOfTheChoir() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Mask of the Choir", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Every voice it stole, quiet at last.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
