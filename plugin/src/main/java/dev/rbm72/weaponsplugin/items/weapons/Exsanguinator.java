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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ranged blood scythe: every ability actually throws physical blade projectiles (real, gravity-affected
 * {@link Snowball} entities dressed as blades via {@link Fx#spinningIcon}) instead of leaning on particles —
 * a fan of blades, a melee reap that flings real {@link Item} debris outward, a geyser of blades stabbed
 * straight up, and a wide blood-rain volley for the ultimate.
 */
public final class Exsanguinator extends Weapon {

    private static final Color BLOOD_BRIGHT = Color.fromRGB(200, 10, 10);
    private static final Color BLOOD_DEEP = Color.fromRGB(120, 0, 15);

    /** Tags which of the three blade-projectile abilities landed, since they all route through one onProjectileHit. */
    private static final byte SHOT_FLURRY = 0;
    private static final byte SHOT_GEYSER = 1;
    private static final byte SHOT_RAIN = 2;

    private final double flurryDamage;
    private final int flurryCount;
    private final double flurrySpeed;
    private final double reapDamage;
    private final double reapRange;
    private final double reapArcDegrees;
    private final double geyserDamage;
    private final int geyserCount;
    private final double geyserRadius;
    private final double rainDamage;
    private final int rainCount;
    private final double rainRadius;
    private final int bleedDurationTicks;
    private final int bleedAmplifier;

    public Exsanguinator(WeaponsPlugin plugin) {
        super(plugin);
        this.flurryDamage = configDouble("flurry-damage", 4.5);
        this.flurryCount = configInt("flurry-count", 5);
        this.flurrySpeed = configDouble("flurry-speed", 1.5);
        this.reapDamage = configDouble("reap-damage", 7.0);
        this.reapRange = configDouble("reap-range", 3.5);
        this.reapArcDegrees = configDouble("reap-arc-degrees", 140.0);
        this.geyserDamage = configDouble("geyser-damage", 5.0);
        this.geyserCount = configInt("geyser-count", 4);
        this.geyserRadius = configDouble("geyser-radius", 2.5);
        this.rainDamage = configDouble("rain-damage", 4.0);
        this.rainCount = configInt("rain-count", 18);
        this.rainRadius = configDouble("rain-radius", 6.0);
        this.bleedDurationTicks = configInt("bleed-duration-ticks", 60);
        this.bleedAmplifier = configInt("bleed-amplifier", 0);
    }

    private NamespacedKey shotKey() {
        return new NamespacedKey(plugin, "exsanguinator_shot");
    }

    @Override
    public String id() {
        return "exsanguinator";
    }

    @Override
    public Material material() {
        return Material.IRON_HOE;
    }

    @Override
    public String displayNameText() {
        return "Exsanguinator";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.2);
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
        return configDouble("ability3-cooldown-seconds", 8.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Sanguine")
                .accent(BLOOD_BRIGHT)
                .perMeleeHit(configDouble("sanguine-per-hit", 5.0))
                .perDamageDealt(configDouble("sanguine-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("sanguine-per-ability", 9.0))
                .perKill(configDouble("sanguine-per-kill", 12.0))
                .decay(configDouble("sanguine-decay-per-second", 2.0), configDouble("sanguine-decay-grace", 7.0))
                .cooldownFloor(configDouble("sanguine-cooldown-floor", 45.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: hurl a fan of", NamedTextColor.GRAY),
                Component.text("real blood blades in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Blade Flurry";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: reap a wide arc,", NamedTextColor.GRAY),
                Component.text("flinging shredded blades outward", NamedTextColor.GRAY),
                Component.text("as everything in front bleeds.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Crimson Reap";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: stab down, launching", NamedTextColor.GRAY),
                Component.text("blades straight up around you", NamedTextColor.GRAY),
                Component.text("that crash back down on landing.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Blood Geyser";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: launch a huge volley", NamedTextColor.GRAY),
                Component.text("of blades that rain down over a", NamedTextColor.GRAY),
                Component.text("wide area in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Crimson Rain";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_THROW;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_PLAYER_LEVELUP;
    }

    private Snowball launchBlade(Player player, Vector velocity, byte shot) {
        Snowball projectile = player.launchProjectile(Snowball.class, velocity);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        projectile.getPersistentDataContainer().set(shotKey(), PersistentDataType.BYTE, shot);

        ItemDisplay icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.IRON_SWORD, 0.7f, 60, 32.0);
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
                Fx.coloredBurst(projectile.getLocation(), BLOOD_BRIGHT, 0.9f, 2, 0.04);
                if (icon != null && !icon.isDead()) {
                    icon.teleport(projectile.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return projectile;
    }

    private static Vector rotateY(Vector v, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    private void bleed(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, bleedDurationTicks, bleedAmplifier, false, true));
    }

    @Override
    public void ability1(Player player) {
        Vector base = player.getLocation().getDirection().normalize();
        int half = flurryCount / 2;
        for (int i = 0; i < flurryCount; i++) {
            double angle = (i - half) * 0.10;
            Vector direction = rotateY(base, angle).multiply(flurrySpeed);
            launchBlade(player, direction, SHOT_FLURRY);
        }
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), BLOOD_BRIGHT, 1.2f, 14, 0.35);
        Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.9f);
    }

    /** Real, physical debris — glowing sword-shaped {@link Item} entities flung outward with velocity/gravity — not particles. */
    private void flingBladeDebris(Player player, Vector forward, double spreadDegrees, int count) {
        World world = player.getWorld();
        Location origin = player.getLocation().add(0, 1.1, 0);
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(ThreadLocalRandom.current().nextDouble(-spreadDegrees / 2, spreadDegrees / 2));
            Vector direction = rotateY(forward, angle);
            direction.setY(ThreadLocalRandom.current().nextDouble(0.05, 0.3));
            Item item = world.dropItem(origin, new ItemStack(Material.IRON_SWORD));
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setGlowing(true);
            item.setVelocity(direction.normalize().multiply(ThreadLocalRandom.current().nextDouble(0.6, 1.0)));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!item.isDead()) {
                        item.remove();
                    }
                }
            }.runTaskLater(plugin, 30L);
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = reapDamage * rarity().statMultiplier();
        Location origin = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();
        Vector forward = player.getLocation().getDirection().setY(0).normalize();
        double halfArcRadians = Math.toRadians(reapArcDegrees / 2);

        flingBladeDebris(player, forward, reapArcDegrees, 6);
        Fx.coloredBurst(origin, BLOOD_DEEP, 1.6f, 26, reapRange * 0.4);
        Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.8f);

        for (Entity entity : world.getNearbyEntities(origin, reapRange, reapRange, reapRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toTarget = living.getLocation().toVector().subtract(origin.toVector());
            if (toTarget.lengthSquared() < 0.01) {
                continue;
            }
            double angleBetween = forward.angle(toTarget.clone().setY(0));
            if (angleBetween <= halfArcRadians) {
                living.damage(damage, player);
                bleed(living);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ability3(Player player) {
        Location center = player.getLocation();
        Fx.sound(player, Sound.BLOCK_BIG_DRIPLEAF_TILT_DOWN, 1.0f, 0.7f);
        Fx.coloredBurst(center.clone().add(0, 0.1, 0), BLOOD_DEEP, 1.6f, 24, 1.0);
        for (int i = 0; i < geyserCount; i++) {
            double ox = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
            double oz = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
            Location spawnAt = center.clone().add(ox, 0.1, oz);
            Vector up = new Vector(ThreadLocalRandom.current().nextDouble(-0.1, 0.1), 1.3, ThreadLocalRandom.current().nextDouble(-0.1, 0.1));
            Snowball blade = player.launchProjectile(Snowball.class);
            blade.teleport(spawnAt);
            blade.setVelocity(up);
            blade.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
            blade.getPersistentDataContainer().set(shotKey(), PersistentDataType.BYTE, SHOT_GEYSER);

            ItemDisplay icon = Fx.spinningIcon(plugin, spawnAt, Material.IRON_SWORD, 0.7f, 60, 32.0);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!blade.isValid()) {
                        if (icon != null && !icon.isDead()) {
                            icon.remove();
                        }
                        cancel();
                        return;
                    }
                    Fx.coloredBurst(blade.getLocation(), BLOOD_BRIGHT, 0.9f, 2, 0.04);
                    if (icon != null && !icon.isDead()) {
                        icon.teleport(blade.getLocation());
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    @Override
    public void ultimate(Player player) {
        Location origin = player.getLocation().add(0, 1.2, 0);
        Vector forward = player.getLocation().getDirection().normalize();
        Fx.sound(player, Sound.ENTITY_WITHER_SHOOT, 1.1f, 0.7f);
        Fx.coloredBurst(origin, BLOOD_BRIGHT, 1.8f, 30, 0.5);

        int half = rainCount / 2;
        for (int i = 0; i < rainCount; i++) {
            double lateralAngle = (i - half) * 0.06;
            Vector direction = rotateY(forward, lateralAngle);
            double distanceFactor = 0.55 + ThreadLocalRandom.current().nextDouble(0, 0.35);
            direction = direction.multiply(rainRadius * distanceFactor * 0.16);
            direction.setY(0.9 + ThreadLocalRandom.current().nextDouble(0, 0.5));
            launchBlade(player, direction, SHOT_RAIN);
        }
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        byte shot = event.getEntity().getPersistentDataContainer()
                .getOrDefault(shotKey(), PersistentDataType.BYTE, SHOT_FLURRY);

        Fx.coloredBurst(loc, BLOOD_BRIGHT, 1.3f, 16, 0.25);
        Fx.sound(loc, Sound.ENTITY_PLAYER_HURT, 0.8f, 1.1f);

        if (shot == SHOT_FLURRY) {
            double damage = flurryDamage * rarity().statMultiplier();
            if (event.getHitEntity() instanceof LivingEntity target) {
                target.damage(damage, shooter);
                bleed(target);
                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            }
        } else {
            double damage = (shot == SHOT_GEYSER ? geyserDamage : rainDamage) * rarity().statMultiplier();
            double radius = shot == SHOT_GEYSER ? geyserRadius : 1.8;
            Fx.expandingRings(plugin, loc, Particle.CRIMSON_SPORE, radius, 3, 2L);
            for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
                if (entity instanceof LivingEntity living && !entity.equals(shooter)) {
                    living.damage(damage, shooter);
                    bleed(living);
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
            }
        }

        if (!event.getEntity().isDead()) {
            event.getEntity().remove();
        }
    }
}
