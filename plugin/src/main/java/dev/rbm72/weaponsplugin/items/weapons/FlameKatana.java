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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dashes through enemies, igniting whatever it hits, and leaves a scorched
 * trail behind that keeps burning anyone who walks through it for a few
 * seconds — the fire is entirely code-driven damage ticks, never real fire
 * blocks, so it can't spread or grief terrain.
 */
public final class FlameKatana extends Weapon {

    private final double dashSpeed;
    private final double abilityDamage;
    private final double hitRadius;
    private final int dashTicks;
    private final int burnTicks;
    private final double trailDamage;
    private final int trailDurationTicks;

    public FlameKatana(WeaponsPlugin plugin) {
        super(plugin);
        this.dashSpeed = configDouble("dash-speed", 1.5);
        this.abilityDamage = configDouble("ability-damage", 7.0);
        this.hitRadius = configDouble("hit-radius", 1.6);
        this.dashTicks = configInt("dash-ticks", 10);
        this.burnTicks = configInt("burn-ticks", 60);
        this.trailDamage = configDouble("trail-damage", 1.5);
        this.trailDurationTicks = configInt("trail-duration-ticks", 60);
    }

    @Override
    public String id() {
        return "flame_katana";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Flame Katana";
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
        return configDouble("cooldown-seconds", 7.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: dash through enemies,", NamedTextColor.GRAY),
                Component.text("burning everything you strike, and", NamedTextColor.GRAY),
                Component.text("leave a scorched trail that keeps", NamedTextColor.GRAY),
                Component.text("burning anyone who crosses it.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Scorching Rush";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_FIRECHARGE_USE;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_BURN;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_FIRE_AMBIENT;
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(dashSpeed).setY(0.25));

        double effectiveDamage = abilityDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();
        List<Location> trailPoints = new ArrayList<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= dashTicks) {
                    cancel();
                    igniteTrail(trailPoints);
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);
                Fx.trail(loc, Particle.FLAME, 16, 0.4, 0.03);
                Fx.trail(loc, Particle.LAVA, 4, 0.35, 0.01);
                if (ticks % 2 == 0) {
                    trailPoints.add(loc.clone());
                }

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

                    entity.damage(effectiveDamage, player);
                    entity.setFireTicks(burnTicks);
                    Fx.burst(entity.getLocation().add(0, 1, 0), Particle.FLAME, 26, 0.45);
                    Fx.coloredBurst(entity.getLocation().add(0, 1, 0), Color.fromRGB(255, 90, 0), 1.9f, 22, 0.5);
                    Fx.bloodSpray(entity.getLocation().add(0, 1.2, 0));
                    Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);

                    Vector knockback = direction.clone().multiply(1.1).setY(0.35);
                    entity.setVelocity(entity.getVelocity().add(knockback));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void igniteTrail(List<Location> trailPoints) {
        if (trailPoints.isEmpty()) {
            return;
        }
        double effectiveTrailDamage = trailDamage * rarity().statMultiplier();
        int interval = 10;

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= trailDurationTicks) {
                    cancel();
                    return;
                }
                for (Location point : trailPoints) {
                    World world = point.getWorld();
                    if (world == null) {
                        continue;
                    }
                    Fx.point(point, Particle.SMALL_FLAME, 6);
                    for (Entity nearby : world.getNearbyEntities(point, 1.0, 1.0, 1.0)) {
                        if (nearby instanceof LivingEntity entity) {
                            entity.damage(effectiveTrailDamage);
                            entity.setFireTicks(20);
                        }
                    }
                }
                elapsed += interval;
            }
        }.runTaskTimer(plugin, 10L, interval);
    }
}
