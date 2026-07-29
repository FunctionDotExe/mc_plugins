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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Cosmic bow: raining star strikes, a homing shot, a comet shot, and a meteor shower ultimate. Every 5th shot explodes bigger. */
public final class CelestialBow extends Weapon {

    private static final Color STAR_GOLD = Color.fromRGB(255, 210, 80);
    private static final Color STAR_WHITE = Color.fromRGB(255, 250, 235);

    private final int rainStrikeCount;
    private final double rainDamagePerStrike;
    private final double rainRadius;
    private final double homingSpeed;
    private final double homingDamage;
    private final double cometSpeed;
    private final double cometDamage;
    private final double cometExplosionRadius;
    private final double bonusExplosionMultiplier;
    private final int meteorStrikeCount;
    private final double meteorDamagePerStrike;
    private final double meteorRadius;

    public CelestialBow(WeaponsPlugin plugin) {
        super(plugin);
        this.rainStrikeCount = configInt("rain-strike-count", 5);
        this.rainDamagePerStrike = configDouble("rain-damage-per-strike", 3.0);
        this.rainRadius = configDouble("rain-radius", 3.5);
        this.homingSpeed = configDouble("homing-speed", 1.6);
        this.homingDamage = configDouble("homing-damage", 6.0);
        this.cometSpeed = configDouble("comet-speed", 1.4);
        this.cometDamage = configDouble("comet-damage", 9.0);
        this.cometExplosionRadius = configDouble("comet-explosion-radius", 2.5);
        this.bonusExplosionMultiplier = configDouble("bonus-explosion-multiplier", 2.0);
        this.meteorStrikeCount = configInt("meteor-strike-count", 8);
        this.meteorDamagePerStrike = configDouble("meteor-damage-per-strike", 5.0);
        this.meteorRadius = configDouble("meteor-radius", 6.0);
    }

    private final Map<UUID, Integer> shotCounts = new HashMap<>();

    private NamespacedKey bonusKey() {
        return new NamespacedKey(plugin, "celestial_bow_bonus");
    }

    /** Returns true (and resets the streak) if this shot is the 5th and should explode bigger. */
    private boolean nextShotIsBonus(Player player) {
        int count = shotCounts.merge(player.getUniqueId(), 1, Integer::sum);
        if (count >= rainStrikeCount) {
            shotCounts.put(player.getUniqueId(), 0);
            return true;
        }
        return false;
    }

    @Override
    public String id() {
        return "celestial_bow";
    }

    @Override
    public Material material() {
        return Material.BOW;
    }

