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
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Blinks behind the nearest enemy in front of the player and lands a
 * poisoned flurry of hits. If a second enemy is nearby, throws a dart at
 * them too. Blinks forward if nothing's in range, so it's never a dead
 * click.
 */
public final class ShadowDaggers extends Weapon {

    private static final Color SHADOW_PURPLE = Color.fromRGB(35, 0, 55);

    private final double abilityDamage;
    private final double range;
    private final double blinkDistance;
    private final int flurryHits;
    private final int poisonTicks;

    public ShadowDaggers(WeaponsPlugin plugin) {
        super(plugin);
        this.abilityDamage = configDouble("ability-damage", 8.0);
        this.range = configDouble("range", 8.0);
        this.blinkDistance = configDouble("blink-distance", 4.0);
        this.flurryHits = configInt("flurry-hits", 3);
        this.poisonTicks = configInt("poison-ticks", 60);
    }

    @Override
    public String id() {
        return "shadow_daggers";
    }

    @Override
    public Material material() {
        return Material.IRON_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Shadow Daggers";
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
        return configDouble("cooldown-seconds", 7.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: teleport behind the", NamedTextColor.GRAY),
                Component.text("nearest enemy for a poisoned flurry", NamedTextColor.GRAY),
                Component.text("of strikes. Throws a dart at a", NamedTextColor.GRAY),
                Component.text("second enemy if one is nearby.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Umbral Flurry";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDERMAN_TELEPORT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_CRIT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_SCULK_CATALYST_BLOOM;
    }

    @Override
    public void ability1(Player player) {
        Location origin = player.getLocation();
        LivingEntity primary = findTarget(player, null);

        Fx.burst(origin.clone().add(0, 1, 0), Particle.SQUID_INK, 26, 0.45);
        Fx.coloredBurst(origin.clone().add(0, 1, 0), SHADOW_PURPLE, 1.4f, 22, 0.45);

        double effectiveDamage = abilityDamage * rarity().statMultiplier();

        if (primary != null) {
            teleportBehind(player, primary);
            flurry(player, primary, effectiveDamage);

            LivingEntity secondary = findTarget(player, primary);
            if (secondary != null) {
                throwDart(player, secondary, effectiveDamage * 0.5);
            }
        } else {
            Location destination = origin.clone().add(origin.getDirection().normalize().multiply(blinkDistance));
            if (isSafe(destination)) {
                destination.setDirection(origin.getDirection());
                player.teleport(destination);
            }
        }

        Fx.burst(player.getLocation().add(0, 1, 0), Particle.SQUID_INK, 26, 0.45);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), SHADOW_PURPLE, 1.4f, 22, 0.45);
        Fx.spinningIcon(plugin, player.getLocation().add(0, 1.6, 0), Material.OBSIDIAN, 1.0f, 15, 20.0);
    }

    private void teleportBehind(Player player, LivingEntity target) {
        Vector behind = target.getLocation().getDirection().normalize().multiply(-1.5);
        Location destination = target.getLocation().add(behind);
        destination.setDirection(target.getLocation().toVector().subtract(destination.toVector()));
        if (isSafe(destination)) {
            player.teleport(destination);
        }
    }

    private void flurry(Player player, LivingEntity target, double totalDamage) {
        new BukkitRunnable() {
            int hit = 0;

            @Override
            public void run() {
                if (hit >= flurryHits || !target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }

                target.damage(totalDamage / flurryHits, player);
                StatusEffectManager.apply(target, PotionEffectType.POISON, poisonTicks, 0);
                Fx.point(target.getLocation().add(0, 1, 0), Particle.CRIT, 18);
                Fx.point(target.getLocation().add(0, 1, 0), Particle.SQUID_INK, 10);
                Fx.coloredBurst(target.getLocation().add(0, 1, 0), SHADOW_PURPLE, 1.3f, 16, 0.4);
                Fx.bloodSpray(target.getLocation().add(0, 1.2, 0));
                Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f + hit * 0.15f);
                hit++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void throwDart(Player player, LivingEntity target, double dartDamage) {
        Location from = player.getLocation().add(0, 1, 0);
        // A real thrown dagger prop flies to the second target instead of a static particle line.
        ItemDisplay icon = Fx.spinningIcon(plugin, from, Material.IRON_SWORD, 0.5f, 6, 60.0);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || ticks >= 3) {
                    if (icon != null && !icon.isDead()) {
                        Location to = target.isValid() ? target.getLocation().add(0, 1, 0) : from;
                        icon.remove();
                        if (target.isValid() && !target.isDead()) {
                            Fx.coloredBurst(to, SHADOW_PURPLE, 1.2f, 16, 0.3);
                            Fx.sound(to, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.4f);
                            target.damage(dartDamage, player);
                            Fx.bloodSpray(to);
                        }
                    }
                    cancel();
                    return;
                }
                Location step = from.clone().add(
                        target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector())
                                .multiply((ticks + 1) / 3.0));
                if (icon != null && !icon.isDead()) {
                    icon.teleport(step);
                }
                Fx.point(step, Particle.CRIT, 6);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private LivingEntity findTarget(Player player, LivingEntity exclude) {
        Vector look = player.getLocation().getDirection().normalize();
        LivingEntity best = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (Entity nearby : player.getNearbyEntities(range, range, range)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (exclude != null && entity.getUniqueId().equals(exclude.getUniqueId())) {
                continue;
            }
            Vector toEntity = entity.getLocation().toVector().subtract(player.getLocation().toVector());
            double distanceSquared = toEntity.lengthSquared();
            if (distanceSquared < 0.01) {
                continue;
            }
            double alignment = toEntity.normalize().dot(look);
            if (alignment < 0.6) {
                continue;
            }
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = entity;
            }
        }
        return best;
    }

    private boolean isSafe(Location destination) {
        return !destination.getBlock().getType().isSolid()
                && !destination.clone().add(0, 1, 0).getBlock().getType().isSolid();
    }
}
