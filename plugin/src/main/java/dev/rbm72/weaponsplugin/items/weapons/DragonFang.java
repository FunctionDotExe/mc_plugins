package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.kit.Counterplay;
import dev.rbm72.weaponsplugin.items.kit.Props;
import dev.rbm72.weaponsplugin.items.kit.TempTerrain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dragon-hunter axe, rebuilt on real objects per batch-1 §0.1.
 * <p>
 * <b>What it used to be.</b> Every one of its four abilities was a particle drawing with a code effect
 * behind it: the roar was two {@code Particle.FLAME} rings plus {@code setVelocity} on everything nearby;
 * the breath was a {@code Particle.FLAME} cone plus a {@code damage()} loop over a dot-product check; the
 * leap was a velocity shove with cloud puffs at the feet; Dragon Form was a flame aura on a repeating task
 * with two potion effects. Delete the particle calls and three of the four still worked exactly as well,
 * which is §0.1's own test for an ability designed the wrong way round — the fire was never in the world,
 * only in the client's particle buffer.
 * <p>
 * <b>What it is now.</b> The roar is a ring of real {@link org.bukkit.entity.WindCharge}s that burst off
 * the floor and do the shoving themselves. The breath is real {@link org.bukkit.entity.SmallFireball}s and
 * real fire on the ground — the ground fire is the damage, so the cone denies a patch of floor for a few
 * seconds instead of resolving instantly and invisibly. The leap is a wind charge fired at the player's own
 * feet, vanilla's own launcher. Dragon Form leaves real fire where it lands blows. Particles remain only as
 * §0.1 allows: telegraph, impact flash, polish.
 * <p>
 * <b>Counterplay.</b> As a dragon-hunter drop it answers the verb dragons deal in — being thrown. Wing Leap
 * cancels a boss's shove before launching ({@link Counterplay#plant}), so the leap doubles as the answer to
 * gust and wing-buffet attacks rather than being a movement ability that happens to exist during them.
 */
public final class DragonFang extends Weapon {

    /** Wind charges in the roar ring. Eight covers a full circle without the burst sounds turning to mud. */
    private static final int ROAR_CHARGES = 8;
    /** Fireballs per breath. Three reads as a cone; more is a wall the target cannot step out of. */
    private static final int BREATH_FIREBALLS = 3;

    private final double roarRadius;
    private final double roarKnockback;
    private final double breathRange;
    private final int breathFireTicks;
    private final double leapPower;
    private final int leapSlowFallTicks;
    private final int dragonFormDurationTicks;
    private final double dragonFormDamageBonus;
    private final int dragonFormFireTicks;

    public DragonFang(WeaponsPlugin plugin) {
        super(plugin);
        this.roarRadius = configDouble("roar-radius", 4.5);
        this.roarKnockback = configDouble("roar-knockback", 1.5);
        this.breathRange = configDouble("breath-range", 6.0);
        this.breathFireTicks = configInt("breath-fire-ticks", 100);
        this.leapPower = configDouble("leap-power", 1.4);
        this.leapSlowFallTicks = configInt("leap-slow-fall-ticks", 60);
        this.dragonFormDurationTicks = configInt("dragon-form-duration-ticks", 180);
        this.dragonFormDamageBonus = configDouble("dragon-form-damage-bonus", 0.35);
        this.dragonFormFireTicks = configInt("dragon-form-fire-ticks", 60);
    }

