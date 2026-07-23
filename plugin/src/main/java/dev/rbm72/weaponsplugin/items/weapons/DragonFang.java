package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dragon-hunter axe: roar knockback, fire breath cone, wing leap, and a brief Dragon Form ultimate. */
public final class DragonFang extends Weapon {

    private final double roarRadius;
    private final double roarKnockback;
    private final double breathDamage;
    private final double breathRange;
    private final double leapPower;
    private final int leapSlowFallTicks;
    private final int dragonFormDurationTicks;
    private final double dragonFormDamageBonus;

    public DragonFang(WeaponsPlugin plugin) {
        super(plugin);
        this.roarRadius = configDouble("roar-radius", 4.5);
        this.roarKnockback = configDouble("roar-knockback", 1.5);
        this.breathDamage = configDouble("breath-damage", 5.0);
        this.breathRange = configDouble("breath-range", 6.0);
        this.leapPower = configDouble("leap-power", 1.4);
        this.leapSlowFallTicks = configInt("leap-slow-fall-ticks", 60);
        this.dragonFormDurationTicks = configInt("dragon-form-duration-ticks", 180);
        this.dragonFormDamageBonus = configDouble("dragon-form-damage-bonus", 0.35);
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
        if (formActive && player.getVelocity().lengthSquared() > 0.01) {
            Fx.trail(player.getLocation(), Particle.FLAME, 6, 0.25, 0.015);
            Fx.trail(player.getLocation(), Particle.LAVA, 2, 0.2, 0.01);
        }
        if (formActive) {
            ItemDisplay icon = dragonFormIcons.get(player.getUniqueId());
            if (icon != null && !icon.isDead()) {
                icon.teleport(player.getEyeLocation().add(0, 0.6, 0));
            }
        } else {
            dragonFormIcons.remove(player.getUniqueId());
        }
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (System.currentTimeMillis() < dragonFormActiveUntilMs.getOrDefault(attacker.getUniqueId(), 0L)) {
            event.setDamage(event.getDamage() * (1 + dragonFormDamageBonus));
        }
    }

    @Override
    public void ability1(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.coloredBurst(origin.clone().add(0, 1, 0), Color.fromRGB(255, 100, 20), 2.2f, 55, roarRadius * 0.6);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.9f);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 12 || !player.isOnline()) {
                    cancel();
                    return;
                }
                double progress = (tick + 1) / 12.0;
                double angle = tick * 0.4;
                Fx.ring(origin, Particle.FLAME, roarRadius * progress, 40, angle);
                Fx.ring(origin, Particle.FLAME, roarRadius * progress + 0.4, 32, angle + 0.2);
                Fx.ring(origin, Particle.LAVA, roarRadius * progress, 24, -angle);
                Fx.ring(origin, Particle.LAVA, roarRadius * progress - 0.4, 16, -angle - 0.2);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        for (Entity entity : world.getNearbyEntities(origin, roarRadius, roarRadius, roarRadius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector knockback = living.getLocation().toVector().subtract(origin.toVector()).normalize()
                    .multiply(roarKnockback).setY(0.4);
            living.setVelocity(living.getVelocity().add(knockback));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = breathDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Location coneCenter = origin.clone().add(direction.clone().multiply(2)).add(0, 1, 0);
        Vector horizontal = new Vector(direction.getX(), 0, direction.getZ());
        Vector perpendicular = horizontal.lengthSquared() > 1e-6
                ? new Vector(-horizontal.getZ(), 0, horizontal.getX()).normalize().multiply(0.5)
                : new Vector(0.5, 0, 0);
        Fx.trail(coneCenter, Particle.FLAME, 60, 0.9, 0.06);
        Fx.trail(coneCenter.clone().add(perpendicular), Particle.FLAME, 32, 0.7, 0.05);
        Fx.trail(coneCenter.clone().subtract(perpendicular), Particle.FLAME, 32, 0.7, 0.05);
        Fx.trail(coneCenter, Particle.LAVA, 16, 0.75, 0.04);
        Fx.coloredBurst(coneCenter, Color.fromRGB(255, 110, 0), 2.0f, 40, 0.9);

        for (Entity entity : world.getNearbyEntities(origin, breathRange, breathRange, breathRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (direction.dot(toEntity) < 0.5) {
                continue;
            }
            living.damage(damage, player);
            living.setFireTicks(80);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(leapPower).setY(0.8));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, leapSlowFallTicks, 0));

        Location loc = player.getLocation();
        Vector horizontal = new Vector(direction.getX(), 0, direction.getZ());
        Vector side = horizontal.lengthSquared() > 1e-6
                ? new Vector(-horizontal.getZ(), 0, horizontal.getX()).normalize().multiply(0.6)
                : new Vector(0.6, 0, 0);
        Fx.burst(loc.clone().add(side), Particle.CLOUD, 32, 0.55);
        Fx.burst(loc.clone().subtract(side), Particle.CLOUD, 32, 0.55);
        Fx.coloredBurst(loc.clone().add(0, 0.3, 0), Color.fromRGB(220, 60, 0), 2.0f, 30, 0.7);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.1f);
    }

    @Override
    public void ultimate(Player player) {
        dragonFormActiveUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + (dragonFormDurationTicks * 50L));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, dragonFormDurationTicks, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dragonFormDurationTicks, 0));
        Fx.burst(player.getLocation(), Particle.FLAME, 90, 1.1);
        Fx.burst(player.getLocation(), Particle.LAVA, 30, 0.8);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(255, 80, 0), 2.6f, 70, 0.95);
        Fx.coloredBurst(player.getLocation().add(0, 1.4, 0), Color.fromRGB(255, 150, 0), 2.0f, 45, 0.7);
        Fx.ring(player.getLocation(), Particle.FLAME, 1.5, 32);
        Fx.ring(player.getLocation().add(0, 1.2, 0), Particle.FLAME, 2.0, 40);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.6f);

        ItemDisplay icon = Fx.spinningIcon(plugin, player.getEyeLocation().add(0, 0.6, 0),
                Material.DRAGON_HEAD, 1.2f, dragonFormDurationTicks, 12.0);
        if (icon != null) {
            icon.setGravity(false);
            dragonFormIcons.put(player.getUniqueId(), icon);
        }

        // Purely cosmetic sustained aura for the whole Dragon Form duration — no damage tied to
        // this loop, so it just keeps the ultimate visually "alive" instead of reading as one burst.
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                Long activeUntil = dragonFormActiveUntilMs.get(player.getUniqueId());
                if (tick >= dragonFormDurationTicks || !player.isOnline()
                        || activeUntil == null || System.currentTimeMillis() >= activeUntil) {
                    cancel();
                    return;
                }
                if (tick % 8 == 0) {
                    Location loc = player.getLocation().add(0, 1, 0);
                    double angle = tick * 0.25;
                    Fx.ring(loc, Particle.FLAME, 1.3, 26, angle);
                    Fx.ring(loc, Particle.LAVA, 1.7, 18, -angle);
                    Fx.coloredBurst(loc, Color.fromRGB(255, 90, 0), 1.6f, 12, 0.4);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
