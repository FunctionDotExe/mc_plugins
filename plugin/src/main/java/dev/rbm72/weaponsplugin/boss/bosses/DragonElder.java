package dev.rbm72.weaponsplugin.boss.bosses;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAmbiance;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.LootTable;
import dev.rbm72.weaponsplugin.boss.events.HitCountShieldEvent;
import dev.rbm72.weaponsplugin.boss.events.SpreadCheckEvent;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FireballBarrageAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.FirestormBreathAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.GrabAndDropAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.MeteorWingsAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.TailSweepAttack;
import dev.rbm72.weaponsplugin.boss.bosses.attacks.WingGustAttack;
import dev.rbm72.weaponsplugin.boss.bosses.dragon.CirclingPhase;
import dev.rbm72.weaponsplugin.boss.bosses.dragon.DenyThePerchPhase;
import dev.rbm72.weaponsplugin.boss.bosses.dragon.DiveBombPhase;
import dev.rbm72.weaponsplugin.boss.bosses.dragon.GroundedPhase;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.weapons.WyrmscaleBow;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Dragon Elder — an ancient wyrm that owns the sky and only touches the ground on its own terms.
 * <p>
 * The roster's ranged-mandatory boss and the inverse of every other fight: melee has to <em>earn</em>
 * windows instead of having them by default. Its signature interaction is pure vanilla — real fireballs
 * a player can hit back, and real perch pillars the group can destroy to deny it rest. See
 * {@code dev.rbm72.weaponsplugin.boss.bosses.dragon} for the aerial rig, the pillars, the wing-membrane
 * meter and the fireball spawner that make that loop real; this class only wires the four phases and
 * the flavour around them.
 * <p>
 * <b>Four phases, none of which ends on health alone</b> (batch-2 §4.3):
 * <ol>
 *   <li><b>Circling</b> (100–76%) — baseline aerial fight; exits on one fireball deflected.</li>
 *   <li><b>Deny the Perch</b> (76–50%) — pillar denial; exits on three perches denied in a row.</li>
 *   <li><b>Dive Bomb</b> (50–22%) — strafing runs across whatever pillars survived; exits on three
 *       runs survived.</li>
 *   <li><b>Grounded</b> (&lt;22%) — wings shredded, it comes down for good; the roles fully invert.</li>
 * </ol>
 * <b>He is never invulnerable.</b> No phase here sets a damage multiplier below 1.0 or forces
 * invulnerability — what gates the group is distance (he is genuinely out of melee reach for most of
 * the fight) and the P2/P3 pillar trade-off, never a permission switch.
 */
