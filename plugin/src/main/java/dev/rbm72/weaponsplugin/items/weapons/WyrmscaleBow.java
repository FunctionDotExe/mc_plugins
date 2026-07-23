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

import java.util.List;

/** Draconic bow: an explosive dragonfire arrow, a fanned barrage, a backflip wing-dash, and a meteor-volley ultimate. Charged ability shots ignite and pierce. */
public final class WyrmscaleBow extends Weapon {

    private static final Color DRAGON_RED = Color.fromRGB(150, 20, 20);
    private static final Color EMBER = Color.fromRGB(255, 110, 0);

    private final double dragonfireSpeed;
    private final double dragonfireDamage;
    private final double dragonfireRadius;
    private final int dragonfireFireTicks;
    private final int barrageArrows;
    private final double barrageSpread;
    private final double barrageDamage;
    private final int barrageFireTicks;
    private final double wingDashPower;
    private final double wingGustDamage;
    private final double wingGustRadius;
    private final double wingGustKnockback;
    private final int meteorStrikeCount;
    private final double meteorDamagePerStrike;
    private final double meteorRadius;
    private final int meteorFireTicks;

    public WyrmscaleBow(WeaponsPlugin plugin) {
        super(plugin);
        this.dragonfireSpeed = configDouble("dragonfire-speed", 2.0);
        this.dragonfireDamage = configDouble("dragonfire-damage", 9.0);
        this.dragonfireRadius = configDouble("dragonfire-radius", 3.0);
        this.dragonfireFireTicks = configInt("dragonfire-fire-ticks", 100);
        this.barrageArrows = configInt("barrage-arrows", 5);
        this.barrageSpread = configDouble("barrage-spread", 0.22);
        this.barrageDamage = configDouble("barrage-damage", 5.0);
        this.barrageFireTicks = configInt("barrage-fire-ticks", 60);
        this.wingDashPower = configDouble("wing-dash-power", 1.3);
        this.wingGustDamage = configDouble("wing-gust-damage", 6.0);
        this.wingGustRadius = configDouble("wing-gust-radius", 4.0);
        this.wingGustKnockback = configDouble("wing-gust-knockback", 1.0);
        this.meteorStrikeCount = configInt("meteor-strike-count", 8);
        this.meteorDamagePerStrike = configDouble("meteor-damage-per-strike", 6.0);
        this.meteorRadius = configDouble("meteor-radius", 6.0);
        this.meteorFireTicks = configInt("meteor-fire-ticks", 80);
    }

    private NamespacedKey shotKey() {
        return new NamespacedKey(plugin, "wyrmscale_bow_shot");
    }

    @Override
    public String id() {
        return "wyrmscale_bow";
    }

    @Override
    public Material material() {
        return Material.BOW;
    }

