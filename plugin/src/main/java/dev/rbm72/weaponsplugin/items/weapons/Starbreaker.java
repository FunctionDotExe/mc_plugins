package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Endgame legendary sword: star spread, warp strike, supernova, and a galaxy-collapse ultimate. Chaining different abilities buffs the next one. */
public final class Starbreaker extends Weapon {

    private static final Color STARLIGHT_VIOLET = Color.fromRGB(150, 80, 255);

    private final long momentumWindowMs;
    private final double momentumDamageBonus;
    private final int spreadCount;
    private final double spreadDamage;
    private final double spreadSpeed;
    private final double spreadSpreadDegrees;
    private final double warpRange;
    private final double warpDamage;
    private final double supernovaDamage;
    private final double supernovaRadius;
    private final double galaxyPullRadius;
    private final int galaxyPullDurationTicks;
    private final double galaxyExplosionDamage;
    private final double galaxyExplosionRadius;

    public Starbreaker(WeaponsPlugin plugin) {
        super(plugin);
        this.momentumWindowMs = configInt("momentum-window-ms", 3000);
        this.momentumDamageBonus = configDouble("momentum-damage-bonus", 0.35);
        this.spreadCount = configInt("spread-count", 5);
        this.spreadDamage = configDouble("spread-damage", 4.0);
        this.spreadSpeed = configDouble("spread-speed", 1.8);
        this.spreadSpreadDegrees = configDouble("spread-spread-degrees", 10.0);
        this.warpRange = configDouble("warp-range", 10.0);
        this.warpDamage = configDouble("warp-damage", 9.0);
        this.supernovaDamage = configDouble("supernova-damage", 8.0);
        this.supernovaRadius = configDouble("supernova-radius", 4.5);
        this.galaxyPullRadius = configDouble("galaxy-pull-radius", 7.0);
        this.galaxyPullDurationTicks = configInt("galaxy-pull-duration-ticks", 50);
        this.galaxyExplosionDamage = configDouble("galaxy-explosion-damage", 16.0);
        this.galaxyExplosionRadius = configDouble("galaxy-explosion-radius", 6.0);
    }

    private final Map<UUID, Integer> lastSlot = new HashMap<>();
    private final Map<UUID, Long> momentumWindowEndMs = new HashMap<>();

    private double momentumMultiplier(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long windowEnd = momentumWindowEndMs.get(uuid);
        Integer previousSlot = lastSlot.get(uuid);
        boolean triggered = windowEnd != null && now <= windowEnd && previousSlot != null && previousSlot != slot;

        lastSlot.put(uuid, slot);
        momentumWindowEndMs.put(uuid, now + momentumWindowMs);

        if (triggered) {
            Fx.point(player.getLocation().add(0, 1.5, 0), Particle.END_ROD, 6);
            Fx.coloredBurst(player.getLocation().add(0, 1.5, 0), STARLIGHT_VIOLET, 0.7f, 6, 0.25);
            return 1 + momentumDamageBonus;
        }
        return 1.0;
    }

