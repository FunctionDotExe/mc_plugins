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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/** Disciplined line-infantry pike: a pinning thrust that roots whatever it skewers, and a thrown javelin for reach. */
public final class LegionnairesPike extends Weapon {

    private static final Color IRON_COLOR = Color.fromRGB(200, 200, 210);

    private final double thrustDamage;
    private final double thrustRange;
    private final int rootDurationTicks;
    private final int rootSlowAmplifier;
    private final double javelinDamage;
    private final double javelinSpeed;

    public LegionnairesPike(WeaponsPlugin plugin) {
        super(plugin);
        this.thrustDamage = configDouble("thrust-damage", 4.5);
        this.thrustRange = configDouble("thrust-range", 4.0);
        this.rootDurationTicks = configInt("root-duration-ticks", 25);
        this.rootSlowAmplifier = configInt("root-slow-amplifier", 6);
        this.javelinDamage = configDouble("javelin-damage", 5.0);
        this.javelinSpeed = configDouble("javelin-speed", 2.4);
    }

    @Override
    public String id() {
        return "legionnaires_pike";
    }

    @Override
    public Material material() {
        return Material.TRIDENT;
    }

    @Override
    public String displayNameText() {
        return "Legionnaire's Pike";
    }

    @Override
    public Rarity rarity() {
        return Rarity.COMMON;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 4.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: thrust forward, damaging", NamedTextColor.GRAY),
                Component.text("and pinning enemies in your path", NamedTextColor.GRAY),
                Component.text("in place.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Pin Thrust";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: hurl the pike", NamedTextColor.GRAY),
                Component.text("as a javelin, skewering the first", NamedTextColor.GRAY),
                Component.text("enemy it strikes.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Javelin Toss";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_THROW;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_CRIT;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_ARMOR_EQUIP_IRON;
    }

    @Override
    public void ability1(Player player) {
        double damage = thrustDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.9f);
        Fx.line(origin.clone().add(0, 1, 0), origin.clone().add(direction.clone().multiply(thrustRange)).add(0, 1, 0), Particle.CRIT, 12);
        Fx.coloredBurst(origin.clone().add(direction.clone().multiply(1.5)).add(0, 1, 0), IRON_COLOR, 1.2f, 14, 0.5);

        for (Entity entity : world.getNearbyEntities(origin, thrustRange, thrustRange, thrustRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector());
            toEntity.setY(0);
            if (toEntity.lengthSquared() < 1.0e-4 || direction.clone().setY(0).normalize().dot(toEntity.normalize()) < 0.5) {
                continue;
            }
            living.damage(damage, player);
            StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, rootDurationTicks, rootSlowAmplifier);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            Fx.sound(living.getLocation(), hitSound(), 0.9f, 1.0f);
        }
    }

    @Override
    public void ability2(Player player) {
        Fx.sound(player, castSound(), 1.0f, 1.0f);
        Vector velocity = player.getLocation().getDirection().normalize().multiply(javelinSpeed);
        Trident projectile = player.launchProjectile(Trident.class, velocity);
        projectile.setDamage(0);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.setPersistent(false);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        Fx.coloredBurst(projectile.getLocation(), IRON_COLOR, 1.0f, 8, 0.1);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        double damage = javelinDamage * rarity().statMultiplier();

        if (event.getHitEntity() instanceof LivingEntity living && !living.equals(shooter)) {
            living.damage(damage, shooter);
            Fx.coloredBurst(living.getLocation().add(0, 1, 0), IRON_COLOR, 1.2f, 10, 0.25);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            Fx.sound(loc, hitSound(), 0.9f, 1.0f);
        }

        if (event.getHitBlock() != null) {
            Fx.burst(loc, Particle.CRIT, 10, 0.2);
        }
        if (event.getEntity().isValid()) {
            event.getEntity().remove();
        }
    }
}
