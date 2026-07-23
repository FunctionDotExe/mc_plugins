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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Launches the player forward in a rising tornado arc, briefly launching
 * anything it slashes into the air, then bursts outward on landing.
 */
public final class WindSpear extends Weapon {

    private static final Color GUST_COLOR = Color.fromRGB(225, 225, 230);

    private final double launchSpeed;
    private final double abilityDamage;
    private final double hitRadius;
    private final int flightTicks;

    public WindSpear(WeaponsPlugin plugin) {
        super(plugin);
        this.launchSpeed = configDouble("launch-speed", 2.0);
        this.abilityDamage = configDouble("ability-damage", 4.0);
        this.hitRadius = configDouble("hit-radius", 1.6);
        this.flightTicks = configInt("flight-ticks", 14);
    }

    @Override
    public String id() {
        return "wind_spear";
    }

    @Override
    public Material material() {
        return Material.TRIDENT;
    }

    @Override
    public String displayNameText() {
        return "Wind Spear";
    }

    @Override
    public Rarity rarity() {
        return Rarity.COMMON;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("cooldown-seconds", 5.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: launch forward in a", NamedTextColor.GRAY),
                Component.text("rising tornado, slashing and", NamedTextColor.GRAY),
                Component.text("launching anything in your path,", NamedTextColor.GRAY),
                Component.text("then burst outward on landing.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Cyclone Leap";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_ELYTRA_FLYING;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_PHANTOM_FLAP;
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(launchSpeed).setY(0.6));

        double effectiveDamage = abilityDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= flightTicks) {
                    cancel();
                    if (player.isOnline()) {
                        landingBurst(player);
                    }
                    return;
                }

                Location center = player.getLocation();
                // Four stacked helix layers form a genuine tornado column instead of two
                // thin spiral lines.
                Fx.helixFrame(center, Particle.CLOUD, 0.8, 3, angle, 0.6);
                Fx.helixFrame(center, Particle.CLOUD, 0.8, 3, angle, 0.0);
                Fx.helixFrame(center, Particle.CLOUD, 0.8, 3, angle, -0.6);
                Fx.helixFrame(center, Particle.CLOUD, 0.8, 3, angle, -1.2);
                Fx.coloredBurst(center.clone().add(0, 0.4, 0), GUST_COLOR, 1.6f, 10, 0.8);
                angle += Math.PI / 3;

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
                    StatusEffectManager.apply(entity, PotionEffectType.LEVITATION, 20, 0);
                    Fx.point(entity.getLocation().add(0, 1, 0), Particle.SWEEP_ATTACK, 1);
                    Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                    Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);

                    Vector knockback = direction.clone().multiply(1.0).setY(0.4);
                    entity.setVelocity(entity.getVelocity().add(knockback));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void landingBurst(Player player) {
        Location loc = player.getLocation();
        Fx.expandingRings(plugin, loc, Particle.CLOUD, 5.0, 4, 2L);
        Fx.coloredBurst(loc.clone().add(0, 0.3, 0), GUST_COLOR, 1.7f, 30, 1.2);
        Fx.coloredBurst(loc.clone().add(0, 1.0, 0), GUST_COLOR, 1.3f, 16, 1.0);
        Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);

        double burstDamage = abilityDamage * rarity().statMultiplier() * 0.5;
        for (Entity nearby : player.getNearbyEntities(2.5, 2.5, 2.5)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            entity.damage(burstDamage, player);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));

            Vector away = entity.getLocation().toVector().subtract(loc.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            entity.setVelocity(away.normalize().multiply(0.8).setY(0.3));
        }
    }
}
