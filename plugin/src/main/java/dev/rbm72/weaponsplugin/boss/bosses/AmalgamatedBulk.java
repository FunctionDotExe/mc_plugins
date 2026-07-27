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
import dev.rbm72.weaponsplugin.boss.mechanics.EscalatingDoomMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.HazardPatchMechanic;
import dev.rbm72.weaponsplugin.boss.mechanics.SplitShardsMechanic;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AcidSprayAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.EngulfAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.OozeSlamAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.SplitAttack;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.Vitriol;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Amalgamated Bulk — a mountain of sentient ooze that sheds mass every phase, visibly shrinking
 * and leaving Bulklings behind to keep fighting, until it reabsorbs everything it's lost and
 * balloons back up huge for one last, furious stand.
 */
public final class AmalgamatedBulk extends Boss {

    private static final Color OOZE_GREEN = Color.fromRGB(90, 170, 70);
    private static final double GIANT_SCALE = 1.7;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public AmalgamatedBulk(WeaponsPlugin plugin) {
        super(plugin);

        OozeSlamAttack oozeSlam = new OozeSlamAttack(plugin);
        AcidSprayAttack acidSpray = new AcidSprayAttack(plugin);
        SplitAttack split = new SplitAttack(plugin);
        EngulfAttack engulf = new EngulfAttack(plugin);

        this.phases = List.of(
                // Acid Puddle Legacy: it leaks as it moves and the leaks stay. The floor closes in
                // steadily, so standing still stops being an option long before it becomes dangerous.
                new BossPhase("The Bulk", 1.0,
                        List.of(oozeSlam, acidSpray),
                        false, AmalgamatedBulk::onEnterPhase1,
                        this::acidLegacy),
                // Ungated: no mechanic, just a very large slime doing its best. Pure race.
                new BossPhase("First Shedding", 0.72,
                        List.of(oozeSlam, acidSpray, split),
                        false, AmalgamatedBulk::onEnterPhase2),
                // Split Pressure: its signature. It sheds shards and wants them back — every shard
                // killed inside the window is mass it never recovers, every survivor walks home and
                // heals it. Partial success is real and the group can watch the arithmetic run.
                new BossPhase("Second Shedding", 0.40,
                        List.of(oozeSlam, acidSpray, split, engulf),
                        false, AmalgamatedBulk::onEnterPhase3,
                        this::splitPressure),
                // Reabsorption: what is left of it runs on a clock only sustained damage holds down,
                // and every overflow leaves it permanently larger and harder to cut.
                new BossPhase("Reabsorption", 0.15,
                        List.of(oozeSlam, acidSpray, engulf),
                        true, AmalgamatedBulk::onEnterEnrage,
                        this::reabsorption));

        this.lootTable = new LootTable()
                .guaranteed(() -> new Vitriol(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> gelhideArmor(Material.DIAMOND_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> gelhideArmor(Material.DIAMOND_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), AmalgamatedBulk::oozeMaterials)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), AmalgamatedBulk::crownOfTheBulk);
    }

    @Override
    public String id() {
        return "amalgamated_bulk";
    }

