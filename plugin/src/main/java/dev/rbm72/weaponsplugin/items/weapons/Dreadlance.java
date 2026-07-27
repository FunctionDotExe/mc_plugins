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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Bleed-focused polearm: a piercing line skewer that opens a wound, and a follow-up lunge that erupts bonus damage on anything still bleeding. */
public final class Dreadlance extends Weapon {

    private static final Color BLOOD_COLOR = Color.fromRGB(150, 10, 20);

    private final double skewerDamage;
    private final double skewerRange;
    private final double bleedDamagePerTick;
    private final int bleedIntervalTicks;
    private final int bleedTotalTicks;
    private final double lungeSpeed;
    private final double lungeDamage;
    private final double lungeRadius;
    private final double bleedBonusMultiplier;

    public Dreadlance(WeaponsPlugin plugin) {
        super(plugin);
        this.skewerDamage = configDouble("skewer-damage", 5.0);
        this.skewerRange = configDouble("skewer-range", 4.5);
        this.bleedDamagePerTick = configDouble("bleed-damage-per-tick", 1.0);
        this.bleedIntervalTicks = configInt("bleed-interval-ticks", 10);
        this.bleedTotalTicks = configInt("bleed-total-ticks", 40);
        this.lungeSpeed = configDouble("lunge-speed", 1.8);
        this.lungeDamage = configDouble("lunge-damage", 5.0);
        this.lungeRadius = configDouble("lunge-radius", 2.0);
        this.bleedBonusMultiplier = configDouble("bleed-bonus-multiplier", 1.8);
    }

    @Override
    public String id() {
        return "dreadlance";
    }

    @Override
    public Material material() {
        return Material.TRIDENT;
    }

    @Override
    public String displayNameText() {
        return "Dreadlance";
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
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 7.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: skewer everything in a", NamedTextColor.GRAY),
                Component.text("line ahead of you, opening a bleeding", NamedTextColor.GRAY),
                Component.text("wound that festers over time.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Skewer";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: lunge forward,", NamedTextColor.GRAY),
                Component.text("dealing bonus damage and consuming", NamedTextColor.GRAY),
                Component.text("the bleed on anything you strike.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Execution Lunge";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_TRIDENT_RIPTIDE_1;
    }

    private NamespacedKey bleedKey() {
        return new NamespacedKey(plugin, "dreadlance_bleeding");
    }

    private void applyBleed(Player attacker, LivingEntity victim) {
        double tickDamage = bleedDamagePerTick * rarity().statMultiplier();
        victim.getPersistentDataContainer().set(bleedKey(), PersistentDataType.BYTE, (byte) 1);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= bleedTotalTicks || victim.isDead() || !victim.isValid()) {
                    if (victim.isValid()) {
                        victim.getPersistentDataContainer().remove(bleedKey());
                    }
                    cancel();
                    return;
                }
                victim.damage(tickDamage, attacker);
                Fx.coloredBurst(victim.getLocation().add(0, 1, 0), BLOOD_COLOR, 0.8f, 4, 0.2);
                elapsed += bleedIntervalTicks;
            }
        }.runTaskTimer(plugin, bleedIntervalTicks, bleedIntervalTicks);
    }

    @Override
    public void ability1(Player player) {
        double damage = skewerDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, castSound(), 1.0f, 0.8f);
        Fx.line(origin.clone().add(0, 1, 0), origin.clone().add(direction.clone().multiply(skewerRange)).add(0, 1, 0), Particle.CRIT, 14);
        Fx.coloredBurst(origin.clone().add(direction.clone().multiply(2.0)).add(0, 1, 0), BLOOD_COLOR, 1.3f, 16, 0.6);

        for (Entity entity : world.getNearbyEntities(origin, skewerRange, skewerRange, skewerRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector());
            toEntity.setY(0);
            if (toEntity.lengthSquared() < 1.0e-4 || direction.clone().setY(0).normalize().dot(toEntity.normalize()) < 0.5) {
                continue;
            }
            living.damage(damage, player);
            applyBleed(player, living);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            Fx.sound(living.getLocation(), hitSound(), 0.9f, 1.0f);
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = lungeDamage * rarity().statMultiplier();
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(lungeSpeed).setY(0.3));
        Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.1f);
        Fx.trail(player.getLocation().add(0, 1, 0), Particle.CRIT, 20, 0.4, 0.05);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 8) {
                    cancel();
                    return;
                }
                for (Entity nearby : player.getNearbyEntities(lungeRadius, lungeRadius, lungeRadius)) {
                    if (!(nearby instanceof LivingEntity living) || living.equals(player)) {
                        continue;
                    }
                    boolean bleeding = living.getPersistentDataContainer()
                            .getOrDefault(bleedKey(), PersistentDataType.BYTE, (byte) 0) == (byte) 1;
                    double finalDamage = bleeding ? damage * bleedBonusMultiplier : damage;
                    living.damage(finalDamage, player);
                    if (bleeding) {
                        living.getPersistentDataContainer().remove(bleedKey());
                        Fx.coloredBurst(living.getLocation().add(0, 1, 0), BLOOD_COLOR, 1.6f, 24, 0.5);
                    }
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    Fx.sound(living.getLocation(), hitSound(), 1.0f, 0.9f);
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
