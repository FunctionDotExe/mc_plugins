package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.kit.Counterplay;
import dev.rbm72.weaponsplugin.items.kit.LungeStrike;
import dev.rbm72.weaponsplugin.items.kit.Props;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The demolition pike: it throws the floor at you.
 * <p>
 * {@link Props#fallingBlock} had no callers in the weapon roster before this one, which is odd given how much
 * of §0.1 is about weight and terrain — a real falling block is the cheapest honest way to make an ability
 * heavy. It arcs, it is drawn by the game from a real block texture, it obeys gravity, it lands where physics
 * puts it rather than where a particle loop drew it, and the wielder can watch a boss walk under one. Every
 * eruption here is built out of the material actually under the target's feet, so the arena at the end of a
 * fight is visibly the arena this weapon has been rearranging.
 * <p>
 * Damage is applied by the ability rather than by the blocks: vanilla falling-block damage scales with fall
 * distance and ignores every number on the weapon (see {@code Props.fallingBlock}), so the blocks supply the
 * physics and the read while the balance sheet keeps a figure it can rank.
 */
public final class Sunderpike extends Weapon {

    private static final Color SLATE = Color.fromRGB(126, 142, 158);

    /** What an eruption falls back to when there is nothing sensible under the target (air, water, a void). */
    private static final Material DEFAULT_DEBRIS = Material.COBBLESTONE;

    private final double underpinDamage;
    private final double contactRadius;
    private final int contactTicks;
    private final int eruptionBlocks;
    private final double eruptionSpread;
    private final int eruptionLandingTicks;
    private final double eruptionLandingDamage;
    private final double sunderDamage;
    private final double sunderRadius;
    private final int caveInBlocks;
    private final double caveInRadius;
    private final int caveInHeight;
    private final int caveInDurationTicks;
    private final double caveInDamage;

    public Sunderpike(WeaponsPlugin plugin) {
        super(plugin);
        this.underpinDamage = configDouble("underpin-damage", 9.0);
        this.contactRadius = configDouble("contact-radius", 2.2);
        this.contactTicks = configInt("contact-ticks", 12);
        this.eruptionBlocks = configInt("eruption-blocks", 8);
        this.eruptionSpread = configDouble("eruption-spread", 0.35);
        this.eruptionLandingTicks = configInt("eruption-landing-ticks", 16);
        this.eruptionLandingDamage = configDouble("eruption-landing-damage", 4.0);
        this.sunderDamage = configDouble("sunder-damage", 7.0);
        this.sunderRadius = configDouble("sunder-radius", 4.5);
        this.caveInBlocks = configInt("cave-in-blocks", 26);
        this.caveInRadius = configDouble("cave-in-radius", 6.0);
        this.caveInHeight = configInt("cave-in-height", 14);
        this.caveInDurationTicks = configInt("cave-in-duration-ticks", 70);
        this.caveInDamage = configDouble("cave-in-damage", 6.0);
    }

    @Override
    public String id() {
        return "sunderpike";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_SPEAR;
    }

    @Override
    public String displayNameText() {
        return "Sunderpike";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.5);
    }

    @Override
    public boolean ability1OnLunge() {
        return true;
    }

    @Override
    public int lungePowerBonus() {
        return configInt("lunge-power-bonus", 2);
    }

    /** A pike this heavy staggers what it hits, the way a maul or an axe does. */
    @Override
    public boolean isHeavyWeapon() {
        return true;
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 11.0);
    }

    @Override
    public String ability1Name() {
        return "Underpin";
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Hold right-click, then release to", NamedTextColor.GRAY),
                Component.text("lunge. The floor under whatever you", NamedTextColor.GRAY),
                Component.text("reach tears loose and comes back", NamedTextColor.GRAY),
                Component.text("down on it.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Sunder";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: drive the pike in", NamedTextColor.GRAY),
                Component.text("and undo the boss's stonework around", NamedTextColor.GRAY),
                Component.text("you, throwing the debris outward.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Cave-In";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Sneak+off-hand: bring the ceiling", NamedTextColor.GRAY),
                Component.text("down. Real blocks rain across the", NamedTextColor.GRAY),
                Component.text("area for several seconds.", NamedTextColor.GRAY));
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Weight")
                .accent(SLATE)
                .perMeleeHit(configDouble("weight-per-hit", 6.0))
                .perDamageDealt(configDouble("weight-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("weight-per-ability", 9.0))
                .perKill(configDouble("weight-per-kill", 12.0))
                .decay(configDouble("weight-decay-per-second", 2.0), configDouble("weight-decay-grace", 7.0))
                .cooldownFloor(configDouble("weight-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_SPEAR_LUNGE_3;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_STONE_BREAK;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_DEEPSLATE_PLACE;
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        return Map.of(CooldownManager.Slot.ABILITY1, underpinDamage + eruptionLandingDamage,
                CooldownManager.Slot.ABILITY2, sunderDamage,
                CooldownManager.Slot.ULTIMATE, caveInDamage * 3);
    }

    /**
     * <b>Counterplay.</b> Sunder is the terrain answer. A boss that walls the group off or encases someone in
     * stone is undone by the same cast that throws the debris, which is why this is the drop worth carrying
     * into a fight that builds.
     */
    @Override
    public Set<CounterVerb> counterVerbs() {
        return Set.of(CounterVerb.PILLARS);
    }

    @Override
    public void ability1(Player player) {
        double damage = underpinDamage * rarity().statMultiplier();

        LungeStrike.onFirstContact(plugin, player, contactRadius, contactTicks, (victim, at) -> {
            victim.damage(damage, player);
            Fx.bloodSpray(at.clone().add(0, 1, 0));
            Fx.sound(at, hitSound(), 1.0f, 0.7f);

            erupt(player, at, eruptionBlocks, 0.55);
            // The blocks are real, so their arrival is a real event with a real delay. The landing hit is
            // paid when they actually come down, not at cast time — a target that walked out from under the
            // eruption has genuinely dodged something.
            new BukkitRunnable() {
                @Override
                public void run() {
                    strikeArea(player, at, 2.5, eruptionLandingDamage * rarity().statMultiplier());
                }
            }.runTaskLater(plugin, Math.max(1, eruptionLandingTicks));
        });
    }

    @Override
    public void ability2(Player player) {
        int cleared = Counterplay.breakPillars(plugin, player, sunderRadius);
        Location origin = player.getLocation();

        // The debris is the boss's stonework leaving, thrown outward as real blocks. More of it when there
        // was more to undo, so the ability reads as answering the terrain rather than ignoring it.
        erupt(player, origin, Math.min(16, 6 + cleared / 4), 0.8);
        strikeArea(player, origin, sunderRadius, sunderDamage * rarity().statMultiplier());
        Fx.sound(player, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
        Fx.coloredRing(origin.clone().add(0, 0.2, 0), SLATE, 1.2f, sunderRadius, 28, 0);
    }

    @Override
    public void ultimate(Player player) {
        Location centre = player.getLocation();
        double damage = caveInDamage * rarity().statMultiplier();
        int perWave = Math.max(1, caveInBlocks / Math.max(1, caveInDurationTicks / 5));

        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (elapsed >= caveInDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < perWave; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = random.nextDouble() * caveInRadius;
                    Location above = centre.clone().add(Math.cos(angle) * distance, caveInHeight, Math.sin(angle) * distance);
                    FallingBlock block = Props.fallingBlock(plugin, Sunderpike.this, above, debrisAt(above));
                    block.setVelocity(new Vector(0, -0.35, 0));
                    Props.despawnAfter(plugin, block, 120);
                }
                strikeArea(player, centre, caveInRadius, damage);
                elapsed += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /** Tears {@code count} real blocks out of the ground and throws them up. */
    private void erupt(Player caster, Location centre, int count, double upward) {
        Material debris = debrisAt(centre);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Location from = centre.clone().add(Math.cos(angle) * 0.6, 0.1, Math.sin(angle) * 0.6);
            FallingBlock block = Props.fallingBlock(plugin, this, from, debris);
            block.setVelocity(new Vector(
                    Math.cos(angle) * eruptionSpread + random.nextGaussian() * 0.05,
                    upward + random.nextDouble() * 0.2,
                    Math.sin(angle) * eruptionSpread + random.nextGaussian() * 0.05));
            Props.despawnAfter(plugin, block, 120);
        }
        Fx.blockBurst(centre.clone().add(0, 0.2, 0), debris, 12, 0.5);
    }

    /** One damage pass over everything but the caster, used wherever debris arrives. */
    private void strikeArea(Player caster, Location centre, double radius, double damage) {
        for (Entity nearby : centre.getWorld().getNearbyEntities(centre, radius, radius, radius)) {
            if (nearby instanceof LivingEntity living && !living.equals(caster) && !living.isDead()) {
                living.damage(damage, caster);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    /**
     * The material an eruption at {@code at} is made of: whatever is actually under it.
     * <p>
     * Falls back rather than refusing, because the alternative is an ability that silently does nothing when
     * fired over water or off a ledge — and gravity-bearing or non-solid sources are skipped so a falling
     * block of sand never turns into a second, real sand physics event nothing recorded.
     */
    private Material debrisAt(Location at) {
        Block floor = at.getBlock().getRelative(0, -1, 0);
        Material type = floor.getType();
        if (!type.isSolid() || type.hasGravity() || !type.isBlock()) {
            return DEFAULT_DEBRIS;
        }
        return type;
    }
}