    /**
     * Dragon Form is earned, not waited out — the one ultimate in this weapon's kit that is a state change
     * rather than a hit, and the state is worth more when the fight has already gone your way.
     * <p>
     * Weighted toward melee hits and damage taken because that is what an axe that closes distance actually
     * does: the fantasy is a hunter who has been in the dragon's face long enough to take on its shape, and
     * a player who spent the fight at range should not arrive at the same button.
     */
    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Fury")
                .accent(Color.fromRGB(255, 90, 0))
                .perMeleeHit(configDouble("fury-per-hit", 6.0))
                .perDamageDealt(configDouble("fury-per-damage-dealt", 0.5))
                .perDamageTaken(configDouble("fury-per-damage-taken", 1.2))
                .perAbilityCast(configDouble("fury-per-ability", 8.0))
                .perKill(configDouble("fury-per-kill", 10.0))
                .decay(configDouble("fury-decay-per-second", 2.0), configDouble("fury-decay-grace", 7.0))
                .cooldownFloor(configDouble("fury-cooldown-floor", 20.0))
                .build();
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        // The roar deals no damage of its own by design — it is displacement, and pricing it as damage in
        // the balance sheet would make a control ability read as an underperforming attack.
        return Map.of(
                CooldownManager.Slot.ABILITY2, configDouble("breath-damage", 5.0),
                CooldownManager.Slot.ULTIMATE, configDouble("dragon-form-sheet-value", 12.0));
    }

    @Override
    public Set<CounterVerb> counterVerbs() {
        return Set.of(CounterVerb.DISPLACEMENT);
    }

    private final Map<UUID, Long> dragonFormActiveUntilMs = new HashMap<>();
    private final Map<UUID, ItemDisplay> dragonFormIcons = new HashMap<>();

    @Override
    public String id() {
        return "dragon_fang";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_AXE;
    }

    @Override
    public String displayNameText() {
        return "Dragon Fang";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 8.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: unleash a roar,", NamedTextColor.GRAY),
                Component.text("knocking back nearby enemies.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Dragon Roar";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: breathe fire in", NamedTextColor.GRAY),
                Component.text("a cone ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Fire Breath";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: leap forward with slow", NamedTextColor.GRAY),
                Component.text("falling.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Wing Leap";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: briefly take on", NamedTextColor.GRAY),
                Component.text("Dragon Form, boosting damage and", NamedTextColor.GRAY),
                Component.text("resetting your leap.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Dragon Form";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDER_DRAGON_GROWL;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_BURN;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_ENDER_DRAGON_FLAP;
    }

    @Override
    public void onTick(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, true, false));
        boolean formActive = System.currentTimeMillis() < dragonFormActiveUntilMs.getOrDefault(player.getUniqueId(), 0L);
        // The flame trail that used to run here is gone with the rest of the aura: a player in Dragon Form
        // is now identifiable by the burning floor they leave behind, which is a real thing in the world and
        // visible to someone who is not looking at the particle budget.
        if (formActive) {
            ItemDisplay icon = dragonFormIcons.get(player.getUniqueId());
            if (icon != null && !icon.isDead()) {
                icon.teleport(player.getEyeLocation().add(0, 0.6, 0));
            }
        } else {
            dragonFormIcons.remove(player.getUniqueId());
        }
    }

    /**
     * In Dragon Form, hits hit harder <em>and</em> leave the floor burning under whatever was struck.
     * <p>
     * The fire is what makes the form legible from outside. A damage multiplier is invisible — the only way
     * to see the ultimate was the aura particles, which is why the old version needed a repeating cosmetic
     * task to stop the form "reading as one burst". Real fire under each blow means the form's duration is
     * written on the ground: an arena that has had a Dragon Form in it looks like one.
     */
    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (System.currentTimeMillis() >= dragonFormActiveUntilMs.getOrDefault(attacker.getUniqueId(), 0L)) {
            return;
        }
        event.setDamage(event.getDamage() * (1 + dragonFormDamageBonus));

        Block under = victim.getLocation().getBlock();
        if (under.getType().isAir() && under.getRelative(0, -1, 0).getType().isSolid()) {
            plugin.tempTerrain().place(attacker, under, Material.FIRE, dragonFormFireTicks);
        }
    }

    /**
     * Dragon Roar — a ring of real wind charges bursting off the floor around the player.
     * <p>
     * The shove is vanilla's, not ours. Each charge is aimed outward and slightly down so it detonates on
     * the ground at roughly {@link #roarRadius}, and the burst it produces already has the shape, the
     * falloff, the sound and the velocity handling that the old {@code setVelocity} loop was imitating —
     * including the part the loop got wrong, where a shove applied straight to velocity fights whatever the
     * server was already doing to that entity's movement.
     * <p>
     * Deals no damage on purpose. It is the kit's displacement tool, and giving it a damage number as well
     * would make it the obvious opener regardless of what the fight needed.
     */
    @Override
    public void ability1(Player player) {
        Location origin = player.getLocation();
        if (origin.getWorld() == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.9f);
        // Impact flash only — §0.1 permits particles for exactly this, and deleting this line changes
        // nothing about what the ability does.
        Fx.coloredBurst(origin.clone().add(0, 1, 0), Color.fromRGB(255, 100, 20), 2.2f, 40, roarRadius * 0.6);

        Location from = origin.clone().add(0, 0.8, 0);
        for (int i = 0; i < ROAR_CHARGES; i++) {
            double angle = 2 * Math.PI * i / ROAR_CHARGES;
            Vector outward = new Vector(Math.cos(angle), -0.35, Math.sin(angle))
                    .normalize().multiply(roarKnockback * 0.6);
            Props.windCharge(plugin, this, player, from.clone().add(outward.getX(), 0, outward.getZ()), outward);
        }
    }

    /**
     * Fire Breath — real fireballs, and real fire left burning on the ground they cross.
     * <p>
     * The ground fire is the ability. The old version resolved instantly against everything inside a cone
     * and left nothing behind, so it was impossible to read afterwards and impossible to play around: there
     * was no moment at which a player could see where the fire was, because there was no fire. Laying real
     * {@link Material#FIRE} through {@link TempTerrain} turns it into a few seconds of denied floor that
     * burns whoever stands in it — the damage now comes from a thing in the world, and the weapon stops
     * needing a {@code damage()} loop at all.
     * <p>
     * Every block goes through the temp ledger, so the fire reverts to whatever was there and cannot spread
     * into someone's build. A fire block the ledger refuses is simply skipped.
     */
    @Override
    public void ability2(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        if (eye.getWorld() == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.1f, 0.8f);

        Vector horizontal = new Vector(direction.getX(), 0, direction.getZ());
        Vector side = horizontal.lengthSquared() > 1e-6
                ? new Vector(-horizontal.getZ(), 0, horizontal.getX()).normalize()
                : new Vector(1, 0, 0);

        for (int i = 0; i < BREATH_FIREBALLS; i++) {
            double spread = (i - (BREATH_FIREBALLS - 1) / 2.0) * 0.16;
            Vector aim = direction.clone().add(side.clone().multiply(spread)).normalize().multiply(0.9);
            Props.fireball(plugin, this, player, eye.clone().add(direction.clone().multiply(0.8)), aim);
        }

        // Fire laid along the floor of the cone, walked outward one block at a time so it follows the
        // ground rather than hanging where the aim vector happened to point.
        Location ground = player.getLocation();
        for (double distance = 1.5; distance <= breathRange; distance += 1.0) {
            Location step = ground.clone().add(direction.clone().multiply(distance));
            for (double lateral = -1; lateral <= 1; lateral++) {
                Location spot = step.clone().add(side.clone().multiply(lateral * distance * 0.18));
                Block floor = spot.getWorld().getHighestBlockAt(spot);
                Block target = floor.getRelative(0, 1, 0);
                if (target.getType().isAir() && floor.getType().isSolid()) {
                    plugin.tempTerrain().place(player, target, Material.FIRE, breathFireTicks);
                }
            }
        }
    }

    /**
     * Wing Leap — a wind charge at your own feet, and the answer to being thrown.
     * <p>
     * Two changes, both doctrine. The launch is a real wind charge detonating underneath the player, which
     * is vanilla's own way of throwing something upward and interacts correctly with everything else
     * touching their velocity; the old direct {@code setVelocity} silently lost to any boss mechanic writing
     * velocity in the same tick. And it opens with {@link Counterplay#plant}, so using it while a boss has
     * just launched you cancels that throw first instead of adding to it — which is what makes this weapon
     * a dragon-hunter's tool rather than a mobility ability that happens to be in the kit.
     */
    @Override
    public void ability3(Player player) {
        // Cancel the boss's throw before adding our own, or the two compose into an uncontrolled launch.
        Counterplay.plant(plugin, player, 0);

        Vector direction = player.getLocation().getDirection().normalize();
        Location feet = player.getLocation();
        Props.windCharge(plugin, this, player, feet.clone().add(0, 0.1, 0),
                direction.clone().multiply(leapPower * 0.3).setY(-0.6));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, leapSlowFallTicks, 0));
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.1f);
    }

    /**
     * Dragon Form — a state, entered by having earned it, and readable off the floor while it lasts.
     * <p>
     * The old version needed a repeating particle task for the whole duration, explicitly "so it just keeps
     * the ultimate visually alive instead of reading as one burst". That comment was the bug report: the form
     * had nothing in the world to show for itself, so a cosmetic loop had to stand in for one. Now
     * {@link #onMeleeDamage} lays real fire under every blow, so the form is visible in the arena's state and
     * the sustained cosmetic is gone entirely — the one-off entry burst and the floating dragon head stay,
     * which is §0.1's "impact flash and polish".
     */
    @Override
    public void ultimate(Player player) {
        dragonFormActiveUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + (dragonFormDurationTicks * 50L));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, dragonFormDurationTicks, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dragonFormDurationTicks, 0));
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(255, 80, 0), 2.6f, 70, 0.95);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.6f);

        ItemDisplay icon = Fx.spinningIcon(plugin, player.getEyeLocation().add(0, 0.6, 0),
                Material.DRAGON_HEAD, 1.2f, dragonFormDurationTicks, 12.0);
        if (icon != null) {
            icon.setGravity(false);
            dragonFormIcons.put(player.getUniqueId(), icon);
        }
    }
}
