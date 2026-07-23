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

import java.util.List;

/**
 * Guaranteed drop from The Fallen King: a heavy combo greatsword that
 * mirrors the boss's own Shockwave Slam — a leap-slam AoE — as its ability,
 * on the existing single-ability weapon framework.
 */
public final class KingsJudgment extends Weapon {

    private final double abilityDamage;
    private final double radius;
    private final double knockup;

    public KingsJudgment(WeaponsPlugin plugin) {
        super(plugin);
        this.abilityDamage = configDouble("ability-damage", 12.0);
        this.radius = configDouble("radius", 4.0);
        this.knockup = configDouble("knockup", 0.6);
    }

    @Override
    public String id() {
        return "kings_judgment";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "King's Judgment";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 4.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("cooldown-seconds", 9.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: slam the ground with", NamedTextColor.GRAY),
                Component.text("the fallen king's own judgment,", NamedTextColor.GRAY),
                Component.text("shattering everything nearby.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Shockwave Slam";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_MACE_SMASH_GROUND_HEAVY;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_IRON_GOLEM_ATTACK;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    @Override
    public void ability1(Player player) {
        double effectiveDamage = abilityDamage * rarity().statMultiplier();
        Location center = player.getLocation();

        Fx.expandingRings(plugin, center, Particle.CRIT, radius * 1.3, 5, 3L);
        Fx.coloredBurst(center.clone().add(0, 1, 0), Color.fromRGB(255, 205, 40), 2.4f, 70, radius * 0.9);
        Fx.sound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.7f);
        Fx.glowPillar(plugin, center, Material.GOLD_BLOCK, 0.6f, 9f, 30);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= 24) {
                    cancel();
                    return;
                }
                angle += Math.PI / 8;
                Fx.ring(center, Particle.END_ROD, radius - 0.4, 40, angle);
                Fx.ring(center, Particle.END_ROD, radius, 40, angle);
                Fx.ring(center, Particle.END_ROD, radius + 0.4, 40, angle);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            entity.damage(effectiveDamage, player);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
            entity.setVelocity(entity.getVelocity().add(new Vector(0, knockup, 0)));
        }
    }
}
