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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dashes through enemies. Every hit also arcs a chain of lightning to one
 * nearby enemy, so hitting a cluster deals more than the sum of its parts.
 */
public final class Stormbreaker extends Weapon {

    private static final Color STORM_COLOR = Color.fromRGB(214, 236, 255);

    private final double dashSpeed;
    private final double abilityDamage;
    private final double hitRadius;
    private final double chainRadius;
    private final double chainDamageFraction;
    private final int dashTicks;

    public Stormbreaker(WeaponsPlugin plugin) {
        super(plugin);
        this.dashSpeed = configDouble("dash-speed", 1.6);
        this.abilityDamage = configDouble("ability-damage", 6.0);
        this.hitRadius = configDouble("hit-radius", 1.6);
        this.chainRadius = configDouble("chain-radius", 3.0);
        this.chainDamageFraction = configDouble("chain-damage-fraction", 0.5);
        this.dashTicks = configInt("dash-ticks", 10);
    }

    @Override
    public String id() {
        return "stormbreaker";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Stormbreaker";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("cooldown-seconds", 6.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: dash forward, damaging", NamedTextColor.GRAY),
                Component.text("enemies in your path. Each hit arcs", NamedTextColor.GRAY),
                Component.text("chain lightning to a nearby foe.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Thunderdash";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_RIPTIDE_1;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_CRIT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_NOTE_BLOCK_PLING;
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(dashSpeed).setY(0.25));

        double effectiveDamage = abilityDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= dashTicks) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);
                Fx.trail(loc, Particle.CLOUD, 16, 0.4, 0.02);
                Fx.trail(loc.clone().add(0, 0.3, 0), Particle.CLOUD, 10, 0.35, 0.02);
                Fx.trail(loc, Particle.ELECTRIC_SPARK, 12, 0.3, 0.03);
                Fx.trail(loc.clone().add(0, 0.4, 0), Particle.ELECTRIC_SPARK, 8, 0.25, 0.03);

                for (Entity nearby : player.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                    if (!(nearby instanceof LivingEntity entity)) {
                        continue;
                    }
                    if (entity.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }
                    if (!alreadyHit.add(entity.getUniqueId())) {
                        continue;
                    }

                    strike(entity, player, effectiveDamage, direction);
                    chainLightning(entity, player, effectiveDamage, alreadyHit);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void strike(LivingEntity entity, Player player, double damage, Vector direction) {
        entity.damage(damage, player);
        Fx.point(entity.getLocation().add(0, 1, 0), Particle.SWEEP_ATTACK, 1);
        Fx.coloredBurst(entity.getLocation().add(0, 1, 0), STORM_COLOR, 1.6f, 24, 0.5);
        Fx.coloredBurst(entity.getLocation().add(0, 1.6, 0), STORM_COLOR, 1.3f, 14, 0.4);
        Fx.bloodSpray(entity.getLocation().add(0, 1.2, 0));
        Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);

        Vector knockback = direction.clone().multiply(1.1).setY(0.35);
        entity.setVelocity(entity.getVelocity().add(knockback));
    }

    private void chainLightning(LivingEntity from, Player player, double baseDamage, Set<UUID> alreadyHit) {
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;

        for (Entity nearby : from.getNearbyEntities(chainRadius, chainRadius, chainRadius)) {
            if (!(nearby instanceof LivingEntity candidate) || alreadyHit.contains(candidate.getUniqueId())) {
                continue;
            }
            if (candidate.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            double distanceSquared = candidate.getLocation().distanceSquared(from.getLocation());
            if (distanceSquared < closest) {
                closest = distanceSquared;
                target = candidate;
            }
        }

        if (target == null) {
            return;
        }

        alreadyHit.add(target.getUniqueId());
        // Two parallel strands offset in height give the chain-lightning arc actual
        // thickness instead of reading as a single thin line of dots.
        Fx.line(from.getLocation().add(0, 1, 0), target.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 18);
        Fx.line(from.getLocation().add(0, 1.3, 0), target.getLocation().add(0, 1.3, 0), Particle.ELECTRIC_SPARK, 14);
        target.damage(baseDamage * chainDamageFraction, player);
        Fx.point(target.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 20);
        Fx.coloredBurst(target.getLocation().add(0, 1, 0), STORM_COLOR, 1.5f, 18, 0.4);
        if (target.getWorld() != null) {
            target.getWorld().strikeLightningEffect(target.getLocation());
        }
        Fx.bloodSpray(target.getLocation().add(0, 1.2, 0));
        Fx.sound(target.getLocation(), Sound.ENTITY_CREEPER_HURT, 0.8f, 1.6f);
    }
}
