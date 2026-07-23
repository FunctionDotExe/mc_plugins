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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Throwing spear: every ability hurls a poison-tipped javelin instead of swinging it — a single heavy throw, or a quick two-javelin volley. */
public final class VenomtipJavelin extends Weapon {

    private static final Color VENOM_GREEN = Color.fromRGB(80, 160, 60);

    private final double throwSpeed;
    private final double throwDamage;
    private final int poisonDurationTicks;
    private final int poisonAmplifier;
    private final double volleyDamage;

    public VenomtipJavelin(WeaponsPlugin plugin) {
        super(plugin);
        this.throwSpeed = configDouble("throw-speed", 1.8);
        this.throwDamage = configDouble("throw-damage", 8.0);
        this.poisonDurationTicks = configInt("poison-duration-ticks", 100);
        this.poisonAmplifier = configInt("poison-amplifier", 1);
        this.volleyDamage = configDouble("volley-damage", 5.0);
    }

    private NamespacedKey shotKey() {
        return new NamespacedKey(plugin, "venomtip_javelin_shot");
    }

    @Override
    public String id() {
        return "venomtip_javelin";
    }

    @Override
    public Material material() {
        return Material.BAMBOO;
    }

    @Override
    public String displayNameText() {
        return "Venomtip Javelin";
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
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: hurl a venom-slicked", NamedTextColor.GRAY),
                Component.text("javelin that drops your target", NamedTextColor.GRAY),
                Component.text("in the poison it leaves behind.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Throw Javelin";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: snap off a quick", NamedTextColor.GRAY),
                Component.text("two-javelin volley before the venom", NamedTextColor.GRAY),
                Component.text("even has time to set in.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Javelin Volley";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_THROW;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_SPIDER_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_ARROW_SHOOT;
    }

    private Snowball launchTagged(Player player, Vector velocity, boolean volleyShot) {
        Snowball projectile = player.launchProjectile(Snowball.class, velocity);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        projectile.getPersistentDataContainer().set(shotKey(), PersistentDataType.BYTE, (byte) (volleyShot ? 1 : 0));

        var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.BAMBOO, 0.7f, 40, 20.0);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid()) {
                    if (icon != null && !icon.isDead()) {
                        icon.remove();
                    }
                    cancel();
                    return;
                }
                Fx.point(projectile.getLocation(), Particle.WITCH, 2);
                Fx.coloredBurst(projectile.getLocation(), VENOM_GREEN, 0.7f, 2, 0.05);
                if (icon != null && !icon.isDead()) {
                    icon.teleport(projectile.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return projectile;
    }

    @Override
    public void ability1(Player player) {
        launchTagged(player, player.getLocation().getDirection().multiply(throwSpeed), false);
    }

    @Override
    public void ability2(Player player) {
        Vector base = player.getLocation().getDirection().normalize();
        for (int i = 0; i < 2; i++) {
            double offset = (i - 0.5) * 0.15;
            Vector direction = rotateY(base, offset).multiply(throwSpeed * 0.95);
            launchTagged(player, direction, true);
        }
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), VENOM_GREEN, 1.2f, 12, 0.35);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        boolean volleyShot = event.getEntity().getPersistentDataContainer()
                .getOrDefault(shotKey(), PersistentDataType.BYTE, (byte) 0) == 1;
        double damage = (volleyShot ? volleyDamage : throwDamage) * rarity().statMultiplier();

        Fx.coloredBurst(loc, VENOM_GREEN, 1.4f, 16, 0.3);
        Fx.sound(loc, hitSound(), 0.9f, 1.1f);

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                    volleyShot ? poisonDurationTicks / 2 : poisonDurationTicks, poisonAmplifier));
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
        if (!event.getEntity().isDead()) {
            event.getEntity().remove();
        }
    }

    private static Vector rotateY(Vector v, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
