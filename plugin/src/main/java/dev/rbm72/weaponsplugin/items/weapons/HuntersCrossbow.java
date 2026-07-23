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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Hunting crossbow: a heavy piercing bolt that punches through a whole line of enemies, and a rapid three-bolt volley. */
public final class HuntersCrossbow extends Weapon {

    private static final Color STEEL = Color.fromRGB(180, 180, 190);

    private final double meleeDamageBonus;
    private final double boltSpeed;
    private final double boltDamage;
    private final int boltPierceLevel;
    private final int volleyCount;
    private final int volleyDelayTicks;
    private final double volleyDamage;

    public HuntersCrossbow(WeaponsPlugin plugin) {
        super(plugin);
        this.meleeDamageBonus = configDouble("melee-damage-bonus", 1.0);
        this.boltSpeed = configDouble("bolt-speed", 2.6);
        this.boltDamage = configDouble("bolt-damage", 7.0);
        this.boltPierceLevel = configInt("bolt-pierce-level", 6);
        this.volleyCount = configInt("volley-count", 3);
        this.volleyDelayTicks = configInt("volley-delay-ticks", 4);
        this.volleyDamage = configDouble("volley-damage", 4.0);
    }

    @Override
    public String id() {
        return "hunters_crossbow";
    }

    @Override
    public Material material() {
        return Material.CROSSBOW;
    }

    @Override
    public String displayNameText() {
        return "Hunter's Crossbow";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return meleeDamageBonus;
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
                Component.text("Right-click: loose a heavy broadhead", NamedTextColor.GRAY),
                Component.text("bolt that punches clean through", NamedTextColor.GRAY),
                Component.text("every enemy standing in its path.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Piercing Bolt";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: crank off a rapid", NamedTextColor.GRAY),
                Component.text("burst of three piercing bolts faster", NamedTextColor.GRAY),
                Component.text("than the string should allow.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Rapid Volley";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_CROSSBOW_SHOOT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_ARROW_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_CROSSBOW_LOADING_END;
    }

    private Arrow launchBolt(Player player, Vector velocity) {
        Arrow projectile = player.launchProjectile(Arrow.class, velocity);
        projectile.setDamage(0);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.setPersistent(false);
        projectile.setPierceLevel(boltPierceLevel);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        return projectile;
    }

    @Override
    public void ability1(Player player) {
        Fx.sound(player, castSound(), 1.0f, 1.0f);
        Arrow projectile = launchBolt(player, player.getLocation().getDirection().multiply(boltSpeed));
        Fx.coloredBurst(projectile.getLocation(), STEEL, 1.0f, 6, 0.1);
    }

    @Override
    public void ability2(Player player) {
        Fx.sound(player, castSound(), 1.0f, 1.15f);
        Vector direction = player.getLocation().getDirection().multiply(boltSpeed);

        new BukkitRunnable() {
            int shot = 0;

            @Override
            public void run() {
                if (shot >= volleyCount || !player.isOnline()) {
                    cancel();
                    return;
                }
                Arrow projectile = launchBolt(player, direction.clone());
                projectile.getPersistentDataContainer().set(volleyKey(), PersistentDataType.BYTE, (byte) 1);
                Fx.coloredBurst(projectile.getLocation(), STEEL, 1.0f, 6, 0.1);
                shot++;
            }
        }.runTaskTimer(plugin, 0L, volleyDelayTicks);
    }

    private NamespacedKey volleyKey() {
        return new NamespacedKey(plugin, "hunters_crossbow_volley");
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        boolean volleyShot = event.getEntity().getPersistentDataContainer()
                .getOrDefault(volleyKey(), PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        double damage = (volleyShot ? volleyDamage : boltDamage) * rarity().statMultiplier();

        if (event.getHitEntity() instanceof LivingEntity living && !living.equals(shooter)) {
            living.damage(damage, shooter);
            Fx.coloredBurst(living.getLocation().add(0, 1, 0), STEEL, 1.2f, 10, 0.25);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            Fx.sound(loc, hitSound(), 0.9f, 1.0f);
        }

        if (event.getHitBlock() != null) {
            Fx.burst(loc, Particle.CRIT, 10, 0.2);
            if (event.getEntity().isValid()) {
                event.getEntity().remove();
            }
        }
    }
}