    @Override
    public String id() {
        return "starbreaker";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Starbreaker";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 4.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 7.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 9.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 65.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Nova")
                .accent(STARLIGHT_VIOLET)
                .perMeleeHit(configDouble("nova-per-hit", 6.0))
                .perDamageDealt(configDouble("nova-per-damage-dealt", 0.45))
                .perAbilityCast(configDouble("nova-per-ability", 9.0))
                .perKill(configDouble("nova-per-kill", 14.0))
                .decay(configDouble("nova-decay-per-second", 2.2), configDouble("nova-decay-grace", 6.0))
                .cooldownFloor(configDouble("nova-cooldown-floor", 58.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: throw a spread of", NamedTextColor.GRAY),
                Component.text("miniature stars.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Star Spread";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: warp to the", NamedTextColor.GRAY),
                Component.text("nearest enemy ahead and strike.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Warp Strike";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: trigger a supernova", NamedTextColor.GRAY),
                Component.text("around you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Supernova";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: collapse a galaxy", NamedTextColor.GRAY),
                Component.text("onto your enemies. Chaining", NamedTextColor.GRAY),
                Component.text("different abilities boosts damage.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Galaxy Collapse";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDER_DRAGON_FLAP;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
    }

    @Override
    public void ability1(Player player) {
        double damage = spreadDamage * rarity().statMultiplier() * momentumMultiplier(player, 1);
        Location eye = player.getEyeLocation();

        for (int i = 0; i < spreadCount; i++) {
            double offsetDegrees = (i - (spreadCount - 1) / 2.0) * spreadSpreadDegrees;
            double radians = Math.toRadians(offsetDegrees);
            Vector direction = eye.getDirection().clone();
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            Vector rotated = new Vector(direction.getX() * cos + direction.getZ() * sin, direction.getY(),
                    -direction.getX() * sin + direction.getZ() * cos);

            Snowball projectile = player.launchProjectile(Snowball.class, rotated.multiply(spreadSpeed));
            projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
            projectile.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "starbreaker_damage"), PersistentDataType.DOUBLE, damage);

            var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.NETHER_STAR, 0.5f, 40, 20.0);

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
                    Fx.point(projectile.getLocation(), Particle.END_ROD, 2);
                    Fx.point(projectile.getLocation(), Particle.PORTAL, 1);
                    Fx.coloredBurst(projectile.getLocation(), STARLIGHT_VIOLET, 0.5f, 2, 0.08);
                    if (icon != null && !icon.isDead()) {
                        icon.teleport(projectile.getLocation());
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        Double taggedDamage = event.getEntity().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "starbreaker_damage"), PersistentDataType.DOUBLE);
        double damage = taggedDamage != null ? taggedDamage : spreadDamage * rarity().statMultiplier();

        Fx.burst(loc, Particle.END_ROD, 30, 0.45);
        Fx.burst(loc, Particle.PORTAL, 20, 0.45);
        Fx.coloredBurst(loc, STARLIGHT_VIOLET, 1.4f, 26, 0.5);
        Fx.sound(loc, hitSound(), 0.7f, 1.3f);

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = warpDamage * rarity().statMultiplier() * momentumMultiplier(player, 2);
        World world = player.getWorld();
        Location origin = player.getLocation();
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;

        for (Entity entity : world.getNearbyEntities(origin, warpRange, warpRange, warpRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = origin.getDirection().normalize().dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.3) {
                continue;
            }
            double distanceSquared = living.getLocation().distanceSquared(origin);
            if (distanceSquared < closest) {
                closest = distanceSquared;
                target = living;
            }
        }
        if (target == null) {
            return;
        }

        Vector behind = target.getLocation().getDirection().normalize().multiply(-1.5);
        Location warpTo = target.getLocation().add(behind).add(0, 0, 0);
        Fx.line(origin.add(0, 1, 0), warpTo.clone().add(0, 1, 0), Particle.END_ROD, 26);
        Fx.coloredBurst(warpTo.clone().add(0, 1, 0), STARLIGHT_VIOLET, 1.2f, 18, 0.35);
        player.teleport(warpTo.setDirection(target.getLocation().subtract(warpTo).toVector()));
        target.damage(damage, player);
        Fx.burst(target.getLocation().add(0, 1, 0), Particle.END_ROD, 36, 0.45);
        Fx.coloredBurst(target.getLocation().add(0, 1, 0), STARLIGHT_VIOLET, 1.4f, 28, 0.45);
        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
    }

    @Override
    public void ability3(Player player) {
        double damage = supernovaDamage * rarity().statMultiplier() * momentumMultiplier(player, 3);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.END_ROD, 75, supernovaRadius * 0.55);
        Fx.burst(center.clone().add(0, 1, 0), Particle.PORTAL, 55, supernovaRadius * 0.55);
        Fx.coloredBurst(center.clone().add(0, 1, 0), STARLIGHT_VIOLET, 1.6f, 55, supernovaRadius * 0.6);
        Fx.expandingRings(plugin, center.clone().add(0, 0.1, 0), Particle.PORTAL, supernovaRadius, 5, 2L);
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f);

        for (Entity entity : world.getNearbyEntities(center, supernovaRadius, supernovaRadius, supernovaRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.4);
                living.setVelocity(living.getVelocity().add(knockback));
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        double explosionDamage = galaxyExplosionDamage * rarity().statMultiplier() * momentumMultiplier(player, 4);
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(5));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        Fx.spinningIcon(plugin, center.clone().add(0, 1.2, 0), Material.NETHER_STAR, 1.3f, galaxyPullDurationTicks, 18.0);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks < galaxyPullDurationTicks) {
                    angle += 0.35;
                    Fx.ring(center.clone().add(0, -0.4, 0), Particle.PORTAL, 2.0, 26, angle);
                    Fx.ring(center, Particle.PORTAL, 2.7, 32, -angle * 1.2);
                    Fx.ring(center.clone().add(0, 0.4, 0), Particle.PORTAL, 3.4, 38, angle * 0.75);
                    Fx.dragonBreathBurst(center, 8, 0);
                    Fx.coloredBurst(center, STARLIGHT_VIOLET, 1.3f, 12, 1.4);
                    for (Entity entity : world.getNearbyEntities(center, galaxyPullRadius, galaxyPullRadius, galaxyPullRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.25);
                            living.setVelocity(living.getVelocity().add(pull));
                        }
                    }
                    ticks++;
                    return;
                }

                Fx.burst(center, Particle.END_ROD, 100, galaxyExplosionRadius * 0.65);
                Fx.burst(center, Particle.PORTAL, 100, galaxyExplosionRadius * 0.65);
                Fx.dragonBreathBurst(center, 70, galaxyExplosionRadius * 0.55);
                Fx.coloredBurst(center, STARLIGHT_VIOLET, 2.0f, 70, galaxyExplosionRadius * 0.65);
                Fx.expandingRings(plugin, center, Particle.END_ROD, galaxyExplosionRadius, 5, 2L);
                Fx.sound(center, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.5f, 0.9f);

                for (Entity entity : world.getNearbyEntities(center, galaxyExplosionRadius, galaxyExplosionRadius, galaxyExplosionRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                        living.damage(explosionDamage, player);
                        Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.6);
                        living.setVelocity(living.getVelocity().add(knockback.multiply(1.5)));
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