    @Override
    public String displayNameText() {
        return "Celestial Bow";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 9.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 4.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Starlight")
                .accent(STAR_GOLD)
                .perMeleeHit(configDouble("starlight-per-hit", 3.0))
                .perDamageDealt(configDouble("starlight-per-damage-dealt", 0.45))
                .perAbilityCast(configDouble("starlight-per-ability", 9.0))
                .perKill(configDouble("starlight-per-kill", 11.0))
                .decay(configDouble("starlight-decay-per-second", 2.0), configDouble("starlight-decay-grace", 7.0))
                .cooldownFloor(configDouble("starlight-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: call down a rain of", NamedTextColor.GRAY),
                Component.text("stars onto your target.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Starfall";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: fire a homing", NamedTextColor.GRAY),
                Component.text("shot that curves toward enemies.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Homing Star";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: fire a heavy comet shot.", NamedTextColor.GRAY),
                Component.text("Every 5th shot explodes bigger.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Comet Shot";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: call down a", NamedTextColor.GRAY),
                Component.text("meteor shower across the area.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Meteor Shower";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ARROW_SHOOT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_AMETHYST_CLUSTER_BREAK;
    }

    private Location targetLocation(Player player) {
        var block = player.getTargetBlockExact(30);
        return block != null ? block.getLocation().add(0.5, 1, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().multiply(15));
    }

    @Override
    public void ability1(Player player) {
        double damage = rainDamagePerStrike * rarity().statMultiplier();
        Location center = targetLocation(player);
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        for (int i = 0; i < rainStrikeCount; i++) {
            int delay = i * 4;
            double offsetX = (Math.random() - 0.5) * rainRadius * 2;
            double offsetZ = (Math.random() - 0.5) * rainRadius * 2;
            boolean bonus = nextShotIsBonus(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location strike = center.clone().add(offsetX, 0, offsetZ);
                double strikeDamage = bonus ? damage * bonusExplosionMultiplier : damage;
                meteorTrail(strike, () -> {
                    Fx.burst(strike, Particle.FIREWORK, bonus ? 60 : 32, 0.6);
                    Fx.coloredBurst(strike, STAR_GOLD, bonus ? 2.2f : 1.8f, bonus ? 45 : 30, 0.6);
                    Fx.sound(strike, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);

                    for (Entity entity : world.getNearbyEntities(strike, 1.5, 1.5, 1.5)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(strikeDamage, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                });
            }, delay);
        }
    }

    @Override
    public void ability2(Player player) {
        boolean bonus = nextShotIsBonus(player);
        Arrow projectile = player.launchProjectile(Arrow.class,
                player.getLocation().getDirection().multiply(homingSpeed));
        projectile.setGravity(false);
        projectile.setDamage(0);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        projectile.getPersistentDataContainer().set(bonusKey(), PersistentDataType.INTEGER, bonus ? 1 : 0);

        var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.NETHER_STAR, 0.7f, 60, 24);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!projectile.isValid() || ticks >= 60) {
                    if (icon != null && !icon.isDead()) {
                        icon.remove();
                    }
                    cancel();
                    return;
                }
                LivingEntity nearest = null;
                double closest = 8.0 * 8.0;
                for (Entity entity : projectile.getNearbyEntities(8, 8, 8)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        double distanceSquared = living.getLocation().distanceSquared(projectile.getLocation());
                        if (distanceSquared < closest) {
                            closest = distanceSquared;
                            nearest = living;
                        }
                    }
                }
                if (nearest != null) {
                    Vector toTarget = nearest.getLocation().add(0, 1, 0).toVector()
                            .subtract(projectile.getLocation().toVector()).normalize();
                    Vector current = projectile.getVelocity();
                    projectile.setVelocity(current.multiply(0.85).add(toTarget.multiply(0.3 * homingSpeed)));
                }
                Fx.point(projectile.getLocation(), Particle.END_ROD, 4);
                Fx.coloredBurst(projectile.getLocation(), STAR_GOLD, 1.2f, 6, 0.08);
                if (icon != null && !icon.isDead()) {
                    icon.teleport(projectile.getLocation());
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        boolean bonus = nextShotIsBonus(player);
        Arrow projectile = player.launchProjectile(Arrow.class,
                player.getLocation().getDirection().multiply(cometSpeed));
        projectile.setDamage(0);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        projectile.getPersistentDataContainer().set(bonusKey(), PersistentDataType.INTEGER, bonus ? 2 : 0);

        var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.FIRE_CHARGE, bonus ? 1.0f : 0.75f, 100, 22.0);

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
                Fx.point(projectile.getLocation(), Particle.FLAME, 4);
                Fx.point(projectile.getLocation(), Particle.END_ROD, 2);
                if (bonus) {
                    Fx.coloredBurst(projectile.getLocation(), STAR_GOLD, 2.0f, 14, 0.2);
                    Fx.trail(projectile.getLocation(), Particle.FLAME, 10, 0.15, 0.02);
                } else {
                    Fx.coloredBurst(projectile.getLocation(), STAR_WHITE, 1.2f, 5, 0.08);
                }
                if (icon != null && !icon.isDead()) {
                    icon.teleport(projectile.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        int marker = event.getEntity().getPersistentDataContainer().getOrDefault(bonusKey(), PersistentDataType.INTEGER, 0);
        boolean isComet = marker == 2;
        boolean bonus = marker != 0;

        double damage = (isComet ? cometDamage : homingDamage) * rarity().statMultiplier();
        double radius = isComet ? cometExplosionRadius : 1.5;
        if (bonus) {
            damage *= bonusExplosionMultiplier;
            radius *= bonusExplosionMultiplier;
        }

        Fx.burst(loc, Particle.FIREWORK, bonus ? 65 : 36, radius * 0.55);
        Fx.coloredBurst(loc, bonus ? STAR_GOLD : STAR_WHITE, bonus ? 2.4f : 1.8f, bonus ? 50 : 32, radius * 0.55);
        Fx.sound(loc, hitSound(), bonus ? 1.3f : 0.9f, 1.0f);

        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !entity.equals(shooter)) {
                living.damage(damage, shooter);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
        if (!event.getEntity().isDead()) {
            event.getEntity().remove();
        }
    }

    /**
     * A real falling glowstone block descends from above the strike point instead of a static
     * particle trail — {@code onImpact} fires once it lands (or after a short cap), so the burst
     * and damage still land right as the meteor visibly hits the ground.
     */
    private void meteorTrail(Location strike, Runnable onImpact) {
        World world = strike.getWorld();
        if (world == null) {
            onImpact.run();
            return;
        }
        Location start = strike.clone().add(0, 8, 0);
        FallingBlock meteor = world.spawnFallingBlock(start, Material.GLOWSTONE.createBlockData());
        meteor.setDropItem(false);
        meteor.setCancelDrop(true);
        meteor.setHurtEntities(false);
        meteor.setPersistent(false);
        meteor.setVelocity(new Vector(0, -1.6, 0));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!meteor.isValid() || ticks >= 20) {
                    if (meteor.isValid()) {
                        meteor.remove();
                    }
                    onImpact.run();
                    cancel();
                    return;
                }
                Fx.trail(meteor.getLocation(), Particle.FLAME, 12, 0.2, 0.02);
                Fx.point(meteor.getLocation(), Particle.END_ROD, 3);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        double damage = meteorDamagePerStrike * rarity().statMultiplier();
        Location center = targetLocation(player);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.4f);

        for (int i = 0; i < meteorStrikeCount; i++) {
            int delay = i * 6;
            double offsetX = (Math.random() - 0.5) * meteorRadius * 2;
            double offsetZ = (Math.random() - 0.5) * meteorRadius * 2;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location strike = center.clone().add(offsetX, 0, offsetZ);
                meteorTrail(strike, () -> {
                    Fx.spinningIcon(plugin, strike.clone().add(0, 0.3, 0), Material.NETHER_STAR, 0.9f, 16, 22);
                    Fx.burst(strike, Particle.EXPLOSION, 8, 0.5);
                    Fx.coloredBurst(strike, STAR_GOLD, 2.2f, 32, 0.55);
                    Fx.sound(strike, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);

                    for (Entity entity : world.getNearbyEntities(strike, 2.0, 2.0, 2.0)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                });
            }, delay);
        }
    }
}
