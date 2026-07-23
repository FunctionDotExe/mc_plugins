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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * A judgment-themed smash weapon: a self-contained leap-and-slam in place of the vanilla mace's
 * fall-damage gimmick, and a single-target execute ultimate that finishes off anything already
 * bled low — mercy, in the Blasphemous sense of the word.
 */
public final class DuskfallMace extends Weapon {

    private static final Color DUSK_COLOR = Color.fromRGB(80, 20, 45);

    private final double leapSlamDamage;
    private final double slamRadius;
    private final double executeRange;
    private final double executeThresholdFraction;
    private final double executeBonusFraction;

    public DuskfallMace(WeaponsPlugin plugin) {
        super(plugin);
        this.leapSlamDamage = configDouble("leap-slam-damage", 8.0);
        this.slamRadius = configDouble("slam-radius", 3.5);
        this.executeRange = configDouble("execute-range", 12.0);
        this.executeThresholdFraction = configDouble("execute-threshold-fraction", 0.3);
        this.executeBonusFraction = configDouble("execute-bonus-fraction", 0.4);
    }

    @Override
    public String id() {
        return "duskfall_mace";
    }

    @Override
    public Material material() {
        return Material.MACE;
    }

    @Override
    public String displayNameText() {
        return "Duskfall Mace";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 4.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 8.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 20.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: leap up, then slam", NamedTextColor.GRAY),
                Component.text("back down, crushing everything", NamedTextColor.GRAY),
                Component.text("beneath you on landing.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Guillotine Drop";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: judge the enemy", NamedTextColor.GRAY),
                Component.text("you're looking at. Below 30% HP,", NamedTextColor.GRAY),
                Component.text("it's executed outright; otherwise", NamedTextColor.GRAY),
                Component.text("it takes bonus damage for its sins.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Last Rites";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(0.4).setY(1.1));
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Fx.point(player.getLocation(), Particle.SMOKE, 3);
                if ((ticks > 3 && player.isOnGround()) || ticks >= 30) {
                    cancel();
                    slam(player);
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void slam(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        double effectiveDamage = leapSlamDamage * rarity().statMultiplier();
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        Fx.expandingRings(plugin, loc, Particle.SMOKE, slamRadius * 1.2, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 0.2, 0), DUSK_COLOR, 2.2f, 40, 1.2);
        Fx.blockBurst(loc, Material.BLACKSTONE, 24, 0.9);

        for (Entity nearby : world.getNearbyEntities(loc, slamRadius, slamRadius, slamRadius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            entity.damage(effectiveDamage, player);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
            Fx.sound(entity.getLocation(), hitSound(), 0.6f, 1.4f);

            Vector away = entity.getLocation().toVector().subtract(loc.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            entity.setVelocity(away.normalize().multiply(1.1).setY(0.5));
        }
    }

    @Override
    public void ultimate(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), executeRange,
                entity -> entity instanceof LivingEntity && !entity.equals(player));

        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            Fx.sound(player, Sound.BLOCK_LEVER_CLICK, 0.7f, 0.8f);
            return;
        }

        Location targetLoc = target.getLocation().add(0, 1, 0);
        Fx.line(eye, targetLoc, Particle.SQUID_INK, 20);
        Fx.coloredBurst(targetLoc, DUSK_COLOR, 1.8f, 20, 0.4);

        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthFraction = target.getHealth() / maxHealth;

        if (healthFraction <= executeThresholdFraction) {
            Fx.sound(target.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 1.6f);
            Fx.coloredBurst(targetLoc, DUSK_COLOR, 2.4f, 45, 0.8);
            Fx.point(targetLoc, Particle.SOUL, 20);
            target.damage(target.getHealth() + 1.0, player);
        } else {
            double missingHealth = maxHealth - target.getHealth();
            double bonusDamage = missingHealth * executeBonusFraction + effectiveMeleeDamage();
            target.damage(bonusDamage, player);
            Fx.bloodSpray(targetLoc);
            Fx.sound(target.getLocation(), hitSound(), 0.7f, 1.2f);
        }
    }
}
