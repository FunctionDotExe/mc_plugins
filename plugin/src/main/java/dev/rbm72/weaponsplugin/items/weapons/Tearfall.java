package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Tearfall — a lens carved from one of the Weeping Colossus's own eyes. A homing tear, a wailing
 * debuff pulse, a marked gaze-burst, and an ultimate that shrinks the wielder down into something
 * small, fast, and considerably more dangerous.
 */
public final class Tearfall extends Weapon {

    private static final Color SORROW_BLUE = Color.fromRGB(70, 120, 200);

    private final double boltDamage;
    private final double boltHitRadius;
    private final double boltSpeed;
    private final int boltMaxLifeTicks;
    private final int boltBlindnessTicks;
    private final double pulseRadius;
    private final double pulseDamage;
    private final int pulseSlownessTicks;
    private final double gazeDamage;
    private final double gazeRadius;
    private final int gazeDelayTicks;
    private final double contractionScale;
    private final int contractionDurationTicks;

    public Tearfall(WeaponsPlugin plugin) {
        super(plugin);
        this.boltDamage = configDouble("bolt-damage", 6.0);
        this.boltHitRadius = configDouble("bolt-hit-radius", 1.4);
        this.boltSpeed = configDouble("bolt-speed", 0.85);
        this.boltMaxLifeTicks = configInt("bolt-max-life-ticks", 50);
        this.boltBlindnessTicks = configInt("bolt-blindness-ticks", 40);
        this.pulseRadius = configDouble("pulse-radius", 4.5);
        this.pulseDamage = configDouble("pulse-damage", 4.5);
        this.pulseSlownessTicks = configInt("pulse-slowness-ticks", 60);
        this.gazeDamage = configDouble("gaze-damage", 10.0);
        this.gazeRadius = configDouble("gaze-radius", 2.8);
        this.gazeDelayTicks = configInt("gaze-delay-ticks", 24);
        this.contractionScale = configDouble("contraction-scale", 0.65);
        this.contractionDurationTicks = configInt("contraction-duration-ticks", 140);
    }

    @Override
    public String id() {
        return "tearfall";
    }

    @Override
    public Material material() {
        return Material.SPYGLASS;
    }

    @Override
    public String displayNameText() {
        return "Tearfall";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 5.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 12.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 9.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Sorrow")
                .accent(SORROW_BLUE)
                .perMeleeHit(configDouble("sorrow-per-hit", 5.0))
                .perDamageDealt(configDouble("sorrow-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("sorrow-per-ability", 8.0))
                .perKill(configDouble("sorrow-per-kill", 11.0))
                .decay(configDouble("sorrow-decay-per-second", 2.0), configDouble("sorrow-decay-grace", 7.0))
                .cooldownFloor(configDouble("sorrow-cooldown-floor", 45.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Tear Bolt — a homing droplet", NamedTextColor.GRAY),
                Component.text("chases whatever you're aiming near.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Tear Bolt";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Wailing Pulse — a", NamedTextColor.GRAY),
                Component.text("cry of sorrow slows everyone nearby.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Wailing Pulse";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Piercing Gaze — mark a spot,", NamedTextColor.GRAY),
                Component.text("a beam collapses onto it shortly after.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Piercing Gaze";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Contraction — shrink", NamedTextColor.GRAY),
                Component.text("down into something small, fast, and furious.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Contraction";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_GHAST_SHOOT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GHAST_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_GHAST_AMBIENT;
    }

    @Override
    public void ability1(Player player) {
        double damage = boltDamage * rarity().statMultiplier();
        Fx.sound(player, castSound(), 1.0f, 0.9f);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        LivingEntity target = null;
        double closestDot = 0.85;
        for (Entity entity : player.getWorld().getNearbyEntities(eye, 20, 20, 20)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(eye.toVector()).normalize();
            double dot = direction.dot(toEntity);
            if (dot > closestDot) {
                closestDot = dot;
                target = living;
            }
        }
        LivingEntity finalTarget = target;
        ItemDisplay icon = Fx.spinningIcon(plugin, eye, Material.PRISMARINE_SHARD, 0.5f, boltMaxLifeTicks + 5, 40.0);
        new BukkitRunnable() {
            int ticks = 0;
            Location pos = eye.clone();

            @Override
            public void run() {
                if (ticks >= boltMaxLifeTicks || (finalTarget != null && !finalTarget.isValid())) {
                    removeIcon();
                    cancel();
                    return;
                }
                Vector heading = finalTarget != null
                        ? finalTarget.getLocation().add(0, 1, 0).toVector().subtract(pos.toVector())
                        : direction.clone().multiply(10);
                if (finalTarget != null && heading.lengthSquared() <= boltHitRadius * boltHitRadius) {
                    finalTarget.damage(damage, player);
                    StatusEffectManager.apply(finalTarget, PotionEffectType.BLINDNESS, boltBlindnessTicks, 0);
                    Fx.coloredBurst(pos, SORROW_BLUE, 1.8f, 30, 0.4);
                    Fx.bloodSpray(pos);
                    removeIcon();
                    cancel();
                    return;
                }
                if (finalTarget == null && pos.distance(eye) > 10) {
                    removeIcon();
                    cancel();
                    return;
                }
                pos.add(heading.normalize().multiply(boltSpeed));
                if (icon != null && !icon.isDead()) {
                    icon.teleport(pos);
                }
                Fx.coloredBurst(pos, SORROW_BLUE, 1.0f, 6, 0.1);
                ticks++;
            }

            private void removeIcon() {
                if (icon != null && !icon.isDead()) {
                    icon.remove();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability2(Player player) {
        double damage = pulseDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        World world = player.getWorld();
        Fx.coloredBurst(origin.clone().add(0, 1, 0), SORROW_BLUE, 1.8f, 34, 0.6);
        Fx.sound(player, Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.7f);
        for (Entity entity : world.getNearbyEntities(origin, pulseRadius, pulseRadius, pulseRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, pulseSlownessTicks, 1);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ability3(Player player) {
        double damage = gazeDamage * rarity().statMultiplier();
        Location markedSpot = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(6));
        World world = player.getWorld();
        Fx.coloredRing(markedSpot, SORROW_BLUE, 1.4f, gazeRadius * 0.6, 20, 0);
        Fx.sound(player, Sound.ENTITY_GHAST_SHOOT, 1.0f, 0.6f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Fx.coloredBurst(markedSpot.clone().add(0, 1, 0), SORROW_BLUE, 2.0f, 40, 0.7);
                Fx.burst(markedSpot.clone().add(0, 1, 0), Particle.EXPLOSION, 3, 0.2);
                for (Entity entity : world.getNearbyEntities(markedSpot, gazeRadius, gazeRadius, gazeRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
            }
        }.runTaskLater(plugin, gazeDelayTicks);
    }

    @Override
    public void ultimate(Player player) {
        var scaleAttr = player.getAttribute(Attribute.SCALE);
        double originalScale = scaleAttr != null ? scaleAttr.getBaseValue() : 1.0;
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(originalScale * contractionScale);
        }
        StatusEffectManager.apply(player, PotionEffectType.SPEED, contractionDurationTicks, 2);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, contractionDurationTicks, 1));
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), SORROW_BLUE, 2.0f, 44, 0.7);
        Fx.sound(player, Sound.ENTITY_GHAST_SCREAM, 1.0f, 1.5f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (scaleAttr != null && player.isOnline()) {
                    scaleAttr.setBaseValue(originalScale);
                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), SORROW_BLUE, 1.4f, 24, 0.5);
                }
            }
        }.runTaskLater(plugin, contractionDurationTicks);
    }
}
