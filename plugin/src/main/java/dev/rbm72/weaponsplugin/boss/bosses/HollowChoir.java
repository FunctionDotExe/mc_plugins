package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.events.BeaconEvent;
import dev.rbm72.weaponsplugin.boss.events.ConvergenceNukeEvent;
import dev.rbm72.weaponsplugin.boss.bosses.choir.ChoirPhases;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SpectralHoundsAttack;
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
 * The Hollow Choir — a machine of note blocks, bells and sculk sensors still playing a song nobody
 * remembers writing. It does not see you. It <b>hears</b> you, and you can lie to it: the roster's
 * <b>active sound misdirection</b> boss (batch-3 spec §4). Make noise where you are not, then attack from
 * the quiet side — and because fighting is loud, damage and safety are directly opposed at all times.
 * <ol>
 *   <li><b>The Listening</b> — strike a note block, watch the attack land there instead of on you.</li>
 *   <li><b>The Dark</b> — Darkness in waves; audio is the only channel left.</li>
 *   <li><b>The Round</b> — it sings three notes and the group plays them back, in the dark, while hunted
 *       by copies of it that make no sound.</li>
 *   <li><b>All Voices</b> — everything is loud, misdirection dies, and it hunts the nearest body.</li>
 * </ol>
 * Where the Plague Warden's sculk demands silence, this boss demands the opposite and <em>never</em>
 * punishes sprinting — batch 3 §0's deconfliction rule, enforced in {@code choir.Noise}.
 */
public final class HollowChoir extends Boss {

    private static final Color PALE_VIOLET = Color.fromRGB(160, 130, 220);

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public HollowChoir(WeaponsPlugin plugin) {
        super(plugin);

        // Its wail, its fangs, its vexes and its illusions are all fired by the noise model now — they
        // have to resolve at a heard <em>location</em> rather than at a target, which is exactly what a
        // BossAttack cannot express. What is left in the pool is the one thing that genuinely hunts.
        SpectralHoundsAttack spectralHounds = new SpectralHoundsAttack(plugin);

        this.phases = List.of(
                // The Listening: sensors, note blocks, and the rule that noise draws attacks.
                new BossPhase("The Listening", 1.0,
                        List.of(spectralHounds),
                        false, HollowChoir::onEnterPhase1,
                        instance -> ChoirPhases.listening(instance, 0.75)),
                // The Dark: vision goes, and the group needs an audio protocol.
                new BossPhase("The Dark", 0.75,
                        List.of(spectralHounds),
                        false, HollowChoir::onEnterPhase2,
                        instance -> ChoirPhases.theDark(instance, 0.50)),
                // The Round: call-and-response, in the dark, among copies that make no sound.
                new BossPhase("The Round", 0.50,
                        List.of(spectralHounds),
                        false, HollowChoir::onEnterPhase3,
                        instance -> ChoirPhases.theRound(instance, 0.24)),
                // All Voices: the tools are removed and it comes at whoever is closest.
                new BossPhase("All Voices", 0.24,
                        List.of(spectralHounds),
                        true, HollowChoir::onEnterEnrage,
                        ChoirPhases::allVoices));

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

    /**
     * Four times the roster default. Misdirection, a Darkness cycle and a call-and-response phrase are
     * all things a group can be doing well for a long stretch without the boss's health moving, and
     * {@code ChoirPhaseMechanic#progressSignal} counts every misdirection and every phrase so that
     * patience never reads as a stalled fight.
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /** Offset from its phase boundaries (0.75 / 0.50 / 0.24) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new BeaconEvent(plugin, id(), new double[] {0.86, 0.36}),
                new ConvergenceNukeEvent(plugin, id(), new double[] {0.62, 0.30},
                        "FULL CHORUS", "Every voice at once — get out of its reach"));
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
                Component.text("The Dark", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("Your eyes are no use here — keep making noise", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), PALE_VIOLET, 2.0f, 42, 0.8);
        Fx.point(loc.clone().add(0, 1.6, 0), Particle.SOUL, 24);
        Fx.sound(loc, Sound.ENTITY_ILLUSIONER_AMBIENT, 1.0f, 0.7f);
        instance.showTitle(
                Component.text("The Round", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("It is singing — play the same three notes back", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1, 0), Particle.SOUL, 60, 0.9);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 2.0f, 5.0, 30, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.WITCH, 7.5, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.4f, 0.5f);
        instance.showTitle(
                Component.text("♫ ALL VOICES ♫", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
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
