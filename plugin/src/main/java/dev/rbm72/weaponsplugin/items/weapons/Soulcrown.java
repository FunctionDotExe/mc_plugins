package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
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
 * Soulcrown — a shard of the Threefold Bane's own skull-core, still whispering with three voices.
 * A homing bolt, a personal decay pulse, a delayed converging burst, and an ultimate that briefly
 * makes the wielder grow to the boss's own monstrous scale.
 */
public final class Soulcrown extends Weapon {

    private static final Color SOUL_BLUE = Color.fromRGB(30, 200, 210);
    private static final Color DECAY_GREY = Color.fromRGB(50, 50, 55);

    private final double boltDamage;
    private final double boltHitRadius;
    private final double boltSpeed;
    private final int boltMaxLifeTicks;
    private final double pulseRadius;
    private final double pulseDamage;
    private final int pulseWitherTicks;
    private final double burstDamage;
    private final double burstRadius;
    private final int burstDelayTicks;
    private final int coronationDurationTicks;
    private final double coronationScale;

    public Soulcrown(WeaponsPlugin plugin) {
        super(plugin);
        this.boltDamage = configDouble("bolt-damage", 6.5);
        this.boltHitRadius = configDouble("bolt-hit-radius", 1.4);
        this.boltSpeed = configDouble("bolt-speed", 0.9);
        this.boltMaxLifeTicks = configInt("bolt-max-life-ticks", 50);
        this.pulseRadius = configDouble("pulse-radius", 3.5);
        this.pulseDamage = configDouble("pulse-damage", 5.5);
        this.pulseWitherTicks = configInt("pulse-wither-ticks", 60);
        this.burstDamage = configDouble("burst-damage", 10.0);
        this.burstRadius = configDouble("burst-radius", 3.0);
        this.burstDelayTicks = configInt("burst-delay-ticks", 20);
        this.coronationDurationTicks = configInt("coronation-duration-ticks", 100);
        this.coronationScale = configDouble("coronation-scale", 1.6);
    }

    @Override
    public String id() {
        return "soulcrown";
    }

    @Override
    public Material material() {
        return Material.WITHER_SKELETON_SKULL;
    }

    @Override
    public String displayNameText() {
        return "Soulcrown";
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
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 12.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 60.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Skull Bolt — a homing bolt", NamedTextColor.GRAY),
                Component.text("chases whatever you're looking near.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Skull Bolt";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Decay Pulse — a ring", NamedTextColor.GRAY),
                Component.text("of rot spreads out from where you stand.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Decay Pulse";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Triple Focus — mark a spot,", NamedTextColor.GRAY),
                Component.text("a converging burst lands there shortly after.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Triple Focus";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Hollow Coronation —", NamedTextColor.GRAY),
                Component.text("grow to monstrous scale for a short time.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Hollow Coronation";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_WITHER_SHOOT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_WITHER_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_WITHER_AMBIENT;
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
        ItemDisplay icon = Fx.spinningIcon(plugin, eye, Material.WITHER_SKELETON_SKULL, 0.5f, boltMaxLifeTicks + 5, 40.0);
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
                    StatusEffectManager.apply(finalTarget, PotionEffectType.WITHER, 60, 0);
                    Fx.coloredBurst(pos, SOUL_BLUE, 1.8f, 30, 0.4);
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
                Fx.coloredBurst(pos, SOUL_BLUE, 1.0f, 6, 0.1);
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
        Fx.coloredBurst(origin.clone().add(0, 1, 0), DECAY_GREY, 1.8f, 34, 0.6);
        Fx.sound(player, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.7f);
        for (Entity entity : world.getNearbyEntities(origin, pulseRadius, pulseRadius, pulseRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                StatusEffectManager.apply(living, PotionEffectType.WITHER, pulseWitherTicks, 0);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ability3(Player player) {
        double damage = burstDamage * rarity().statMultiplier();
        Location markedSpot = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(6));
        World world = player.getWorld();
        Fx.coloredRing(markedSpot, SOUL_BLUE, 1.4f, burstRadius * 0.6, 20, 0);
        Fx.sound(player, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.6f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Fx.coloredBurst(markedSpot.clone().add(0, 1, 0), SOUL_BLUE, 2.0f, 40, 0.7);
                Fx.burst(markedSpot.clone().add(0, 1, 0), Particle.EXPLOSION, 3, 0.2);
                for (Entity entity : world.getNearbyEntities(markedSpot, burstRadius, burstRadius, burstRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
            }
        }.runTaskLater(plugin, burstDelayTicks);
    }

    @Override
    public void ultimate(Player player) {
        var scaleAttr = player.getAttribute(Attribute.SCALE);
        double originalScale = scaleAttr != null ? scaleAttr.getBaseValue() : 1.0;
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(originalScale * coronationScale);
        }
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), SOUL_BLUE, 2.2f, 50, 0.8);
        Fx.sound(player, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.STRENGTH, coronationDurationTicks, 1));
        new BukkitRunnable() {
            @Override
            public void run() {
                if (scaleAttr != null && player.isOnline()) {
                    scaleAttr.setBaseValue(originalScale);
                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), DECAY_GREY, 1.4f, 24, 0.5);
                }
            }
        }.runTaskLater(plugin, coronationDurationTicks);
    }
}
