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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Spinelash — the Grafted Horror's own tailbone, torn free and weaponized. Four abilities, one for
 * each donor stitched into the horror's body: a venomous whip crack (spider), a short reality-warp
 * blink (enderman), a self-igniting marrow pulse (blaze), and a life-draining decay nova (wither).
 */
public final class Spinelash extends Weapon {

    private static final Color SICKLY_GREEN = Color.fromRGB(90, 130, 60);
    private static final Color VOID_PURPLE = Color.fromRGB(90, 0, 120);
    private static final Color EMBER = Color.fromRGB(255, 120, 0);
    private static final Color DECAY_GREY = Color.fromRGB(60, 60, 60);

    private final double whipRange;
    private final double whipDamage;
    private final int whipPoisonTicks;
    private final double blinkDistance;
    private final double blinkDamage;
    private final double marrowRadius;
    private final double marrowDamage;
    private final int marrowFireTicks;
    private final double novaMaxRadius;
    private final int novaDurationTicks;
    private final double novaDamagePerTick;
    private final double novaHealFraction;

    public Spinelash(WeaponsPlugin plugin) {
        super(plugin);
        this.whipRange = configDouble("whip-range", 5.0);
        this.whipDamage = configDouble("whip-damage", 6.5);
        this.whipPoisonTicks = configInt("whip-poison-ticks", 60);
        this.blinkDistance = configDouble("blink-distance", 6.0);
        this.blinkDamage = configDouble("blink-damage", 7.0);
        this.marrowRadius = configDouble("marrow-radius", 3.5);
        this.marrowDamage = configDouble("marrow-damage", 6.0);
        this.marrowFireTicks = configInt("marrow-fire-ticks", 40);
        this.novaMaxRadius = configDouble("nova-max-radius", 6.5);
        this.novaDurationTicks = configInt("nova-duration-ticks", 30);
        this.novaDamagePerTick = configDouble("nova-damage-per-tick", 3.0);
        this.novaHealFraction = configDouble("nova-heal-fraction", 0.5);
    }

    @Override
    public String id() {
        return "spinelash";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SHOVEL;
    }

    @Override
    public String displayNameText() {
        return "Spinelash";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 7.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 8.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Tail Lash — a venomous whip", NamedTextColor.GRAY),
                Component.text("crack rends everything ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Tail Lash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Warp Snap — blink", NamedTextColor.GRAY),
                Component.text("forward and rend whatever you land on.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Warp Snap";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Cinder Marrow — an igniting", NamedTextColor.GRAY),
                Component.text("pulse burns everyone around you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Cinder Marrow";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Unravel — an expanding", NamedTextColor.GRAY),
                Component.text("decay nova saps life back into you.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Unravel";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_SPIDER_HURT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_RAVAGER_ATTACK;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_WITHER_AMBIENT;
    }

    @Override
    public void ability1(Player player) {
        double damage = whipDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        Fx.sound(player, castSound(), 1.0f, 0.8f);
        for (double d = 1; d <= whipRange; d += 0.5) {
            Fx.coloredBurst(origin.clone().add(direction.clone().multiply(d)), SICKLY_GREEN, 1.0f, 4, 0.25);
        }
        for (Entity entity : world.getNearbyEntities(origin, whipRange, whipRange, whipRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = direction.dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.6) {
                continue;
            }
            living.damage(damage, player);
            StatusEffectManager.apply(living, PotionEffectType.POISON, whipPoisonTicks, 0);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = blinkDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Location destination = origin.clone().add(origin.getDirection().normalize().multiply(blinkDistance));
        if (isSafe(destination)) {
            destination.setDirection(origin.getDirection());
            player.teleport(destination);
        }
        Fx.burst(player.getLocation().add(0, 1, 0), Particle.PORTAL, 30, 0.4);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), VOID_PURPLE, 1.4f, 24, 0.4);
        Fx.sound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 2.5, 2.5, 2.5)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ability3(Player player) {
        double damage = marrowDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        World world = player.getWorld();
        Fx.coloredBurst(origin.clone().add(0, 1, 0), EMBER, 1.6f, 34, 0.6);
        Fx.burst(origin.clone().add(0, 1, 0), Particle.FLAME, 24, 0.5);
        Fx.sound(player, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
        for (Entity entity : world.getNearbyEntities(origin, marrowRadius, marrowRadius, marrowRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                living.setFireTicks(Math.max(living.getFireTicks(), marrowFireTicks));
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        double damage = novaDamagePerTick * rarity().statMultiplier();
        World world = player.getWorld();
        Location center = player.getLocation();
        Fx.sound(player, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.7f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= novaDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                double radius = novaMaxRadius * (ticks + 1) / (double) novaDurationTicks;
                Fx.coloredRing(center, DECAY_GREY, 1.3f, radius, 26, ticks * 0.3);
                Fx.ring(center, Particle.SOUL, radius, 20, ticks * 0.3);
                if (ticks % 5 == 0) {
                    for (Entity entity : world.getNearbyEntities(center, radius, 3.0, radius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            double dist = living.getLocation().distance(center);
                            if (dist <= radius && dist >= radius - 1.5) {
                                living.damage(damage, player);
                                StatusEffectManager.apply(living, PotionEffectType.WITHER, 60, 0);
                                double heal = damage * novaHealFraction;
                                double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                                player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
                                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isSafe(Location destination) {
        return !destination.getBlock().getType().isSolid()
                && !destination.clone().add(0, 1, 0).getBlock().getType().isSolid();
    }
}
