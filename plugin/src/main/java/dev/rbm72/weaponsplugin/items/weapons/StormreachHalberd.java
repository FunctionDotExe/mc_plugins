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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Polearm: a wide passive cleave on every melee hit, a long forward thrust, and a full-circle sweep. */
public final class StormreachHalberd extends Weapon {

    private static final Color STEEL_BLUE = Color.fromRGB(120, 140, 170);

    private final double sweepRadius;
    private final double sweepDamageFraction;
    private final double thrustRange;
    private final double thrustDamage;
    private final double cleaveRadius;
    private final double cleaveDamage;

    public StormreachHalberd(WeaponsPlugin plugin) {
        super(plugin);
        this.sweepRadius = configDouble("sweep-radius", 2.2);
        this.sweepDamageFraction = configDouble("sweep-damage-fraction", 0.5);
        this.thrustRange = configDouble("thrust-range", 4.5);
        this.thrustDamage = configDouble("thrust-damage", 7.0);
        this.cleaveRadius = configDouble("cleave-radius", 3.5);
        this.cleaveDamage = configDouble("cleave-damage", 6.0);
    }

    @Override
    public String id() {
        return "stormreach_halberd";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_AXE;
    }

    @Override
    public String displayNameText() {
        return "Stormreach Halberd";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.5);
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
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: plant your feet and drive", NamedTextColor.GRAY),
                Component.text("the halberd's full reach through", NamedTextColor.GRAY),
                Component.text("everything standing in the line.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Reach Thrust";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: spin the halberd", NamedTextColor.GRAY),
                Component.text("through a full circle, cleaving", NamedTextColor.GRAY),
                Component.text("anything that strays too close.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Wide Cleave";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_THROW;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_ARMOR_EQUIP_NETHERITE;
    }

    /** Passive: a halberd's reach carries the blow past the primary target into whoever's standing beside them. */
    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        double splashDamage = event.getFinalDamage() * sweepDamageFraction;
        if (splashDamage <= 0) {
            return;
        }
        for (Entity nearby : victim.getNearbyEntities(sweepRadius, sweepRadius, sweepRadius)) {
            if (!(nearby instanceof LivingEntity extra) || extra.equals(victim) || extra.equals(attacker)) {
                continue;
            }
            extra.damage(splashDamage, attacker);
            Fx.bloodSpray(extra.getLocation().add(0, 1, 0));
        }
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), STEEL_BLUE, 1.0f, 8, sweepRadius * 0.3);
    }

    @Override
    public void ability1(Player player) {
        double damage = thrustDamage * rarity().statMultiplier();
        Vector look = player.getLocation().getDirection().normalize();
        player.setVelocity(look.clone().multiply(0.9).setY(0.1));

        Set<UUID> alreadyHit = new HashSet<>();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 6) {
                    cancel();
                    return;
                }
                Location origin = player.getLocation().add(0, 1, 0);
                Vector direction = origin.getDirection().normalize();
                Fx.trail(origin, Particle.CRIT, 12, thrustRange * 0.3, 0.03);
                Fx.coloredBurst(origin.clone().add(direction.clone().multiply(2)), STEEL_BLUE, 1.2f, 10, 0.4);

                for (Entity entity : player.getNearbyEntities(thrustRange, 1.5, thrustRange)) {
                    if (!(entity instanceof LivingEntity target) || !alreadyHit.add(target.getUniqueId())) {
                        continue;
                    }
                    Vector toTarget = target.getLocation().toVector().subtract(origin.toVector()).setY(0).normalize();
                    if (toTarget.dot(direction.clone().setY(0).normalize()) < 0.5) {
                        continue;
                    }
                    target.damage(damage, player);
                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability2(Player player) {
        double damage = cleaveDamage * rarity().statMultiplier();
        Location origin = player.getLocation();

        Fx.ring(origin.clone().add(0, 0.1, 0), Particle.SWEEP_ATTACK, cleaveRadius, 24, 0);
        Fx.coloredBurst(origin.clone().add(0, 1, 0), STEEL_BLUE, 1.6f, 30, cleaveRadius * 0.4);
        Fx.sound(player, hitSound(), 1.2f, 0.9f);

        for (Entity entity : player.getNearbyEntities(cleaveRadius, cleaveRadius, cleaveRadius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) {
                continue;
            }
            target.damage(damage, player);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }
}