public final class DragonElder extends Boss {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);
    private static final Color ELDER_BLACK = Color.fromRGB(30, 5, 5);
    private static final Color EMBER = Color.fromRGB(255, 130, 30);

    /** Health-fraction seams, shared between the phase list and its flavour hooks. */
    private static final double DENY_ENTRY = 0.76;
    private static final double DIVEBOMB_ENTRY = 0.50;
    private static final double GROUNDED_ENTRY = 0.22;

    private final List<BossPhase> phases;
    private final LootTable lootTable;

    public DragonElder(WeaponsPlugin plugin) {
        super(plugin);

        WingGustAttack wingGust = new WingGustAttack(plugin);
        FireballBarrageAttack fireballBarrage = new FireballBarrageAttack(plugin);
        MeteorWingsAttack meteorWings = new MeteorWingsAttack(plugin);
        TailSweepAttack tailSweep = new TailSweepAttack(plugin);
        GrabAndDropAttack grabAndDrop = new GrabAndDropAttack(plugin);
        FirestormBreathAttack firestormBreath = new FirestormBreathAttack(plugin);

        this.phases = List.of(
                // Circling: the baseline. HoverRepositionAttack, DiveBombAttack, SkyboundAssaultAttack
                // and CataclysmAttack are deliberately not reused anywhere in this pool — each one either
                // moves the boss's own position/velocity directly (DiveBombAttack, CataclysmAttack;
                // that would fight the new aerial rig's continuous per-tick velocity control for
                // ownership of the entity) or gates its own hittability aloft (SkyboundAssaultAttack;
                // that model predates and contradicts this rework's "boss stays live, never gated by a
                // permission switch" rule) or is flatly superseded (HoverRepositionAttack — the aerial
                // rig's CIRCLING state already does continuous hover/chase every tick).
                new BossPhase("Circling", 1.0,
                        List.of(wingGust, fireballBarrage, meteorWings, tailSweep),
                        false, DragonElder::onEnterCircling,
                        instance -> new CirclingPhase(instance, DENY_ENTRY)),
                // Deny the Perch: pillar denial becomes real. Grab and Drop joins here per batch-2 §4.4
                // ("P2+") — it specifically hunts a stationary archer, which is the anti-cheese answer to
                // this being the one boss where ranged is mandatory.
                new BossPhase("Deny the Perch", DENY_ENTRY,
                        List.of(wingGust, fireballBarrage, meteorWings, tailSweep, grabAndDrop),
                        false, DragonElder::onEnterDenyThePerch,
                        instance -> new DenyThePerchPhase(instance, DIVEBOMB_ENTRY)),
                // Dive Bomb: it stops perching, strafing runs start. Firestorm Breath joins here per
                // "Fire Breath: P3 onward" — it does not gate anything, so it stays an ordinary pool
                // attack rather than phase-owned state; see DiveBombPhase's javadoc for why.
                new BossPhase("Dive Bomb", DIVEBOMB_ENTRY,
                        List.of(wingGust, fireballBarrage, meteorWings, tailSweep, grabAndDrop, firestormBreath),
                        false, DragonElder::onEnterDiveBomb,
                        instance -> new DiveBombPhase(instance, GROUNDED_ENTRY)),
                // Grounded: wings shredded, it comes down for good — role reversal as the finale.
                // MeteorWingsAttack drops out (nothing to "wheel overhead" with any more); everything
                // else that never needed flight stays in the pool.
                new BossPhase("Grounded", GROUNDED_ENTRY,
                        List.of(wingGust, fireballBarrage, tailSweep, grabAndDrop, firestormBreath),
                        true, DragonElder::onEnterGrounded,
                        GroundedPhase::new));

        this.lootTable = new LootTable()
                .guaranteed(() -> new WyrmscaleBow(plugin).createItem())
                .weighted(configDouble("loot-armor-chance", 0.35), () -> wyrmscaleArmor(Material.NETHERITE_HELMET))
                .weighted(configDouble("loot-armor-chance", 0.35), () -> wyrmscaleArmor(Material.NETHERITE_CHESTPLATE))
                .weighted(configDouble("loot-materials-chance", 0.6), DragonElder::dragonBreath)
                .weighted(configDouble("loot-materials-chance", 0.6), DragonElder::blazeRods)
                .weighted(configDouble("loot-cosmetic-chance", 0.008), DragonElder::elderDragonSkull);
    }

    @Override
    public String id() {
        return "dragon_elder";
    }

    @Override
    public Component displayName() {
        return Component.text("The Dragon Elder", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true);
    }

    @Override
    public EntityType baseEntityType() {
        return EntityType.PHANTOM;
    }

    @Override
    public double maxHealth() {
        return configDouble("max-health", 400.0);
    }

    @Override
    public List<BossPhase> phases() {
        return phases;
    }

    @Override
    public LootTable lootTable() {
        return lootTable;
    }

    /** Offset from its phase boundaries (0.76 / 0.50 / 0.22) so they land mid-phase, not on transitions. */
    @Override
    public List<BossEvent> events() {
        return List.of(
                new HitCountShieldEvent(plugin, id(), new double[] {0.88}),
                new SpreadCheckEvent(plugin, id(), new double[] {0.62, 0.30}));
    }

    @Override
    public BossAmbiance ambiance() {
        return BossAmbiance.of(Particle.FLAME, "boss.dragon_elder.ambient", Sound.ENTITY_ENDER_DRAGON_AMBIENT,
                true, Biome.WINDSWEPT_HILLS);
    }

    @Override
    public Component entranceTitle() {
        return Component.text("🐉 THE DRAGON ELDER 🐉", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component entranceSubtitle() {
        return Component.text("An ancient wyrm blots out the sky — bring a bow", NamedTextColor.GRAY);
    }

    @Override
    public Component defeatTitle() {
        return Component.text("🐉 THE DRAGON ELDER FALLS 🐉", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    @Override
    public Component defeatSubtitle() {
        return Component.text("The last of the elder wyrms comes crashing down for good", NamedTextColor.GRAY);
    }

    private static void onEnterCircling(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        // The elder unfurls its wings and takes the sky: a roaring corona of flame and a downdraft ring.
        Fx.expandingRings(instance.plugin(), loc, Particle.FLAME, 6.0, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 1.5, 0), DRAGON_RED, 1.8f, 45, 0.7);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.LAVA, 20, 0.6);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 0.5f);
    }

    private static void onEnterDenyThePerch(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.FLAME, 45, 0.9);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), EMBER, 1.7f, 40, 0.8);
        Fx.expandingRings(instance.plugin(), loc, Particle.LAVA, 7.0, 3, 2L);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.5f);
        instance.showTitle(
                Component.text("Deny the Perch", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("It's healing off the pillars — break the one it's heading for", NamedTextColor.GRAY));
    }

    private static void onEnterDiveBomb(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1.2, 0), Particle.LAVA, 35, 0.7);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), ELDER_BLACK, 1.8f, 30, 0.8);
        for (int ring = 0; ring < 3; ring++) {
            Fx.coloredRing(loc, DRAGON_RED, 1.6f, 6.0 - ring * 1.5, 22, ring * 0.6);
        }
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        Fx.sound(loc, Sound.ENTITY_BLAZE_DEATH, 0.6f, 0.4f);
        instance.showTitle(
                Component.text("Dive Bomb", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("It stops perching — strafing runs, use what pillars survived", NamedTextColor.GRAY));
    }

    private static void onEnterGrounded(BossInstance instance) {
        Location loc = instance.entity().getLocation();
        Fx.burst(loc.clone().add(0, 1, 0), Particle.FLAME, 45, 0.7);
        Fx.coloredRing(loc, DRAGON_RED, 1.6f, 4.5, 24, 0);
        Fx.spinningIcon(instance.plugin(), loc.clone().add(0, 2.6, 0), Material.MAGMA_BLOCK, 1.4f, 100, 12.0);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.0f);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f);
        instance.showTitle(
                Component.text("🔥 GROUNDED 🔥", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Wings shredded — it's yours to finish", NamedTextColor.GRAY));
    }

    private static ItemStack wyrmscaleArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Wyrmscale", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack dragonBreath() {
        return new ItemStack(Material.DRAGON_BREATH, ThreadLocalRandom.current().nextInt(1, 4));
    }

    private static ItemStack blazeRods() {
        return new ItemStack(Material.BLAZE_ROD, ThreadLocalRandom.current().nextInt(2, 6));
    }

    /** Sub-1% cosmetic trophy — a player-head with no functional effect. */
    private static ItemStack elderDragonSkull() {
        ItemStack item = new ItemStack(Material.DRAGON_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.displayName(Component.text("Elder Dragon Skull", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("The skull of an elder wyrm, still warm with dying fire.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
