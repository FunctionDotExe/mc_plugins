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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Vitriol — a golden ladle scooped straight out of the Amalgamated Bulk, still dripping. An acid
 * cone, a self-shrink for speed and evasion, a sticky homing glob, and an ultimate that swallows the
 * nearest enemy whole.
 */
public final class Vitriol extends Weapon {

    private static final Color OOZE_GREEN = Color.fromRGB(90, 170, 70);

    private final double spitRange;
    private final double spitDamage;
    private final int spitPoisonTicks;
    private final double shrinkScale;
    private final int shrinkDurationTicks;
    private final double globDamage;
    private final double globHitRadius;
    private final double globSpeed;
    private final int globMaxLifeTicks;
    private final int globSlownessTicks;
    private final double engulfDamage;
    private final double engulfRange;
    private final int engulfWeaknessTicks;

    public Vitriol(WeaponsPlugin plugin) {
        super(plugin);
        this.spitRange = configDouble("spit-range", 5.0);
        this.spitDamage = configDouble("spit-damage", 6.0);
        this.spitPoisonTicks = configInt("spit-poison-ticks", 60);
        this.shrinkScale = configDouble("shrink-scale", 0.6);
        this.shrinkDurationTicks = configInt("shrink-duration-ticks", 120);
        this.globDamage = configDouble("glob-damage", 6.5);
        this.globHitRadius = configDouble("glob-hit-radius", 1.4);
        this.globSpeed = configDouble("glob-speed", 0.7);
        this.globMaxLifeTicks = configInt("glob-max-life-ticks", 50);
        this.globSlownessTicks = configInt("glob-slowness-ticks", 70);
        this.engulfDamage = configDouble("engulf-damage", 11.0);
        this.engulfRange = configDouble("engulf-range", 3.5);
        this.engulfWeaknessTicks = configInt("engulf-weakness-ticks", 90);
    }

    @Override
    public String id() {
        return "vitriol";
    }

    @Override
    public Material material() {
        return Material.GOLDEN_SHOVEL;
    }

    @Override
    public String displayNameText() {
        return "Vitriol";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 6.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 16.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 45.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Corrosion")
                .accent(OOZE_GREEN)
                .perMeleeHit(configDouble("corrosion-per-hit", 6.0))
                .perDamageDealt(configDouble("corrosion-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("corrosion-per-ability", 8.0))
                .perKill(configDouble("corrosion-per-kill", 12.0))
                .decay(configDouble("corrosion-decay-per-second", 2.0), configDouble("corrosion-decay-grace", 7.0))
                .cooldownFloor(configDouble("corrosion-cooldown-floor", 40.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Acid Spit — a corrosive cone", NamedTextColor.GRAY),
                Component.text("poisons everything ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Acid Spit";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Mitosis — shrink down,", NamedTextColor.GRAY),
                Component.text("trading size for a burst of speed.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Mitosis";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Glob Toss — a sticky homing", NamedTextColor.GRAY),
                Component.text("glob chases and slows whatever it hits.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Glob Toss";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Engulf — swallow the", NamedTextColor.GRAY),
                Component.text("nearest enemy whole.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Engulf";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_SLIME_ATTACK;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_SLIME_SQUISH;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_SLIME_JUMP;
    }

    @Override
    public void ability1(Player player) {
        double damage = spitDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        Fx.sound(player, castSound(), 1.0f, 0.8f);
        for (double d = 1; d <= spitRange; d += 0.5) {
            Fx.coloredBurst(origin.clone().add(direction.clone().multiply(d)), OOZE_GREEN, 1.0f, 4, 0.3);
        }
        for (Entity entity : world.getNearbyEntities(origin, spitRange, spitRange, spitRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = direction.dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.6) {
                continue;
            }
            living.damage(damage, player);
            StatusEffectManager.apply(living, PotionEffectType.POISON, spitPoisonTicks, 0);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        var scaleAttr = player.getAttribute(Attribute.SCALE);
        double originalScale = scaleAttr != null ? scaleAttr.getBaseValue() : 1.0;
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(originalScale * shrinkScale);
        }
        StatusEffectManager.apply(player, PotionEffectType.SPEED, shrinkDurationTicks, 2);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), OOZE_GREEN, 1.6f, 30, 0.5);
        Fx.sound(player, Sound.ENTITY_SLIME_SQUISH, 1.0f, 1.4f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (scaleAttr != null && player.isOnline()) {
                    scaleAttr.setBaseValue(originalScale);
                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), OOZE_GREEN, 1.4f, 24, 0.5);
                }
            }
        }.runTaskLater(plugin, shrinkDurationTicks);
    }

    @Override
    public void ability3(Player player) {
        double damage = globDamage * rarity().statMultiplier();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Fx.sound(player, Sound.ENTITY_SLIME_ATTACK, 1.0f, 0.8f);
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
        ItemDisplay icon = Fx.spinningIcon(plugin, eye, Material.SLIME_BALL, 0.6f, globMaxLifeTicks + 5, 35.0);
        new BukkitRunnable() {
            int ticks = 0;
            Location pos = eye.clone();

            @Override
            public void run() {
                if (ticks >= globMaxLifeTicks || (finalTarget != null && !finalTarget.isValid())) {
                    removeIcon();
                    cancel();
                    return;
                }
                Vector heading = finalTarget != null
                        ? finalTarget.getLocation().add(0, 1, 0).toVector().subtract(pos.toVector())
                        : direction.clone().multiply(10);
                if (finalTarget != null && heading.lengthSquared() <= globHitRadius * globHitRadius) {
                    finalTarget.damage(damage, player);
                    StatusEffectManager.apply(finalTarget, PotionEffectType.SLOWNESS, globSlownessTicks, 1);
                    Fx.coloredBurst(pos, OOZE_GREEN, 1.8f, 30, 0.4);
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
                pos.add(heading.normalize().multiply(globSpeed));
                if (icon != null && !icon.isDead()) {
                    icon.teleport(pos);
                }
                Fx.coloredBurst(pos, OOZE_GREEN, 1.0f, 6, 0.1);
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
    public void ultimate(Player player) {
        double damage = engulfDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        World world = player.getWorld();
        LivingEntity nearest = null;
        double closestDistance = engulfRange;
        for (Entity entity : world.getNearbyEntities(origin, engulfRange, engulfRange, engulfRange)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                double dist = living.getLocation().distance(origin);
                if (dist < closestDistance) {
                    closestDistance = dist;
                    nearest = living;
                }
            }
        }
        if (nearest == null) {
            return;
        }
        Fx.coloredBurst(nearest.getLocation().add(0, 1, 0), OOZE_GREEN, 2.2f, 50, 0.6);
        Fx.burst(nearest.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 40, 0.5);
        Fx.sound(player, Sound.ENTITY_SLIME_SQUISH, 1.4f, 0.4f);
        nearest.damage(damage, player);
        StatusEffectManager.apply(nearest, PotionEffectType.WEAKNESS, engulfWeaknessTicks, 1);
        Fx.bloodSpray(nearest.getLocation().add(0, 1, 0));
    }
}
