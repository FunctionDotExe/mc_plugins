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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/** Siege-weight crossbow: a heavy bolt that detonates in an AoE burst, and an anchor bolt that roots whatever it skewers. */
public final class BallistaCrossbow extends Weapon {

    private static final Color IRON_COLOR = Color.fromRGB(120, 110, 100);
    private static final Color ANCHOR_COLOR = Color.fromRGB(150, 150, 160);

    private final double meleeDamageBonus;
    private final double boltSpeed;
    private final double siegeDamage;
    private final double siegeRadius;
    private final double anchorDamage;
    private final int anchorSlowAmplifier;
    private final int anchorDurationTicks;

    public BallistaCrossbow(WeaponsPlugin plugin) {
        super(plugin);
        this.meleeDamageBonus = configDouble("melee-damage-bonus", 1.5);
        this.boltSpeed = configDouble("bolt-speed", 2.2);
        this.siegeDamage = configDouble("siege-damage", 10.0);
        this.siegeRadius = configDouble("siege-radius", 3.5);
        this.anchorDamage = configDouble("anchor-damage", 6.0);
        this.anchorSlowAmplifier = configInt("anchor-slow-amplifier", 9);
        this.anchorDurationTicks = configInt("anchor-duration-ticks", 60);
    }

    @Override
    public String id() {
        return "ballista_crossbow";
    }

    @Override
    public Material material() {
        return Material.CROSSBOW;
    }

    @Override
    public String displayNameText() {
        return "Ballista Crossbow";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return meleeDamageBonus;
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 10.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: loose a siege bolt that", NamedTextColor.GRAY),
                Component.text("detonates on impact, blasting every", NamedTextColor.GRAY),
                Component.text("enemy caught in the burst.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Siege Bolt";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: fire an anchor", NamedTextColor.GRAY),
                Component.text("bolt that skewers its target to", NamedTextColor.GRAY),
                Component.text("the ground, rooting it in place.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Anchor Bolt";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_CROSSBOW_SHOOT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_CROSSBOW_LOADING_END;
    }

    private NamespacedKey anchorKey() {
        return new NamespacedKey(plugin, "ballista_anchor");
    }

    private Arrow launchBolt(Player player, boolean anchor) {
        Arrow projectile = player.launchProjectile(Arrow.class, player.getLocation().getDirection().multiply(boltSpeed));
        projectile.setDamage(0);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.setPersistent(false);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        if (anchor) {
            projectile.getPersistentDataContainer().set(anchorKey(), PersistentDataType.BYTE, (byte) 1);
        }
        return projectile;
    }

    @Override
    public void ability1(Player player) {
        Fx.sound(player, castSound(), 1.0f, 0.8f);
        Arrow projectile = launchBolt(player, false);
        Fx.coloredBurst(projectile.getLocation(), IRON_COLOR, 1.2f, 8, 0.1);
    }

    @Override
    public void ability2(Player player) {
        Fx.sound(player, castSound(), 1.0f, 1.2f);
        Arrow projectile = launchBolt(player, true);
        Fx.coloredBurst(projectile.getLocation(), ANCHOR_COLOR, 1.0f, 6, 0.1);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        boolean anchor = event.getEntity().getPersistentDataContainer()
                .getOrDefault(anchorKey(), PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        World world = loc.getWorld();

        if (anchor) {
            double damage = anchorDamage * rarity().statMultiplier();
            if (event.getHitEntity() instanceof LivingEntity living && !living.equals(shooter)) {
                living.damage(damage, shooter);
                StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, anchorDurationTicks, anchorSlowAmplifier);
                Fx.coloredBurst(living.getLocation().add(0, 1, 0), IRON_COLOR, 1.3f, 12, 0.3);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                Fx.sound(loc, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
            }
        } else {
            double damage = siegeDamage * rarity().statMultiplier();
            Fx.burst(loc, Particle.EXPLOSION, 1, 0.0);
            Fx.expandingRings(plugin, loc, Particle.CLOUD, siegeRadius, 3, 2L);
            Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);

            if (world != null) {
                for (Entity nearby : world.getNearbyEntities(loc, siegeRadius, siegeRadius, siegeRadius)) {
                    if (!(nearby instanceof LivingEntity living) || living.equals(shooter)) {
                        continue;
                    }
                    double distance = living.getLocation().distance(loc);
                    double falloff = Math.max(0.2, 1.0 - distance / siegeRadius);
                    living.damage(damage * falloff, shooter);
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    Vector away = living.getLocation().toVector().subtract(loc.toVector());
                    if (away.lengthSquared() < 0.01) {
                        away = new Vector(0, 1, 0);
                    }
                    living.setVelocity(living.getVelocity().add(away.normalize().multiply(0.6).setY(0.3)));
                }
            }
        }

        if (event.getEntity().isValid()) {
            event.getEntity().remove();
        }
    }
}