    @Override
    public Component displayName() {
        return Component.text("The Amalgamated Bulk", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.SLIME;
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
     * Acid Puddle Legacy: puddles keep appearing and growing and only leave on their own schedule, so
     * the usable floor shrinks the whole band. Hard-capped well short of sealing the arena, and never
     * placed under someone's feet — it denies space rather than dealing unreadable damage.
     */
    private PhaseMechanic acidLegacy(BossInstance instance) {
        return new HazardPatchMechanic(instance, "Acid Legacy", OOZE_GREEN, Particle.ITEM_SLIME,
                configInt("acid-spawn-interval-ticks", 65),
                configInt("acid-max-patches", 8),
                configDouble("acid-start-radius", 2.6),
                configDouble("acid-max-radius", 5.2),
                configDouble("acid-growth-per-second", 0.3),
                configInt("acid-lifetime-ticks", 340),
                configDouble("acid-damage-per-second", 3.5),
                PotionEffectType.SLOWNESS, 1,
                configDouble("acid-placement-fraction", 0.8));
    }

    /**
     * Split Pressure: its signature, and the phase Total Absorption belongs to — the shards it fails
     * to get back are gone for good, and the ones it does get back heal it. Blunted rather than immune
     * while split, so a group that never solves it still finishes, just against a much fatter slime.
     */
    private PhaseMechanic splitPressure(BossInstance instance) {
        return new SplitShardsMechanic(instance, "Split Pressure", "Shed Mass", OOZE_GREEN,
                EntityType.SLIME,
                configInt("split-shard-count", 5),
                configDouble("split-shard-health", 26.0),
                configInt("split-window-ticks", 220),
                configInt("split-regroup-delay-ticks", 120),
                configDouble("split-heal-per-survivor", 14.0),
                configDouble("split-exposed-multiplier", 2.2),
                configInt("split-exposed-ticks", 170),
                configInt("split-stagger-ticks", 60),
                configDouble("split-spawn-radius", 6.0));
    }

    /**
     * Reabsorption: a clock only sustained damage pushes back. Each overflow leaves it permanently
     * bigger, faster and harder to hurt — falling behind here compounds rather than merely costing
     * health.
     */
    private PhaseMechanic reabsorption(BossInstance instance) {
        return new EscalatingDoomMechanic(instance, "Reabsorption", OOZE_GREEN,
                configDouble("reabsorption-fill-per-second", 5.0),
                configDouble("reabsorption-damage-per-point", 5.0),
                configDouble("reabsorption-cap", 100.0),
                configDouble("reabsorption-scale-step", 1.1),
                configDouble("reabsorption-speed-step", 1.05),
                configDouble("reabsorption-harden-step", 0.08),
                configDouble("reabsorption-overflow-damage", 14.0));
    }

    /** Offset from its phase boundaries (0.72 / 0.40 / 0.15) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.86, 0.30}),
                new PullNukeEvent(plugin, id(), new double[] {0.58, 0.24},
                        "TOTAL ABSORPTION", "It is inhaling the room — get out of its reach"));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.ITEM_SLIME, "boss.amalgamated_bulk.ambient", Sound.ENTITY_SLIME_SQUISH,
                false, null);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("🟢 THE AMALGAMATED BULK 🟢", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("A mountain of ooze that remembers being many things", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("🟢 THE BULK DISSOLVES 🟢", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("Every shed piece finally goes still", NamedTextColor.GRAY);
    }

    private static void onEnterPhase1(BossInstance instance) {
        AttributeInstance scaleAttr = instance.entity().getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(GIANT_SCALE);
        }
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1, 0), OOZE_GREEN, 1.6f, 34, 0.6);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SLIME, 24, 0.5);
        Fx.sound(loc, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.5f);
    }

    private static void onEnterPhase2(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(0.8, 1.1);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), OOZE_GREEN, 2.0f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.ITEM_SLIME, 6.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.7f);
        instance.showTitle(
                Component.text("First Shedding", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Mass peels away and keeps moving", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(0.75, 1.15);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), OOZE_GREEN, 1.8f, 40, 0.9);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SLIME, 34, 0.7);
        Fx.sound(loc, Sound.ENTITY_SLIME_SQUISH, 1.3f, 0.9f);
        instance.showTitle(
                Component.text("Second Shedding", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Thinner now, and hungrier for it", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(2.1, 1.2);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SLIME, 60, 0.9);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 2.0f, 5.0, 30, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.ITEM_SLIME, 7.5, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_SLIME_SQUISH, 1.4f, 0.3f);
        instance.showTitle(
                Component.text("🟢 REABSORPTION 🟢", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("It calls every shed piece back home at once", NamedTextColor.GRAY));
    }

    private static ItemStack gelhideArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Gelplate", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack oozeMaterials() {
        Material[] picks = {Material.SLIME_BLOCK, Material.HONEY_BLOCK, Material.SLIME_BALL};
        Material picked = picks[ThreadLocalRandom.current().nextInt(picks.length)];
        return new ItemStack(picked, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack crownOfTheBulk() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Core of the Bulk", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Somehow, still faintly warm.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