    @Override
    public String displayNameText() {
        return "Wyrmscale Bow";
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
        return configDouble("ability1-cooldown-seconds", 8.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 9.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: loose an explosive", NamedTextColor.GRAY),
                Component.text("dragonfire arrow that ignites on impact.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Dragonfire Arrow";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: fan a barrage of", NamedTextColor.GRAY),
                Component.text("burning arrows in a wide spread.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Flame Barrage";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: backflip away on a wing-dash,", NamedTextColor.GRAY),
                Component.text("blasting nearby foes back with a gust.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Wing Dash";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Elder's Wrath — call", NamedTextColor.GRAY),
                Component.text("down a meteor volley at your aim.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Elders Wrath";
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
        return Sound.ENTITY_ENDER_DRAGON_FLAP;
    }

    private Location targetLocation(Player player) {
        var block = player.getTargetBlockExact(30);
        return block != null ? block.getLocation().add(0.5, 1, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().multiply(15));
    }

    private Arrow launchTagged(Player player, Vector velocity, int marker) {
        Arrow projectile = player.launchProjectile(Arrow.class, velocity);
        projectile.setDamage(0);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.setPersistent(false);
        projectile.setPierceLevel(3);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        projectile.getPersistentDataContainer().set(shotKey(), PersistentDataType.INTEGER, marker);
        return projectile;
    }

    @Override
    public void ability1(Player player) {
        Arrow projectile = launchTagged(player,
                player.getLocation().getDirection().multiply(dragonfireSpeed), 1);
        Fx.sound(player, castSound(), 1.0f, 0.7f);
        var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.FIRE_CHARGE, 0.9f, 100, 22.0);

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
                Fx.point(projectile.getLocation(), Particle.FLAME, 5);
                Fx.coloredBurst(projectile.getLocation(), EMBER, 1.2f, 6, 0.1);
                if (icon != null && !icon.isDead()) {
                    icon.teleport(projectile.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability2(Player player) {
        Fx.sound(player, castSound(), 1.0f, 1.1f);
        Vector base = player.getLocation().getDirection().normalize();
        for (int i = 0; i < barrageArrows; i++) {
            double offset = (i - (barrageArrows - 1) / 2.0) * barrageSpread;
            Vector dir = rotateY(base, offset).multiply(dragonfireSpeed * 0.9);
            Arrow projectile = launchTagged(player, dir, 2);
            Fx.point(projectile.getLocation(), Particle.FLAME, 3);
        }
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), DRAGON_RED, 1.4f, 16, 0.4);
    }

    @Override
    public void ability3(Player player) {
        Vector look = player.getLocation().getDirection().setY(0).normalize();
        Vector leap = look.multiply(-wingDashPower).setY(0.7);
        player.setVelocity(leap);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.8f);
        Fx.sound(player, Sound.ENTITY_PHANTOM_SWOOP, 0.9f, 0.9f);

        Location origin = player.getLocation();
        Fx.expandingRings(plugin, origin, Particle.CLOUD, wingGustRadius, 3, 2L);
        Fx.coloredBurst(origin.clone().add(0, 1, 0), DRAGON_RED, 1.6f, 24, 0.6);

        double damage = wingGustDamage * rarity().statMultiplier();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(origin, wingGustRadius, wingGustRadius, wingGustRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Vector push = living.getLocation().toVector().subtract(origin.toVector()).setY(0.4);
                if (push.lengthSquared() > 0.001) {
                    push = push.normalize().multiply(wingGustKnockback).setY(0.4);
                    living.setVelocity(living.getVelocity().add(push));
                }
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        int marker = event.getEntity().getPersistentDataContainer()
                .getOrDefault(shotKey(), PersistentDataType.INTEGER, 0);
        boolean dragonfire = marker == 1;

        double damage = (dragonfire ? dragonfireDamage : barrageDamage) * rarity().statMultiplier();
        double radius = dragonfire ? dragonfireRadius : 1.5;
        int fireTicks = dragonfire ? dragonfireFireTicks : barrageFireTicks;

        Fx.burst(loc, Particle.FLAME, dragonfire ? 40 : 16, radius * 0.5);
        Fx.coloredBurst(loc, dragonfire ? DRAGON_RED : EMBER, dragonfire ? 2.2f : 1.4f, dragonfire ? 40 : 18, radius * 0.5);
        if (dragonfire) {
            world.spawnParticle(Particle.EXPLOSION, loc, 18, 0.64, 0.64, 0.64, 0.05);
        }
        Fx.sound(loc, dragonfire ? hitSound() : Sound.ENTITY_BLAZE_HURT, dragonfire ? 1.2f : 0.8f, 1.0f);

        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !entity.equals(shooter)) {
                living.damage(damage, shooter);
                living.setFireTicks(fireTicks);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
        if (!event.getEntity().isDead()) {
            event.getEntity().remove();
        }
    }

    /**
     * A real falling magma block descends from above the strike point instead of a static
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
        FallingBlock meteor = world.spawnFallingBlock(start, Material.MAGMA_BLOCK.createBlockData());
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
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);

        for (int i = 0; i < meteorStrikeCount; i++) {
            int delay = i * 6;
            double offsetX = (Math.random() - 0.5) * meteorRadius * 2;
            double offsetZ = (Math.random() - 0.5) * meteorRadius * 2;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location strike = center.clone().add(offsetX, 0, offsetZ);
                meteorTrail(strike, () -> {
                    Fx.burst(strike, Particle.EXPLOSION, 8, 0.5);
                    Fx.coloredBurst(strike, DRAGON_RED, 2.2f, 32, 0.55);
                    Fx.sound(strike, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);

                    for (Entity entity : world.getNearbyEntities(strike, 2.0, 2.0, 2.0)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            living.setFireTicks(meteorFireTicks);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                });
            }, delay);
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
