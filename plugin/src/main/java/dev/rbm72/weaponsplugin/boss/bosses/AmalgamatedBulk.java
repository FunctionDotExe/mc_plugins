package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.events.PullNukeEvent;
import dev.rbm72.weaponsplugin.boss.bosses.bulk.BulkPhases;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.AcidSprayAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.EngulfAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.OozeSlamAttack;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Amalgamated Bulk — a growth, not a creature, and the roster's <b>restraint</b> boss: the one fight
 * where killing is dangerous. It is a walking sculk catalyst (batch-3 spec §3), so every add killed
 * inside a catalyst node's radius blooms real sculk across the floor, which slows the group, blinds them
 * with shriekers and feeds the Bulk. Kill too eagerly and you grow it; kill too slowly and it reabsorbs
 * what it shed. The correct play is a narrow, deliberate band.
 * <ol>
 *   <li><b>Shedding</b> — the catalyst rule, taught by an unmissable first mistake.</li>
 *   <li><b>The Spread</b> — the floor is the problem, and clearing it is the objective.</li>
 *   <li><b>Reabsorption</b> — adds must die now, and still not near a node.</li>
 *   <li><b>Full Mass</b> — no adds, no nodes, just it and the floor the group left behind.</li>
 * </ol>
 * This is the only boss in the roster where excess DPS is itself the cheese, and §3.6's answer is
 * structural: the tax is the arena, so no damage wall is needed anywhere in the fight.
 */
public final class AmalgamatedBulk extends Boss {

    private static final Color OOZE_GREEN = Color.fromRGB(90, 170, 70);
    private static final Color SCULK_TEAL = Color.fromRGB(20, 130, 130);
    private static final double GIANT_SCALE = 1.7;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public AmalgamatedBulk(WeaponsPlugin plugin) {
        super(plugin);

        // Shedding is a mechanic now rather than an attack, so SplitAttack is gone from the pool — two
        // systems spawning adds on separate clocks would make the Bulkling economy unreadable.
        OozeSlamAttack oozeSlam = new OozeSlamAttack(plugin);
        AcidSprayAttack acidSpray = new AcidSprayAttack(plugin);
        EngulfAttack engulf = new EngulfAttack(plugin);

        this.phases = List.of(
                // Shedding: nodes down, Bulklings shedding, and the first close-in kill teaches the rule.
                new BossPhase("Shedding", 1.0,
                        List.of(oozeSlam, acidSpray),
                        false, AmalgamatedBulk::onEnterPhase1,
                        instance -> BulkPhases.shedding(instance, 0.75)),
                // The Spread: shriekers, Darkness, slowed ground — and a floor the group has to clean.
                new BossPhase("The Spread", 0.75,
                        List.of(oozeSlam, acidSpray, engulf),
                        false, AmalgamatedBulk::onEnterPhase2,
                        instance -> BulkPhases.theSpread(instance, 0.50)),
                // Reabsorption: the restraint band tightens from both ends at once.
                new BossPhase("Reabsorption", 0.50,
                        List.of(oozeSlam, acidSpray, engulf),
                        false, AmalgamatedBulk::onEnterPhase3,
                        instance -> BulkPhases.reabsorption(instance, 0.24)),
                // Full Mass: it balloons, stops shedding, and comes at the group across its own floor.
                new BossPhase("Full Mass", 0.24,
                        List.of(oozeSlam, acidSpray, engulf),
                        true, AmalgamatedBulk::onEnterEnrage,
                        BulkPhases::fullMass));

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
     * Four times the roster default. Pulling an add clear of a node before killing it, and scraping
     * sculk off the floor, are slow physical acts, and {@code BulkPhaseMechanic#progressSignal} counts
     * all three currencies (clean kills, denied pulls, floor cleared) so patient play never reads as a
     * stalled fight.
     */
    @Override
    public int phaseFloorTimeoutMs() {
        return configInt("phase-floor-timeout-ms", 180_000);
    }

    /** Offset from its phase boundaries (0.75 / 0.50 / 0.24) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.86, 0.36}),
                new PullNukeEvent(plugin, id(), new double[] {0.62, 0.30},
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
        instance.empower(0.85, 1.1);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), SCULK_TEAL, 2.0f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.SCULK_SOUL, 6.0, 3, 2L);
        Fx.sound(loc, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.2f, 0.7f);
        instance.showTitle(
                Component.text("The Spread", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("The floor is growing — break it back", NamedTextColor.GRAY));
    }

    private static void onEnterPhase3(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(0.9, 1.15);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), OOZE_GREEN, 1.8f, 40, 0.9);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SLIME, 34, 0.7);
        Fx.sound(loc, Sound.ENTITY_SLIME_JUMP, 1.3f, 0.5f);
        instance.showTitle(
                Component.text("Reabsorption", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It wants its mass back — kill what it reaches for", NamedTextColor.GRAY));
    }

    private static void onEnterEnrage(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        instance.empower(2.1, 1.2);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SLIME, 60, 0.9);
        Fx.coloredRing(loc, Color.fromRGB(180, 20, 20), 2.0f, 5.0, 30, 0);
        Fx.expandingRings(instance.plugin(), loc, Particle.ITEM_SLIME, 7.5, 4, 2L);
        Fx.sound(loc, Sound.ENTITY_SLIME_SQUISH, 1.4f, 0.3f);
        instance.showTitle(
                Component.text("🟢 FULL MASS 🟢", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Every piece it ever shed, back in one body", NamedTextColor.GRAY));
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
