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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Moon-themed sword: crescent projectile, moon dash, gravity field, and an Eclipse self-buff ultimate. */
public final class LunarBlade extends Weapon {

    private final double nightDamageBonus;
    private final double crescentDamage;
    private final double crescentSpeed;
    private final double dashDamage;
    private final double dashDistance;
    private final double gravityDamage;
    private final double gravityRadius;
    private final int gravityDurationTicks;
    private final int eclipseDurationTicks;
    private final double eclipseDamageBonus;

    public LunarBlade(WeaponsPlugin plugin) {
        super(plugin);
        this.nightDamageBonus = configDouble("night-damage-bonus", 0.3);
        this.crescentDamage = configDouble("crescent-damage", 5.0);
        this.crescentSpeed = configDouble("crescent-speed", 1.8);
        this.dashDamage = configDouble("dash-damage", 5.0);
        this.dashDistance = configDouble("dash-distance", 5.0);
        this.gravityDamage = configDouble("gravity-damage", 4.0);
        this.gravityRadius = configDouble("gravity-radius", 4.0);
        this.gravityDurationTicks = configInt("gravity-duration-ticks", 30);
        this.eclipseDurationTicks = configInt("eclipse-duration-ticks", 160);
        this.eclipseDamageBonus = configDouble("eclipse-damage-bonus", 0.4);
    }

    private final Map<UUID, Long> eclipseActiveUntilMs = new HashMap<>();

    @Override
    public String id() {
        return "lunar_blade";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Lunar Blade";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: fling a crescent blade", NamedTextColor.GRAY),
                Component.text("of moonlight that carves through", NamedTextColor.GRAY),
                Component.text("whatever it crosses.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Crescent Slash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: dash forward,", NamedTextColor.GRAY),
                Component.text("damaging enemies you pass through.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Moon Dash";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: call the moon's pull,", NamedTextColor.GRAY),
                Component.text("dragging nearby enemies down like the tide.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Tidal Pull";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: enter an Eclipse,", NamedTextColor.GRAY),
                Component.text("boosting your damage and speed.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Eclipse";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_PHANTOM_FLAP;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_AMETHYST_BLOCK_CHIME;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        double multiplier = 1.0;
        if (!attacker.getWorld().isDayTime()) {
            multiplier += nightDamageBonus;
        }
        if (System.currentTimeMillis() < eclipseActiveUntilMs.getOrDefault(attacker.getUniqueId(), 0L)) {
            multiplier += eclipseDamageBonus;
        }
        if (multiplier > 1.0) {
            event.setDamage(event.getDamage() * multiplier);
            Fx.point(victim.getLocation().add(0, 1.5, 0), Particle.END_ROD, 3);
            Fx.coloredBurst(victim.getLocation().add(0, 1.5, 0), Color.fromRGB(190, 205, 255), 0.9f, 8, 0.3);
        }
    }

    @Override
    public void ability1(Player player) {
        Snowball projectile = player.launchProjectile(Snowball.class,
                player.getLocation().getDirection().multiply(crescentSpeed));
        projectile.setGravity(false);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());

        var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.DIAMOND_SWORD, 0.8f, 60, 26.0);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!projectile.isValid()) {
                    if (icon != null && !icon.isDead()) {
                        icon.remove();
                    }
                    cancel();
                    return;
                }
                Fx.point(projectile.getLocation(), Particle.END_ROD, 2);
                Fx.point(projectile.getLocation(), Particle.GLOW, 1);
                if (ticks % 2 == 0) {
                    Fx.coloredBurst(projectile.getLocation(), Color.fromRGB(215, 225, 255), 0.8f, 3, 0.08);
                }
                if (icon != null && !icon.isDead()) {
                    icon.teleport(projectile.getLocation());
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        double damage = crescentDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.END_ROD, 15, 0.4);
        Fx.coloredBurst(loc, Color.fromRGB(215, 225, 255), 1.1f, 18, 0.4);
        Fx.sound(loc, hitSound(), 0.8f, 1.4f);

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = dashDamage * rarity().statMultiplier();
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        Location end = start.clone().add(direction.clone().multiply(dashDistance));
        World world = start.getWorld();
        if (world == null) {
            return;
        }

        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        for (double offset : new double[] {-0.6, 0.0, 0.6}) {
            Vector shift = perpendicular.clone().multiply(offset);
            Fx.line(start.clone().add(0, 1, 0).add(shift), end.clone().add(0, 1, 0).add(shift), Particle.END_ROD, 15);
        }
        Fx.coloredBurst(start.clone().add(0, 1, 0), Color.fromRGB(215, 225, 255), 1.0f, 12, 0.3);
        for (Entity entity : world.getNearbyEntities(start, dashDistance, 2, dashDistance)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(start.toVector());
            if (direction.dot(toEntity.clone().normalize()) < 0.6 || toEntity.length() > dashDistance) {
                continue;
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }

        Location safeEnd = end.getBlock().getType().isSolid() ? start : end;
        player.teleport(safeEnd.setDirection(direction));
        Fx.burst(safeEnd, Particle.END_ROD, 20, 0.3);
        Fx.coloredBurst(safeEnd, Color.fromRGB(215, 225, 255), 1.1f, 16, 0.35);
    }

    @Override
    public void ability3(Player player) {
        double damage = gravityDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= gravityDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                angle += Math.PI / 10;
                Fx.ring(center, Particle.REVERSE_PORTAL, gravityRadius, 30, angle);
                Fx.ring(center.clone().add(0, 0.1, 0), Particle.REVERSE_PORTAL, gravityRadius * 0.7, 24, -angle);
                double helixHeight = 2.0 * (1.0 - (double) ticks / gravityDurationTicks);
                Fx.helixFrame(center, Particle.REVERSE_PORTAL, gravityRadius * 0.6, 10, angle * 1.5, helixHeight);

                for (Entity entity : world.getNearbyEntities(center, gravityRadius, gravityRadius, gravityRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    living.setVelocity(living.getVelocity().setY(-0.3));
                    if (ticks % 10 == 0) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        eclipseActiveUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + (eclipseDurationTicks * 50L));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, eclipseDurationTicks, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, eclipseDurationTicks, 1));
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.5f);
        Fx.spinningIcon(plugin, player.getLocation().add(0, 2.2, 0), Material.ENDER_EYE, 1.2f, eclipseDurationTicks, 14);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= eclipseDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                angle += Math.PI / 6;
                Fx.ring(player.getLocation(), Particle.SOUL_FIRE_FLAME, 1.2, 24, angle);
                Fx.ring(player.getLocation().clone().add(0, 0.05, 0), Particle.SOUL_FIRE_FLAME, 1.6, 28, -angle);
                if (ticks % 5 == 0) {
                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(150, 170, 255), 2.2f, 24, 0.7);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
