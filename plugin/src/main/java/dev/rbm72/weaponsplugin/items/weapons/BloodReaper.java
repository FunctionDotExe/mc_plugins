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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Risk-vs-reward scythe (reskinned hoe): HP-cost burst, lifesteal slash, blood AoE, and a full-heal drain ultimate. */
public final class BloodReaper extends Weapon {

    private static final Color BLOOD_DEEP = Color.fromRGB(120, 0, 15);
    private static final Color BLOOD_BRIGHT = Color.fromRGB(200, 10, 10);

    private final double lowHpDamageBonusMax;
    private final double sacrificeHpCost;
    private final double sacrificeDamage;
    private final double sacrificeRadius;
    private final double lifestealDamage;
    private final double lifestealRange;
    private final double lifestealHealFraction;
    private final double explosionDamage;
    private final double explosionRadius;
    private final double explosionHealFraction;
    private final double drainRadius;
    private final int drainDurationTicks;
    private final double drainDamagePerTick;

    public BloodReaper(WeaponsPlugin plugin) {
        super(plugin);
        this.lowHpDamageBonusMax = configDouble("low-hp-damage-bonus-max", 0.5);
        this.sacrificeHpCost = configDouble("sacrifice-hp-cost", 3.0);
        this.sacrificeDamage = configDouble("sacrifice-damage", 9.0);
        this.sacrificeRadius = configDouble("sacrifice-radius", 4.0);
        this.lifestealDamage = configDouble("lifesteal-damage", 6.0);
        this.lifestealRange = configDouble("lifesteal-range", 3.0);
        this.lifestealHealFraction = configDouble("lifesteal-heal-fraction", 0.6);
        this.explosionDamage = configDouble("explosion-damage", 6.0);
        this.explosionRadius = configDouble("explosion-radius", 3.5);
        this.explosionHealFraction = configDouble("explosion-heal-fraction", 0.3);
        this.drainRadius = configDouble("drain-radius", 5.0);
        this.drainDurationTicks = configInt("drain-duration-ticks", 50);
        this.drainDamagePerTick = configDouble("drain-damage-per-tick", 1.0);
    }

    @Override
    public String id() {
        return "blood_reaper";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_HOE;
    }

    @Override
    public String displayNameText() {
        return "Blood Reaper";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 8.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 5.0);
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
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: sacrifice your own", NamedTextColor.GRAY),
                Component.text("health for a damaging blood burst.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Blood Sacrifice";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: lifesteal slash,", NamedTextColor.GRAY),
                Component.text("healing you for damage dealt.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Lifesteal Slash";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: blood explosion around", NamedTextColor.GRAY),
                Component.text("you, damaging and healing you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Crimson Burst";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: drain nearby", NamedTextColor.GRAY),
                Component.text("enemies' health to heal yourself.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Blood Drain";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_HOSTILE_BIG_FALL;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_PLAYER_LEVELUP;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        double missingFraction = 1 - (attacker.getHealth() / attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        double bonus = 1 + (missingFraction * lowHpDamageBonusMax);
        event.setDamage(event.getDamage() * bonus);
    }

    @Override
    public void ability1(Player player) {
        if (player.getHealth() <= sacrificeHpCost) {
            return;
        }
        player.damage(sacrificeHpCost);
        double damage = sacrificeDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.CRIMSON_SPORE, 70,
                sacrificeRadius * 0.55);
        Fx.coloredBurst(center.clone().add(0, 1, 0), BLOOD_DEEP, 2.2f, 45, sacrificeRadius * 0.55);
        Fx.spinningIcon(plugin, center.clone().add(0, 1.4, 0), Material.REDSTONE, 1.0f, 20, 16);
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);

        for (Entity entity : world.getNearbyEntities(center, sacrificeRadius, sacrificeRadius, sacrificeRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = lifestealDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location origin = player.getLocation();
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;

        for (Entity entity : world.getNearbyEntities(origin, lifestealRange, lifestealRange, lifestealRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
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

        slashTrail(player);
        target.damage(damage, player);
        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        double healAmount = damage * lifestealHealFraction;
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));
        Fx.burst(player.getLocation().add(0, 1, 0), Particle.CRIMSON_SPORE, 30, 0.5);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), BLOOD_BRIGHT, 1.8f, 24, 0.45);
    }

    /**
     * A crimson arc swept across the player's facing direction so the lifesteal hit reads as an actual
     * slash. Drawn as two parallel arcs at slightly different heights so the slash has real blade width
     * instead of being a single thin line of dots.
     */
    private void slashTrail(Player player) {
        Location center = player.getLocation().add(0, 1.1, 0);
        Vector forward = player.getLocation().getDirection().setY(0).normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        for (int i = -6; i <= 6; i++) {
            double t = i / 6.0;
            Location point = center.clone().add(right.clone().multiply(t * 1.5)).add(forward.clone().multiply(0.4 + Math.abs(t) * 0.2));
            Fx.coloredBurst(point, BLOOD_BRIGHT, 1.4f, 3, 0.05);
            Fx.coloredBurst(point.clone().add(0, 0.35, 0), BLOOD_BRIGHT, 1.1f, 2, 0.04);
            Fx.coloredBurst(point.clone().add(0, -0.35, 0), BLOOD_BRIGHT, 1.1f, 2, 0.04);
        }
    }

    @Override
    public void ability3(Player player) {
        double damage = explosionDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.CRIMSON_SPORE, 65, explosionRadius * 0.55);
        bloodRing(center.clone().add(0, 0.1, 0), explosionRadius);
        Fx.expandingRings(plugin, center.clone().add(0, 0.1, 0), Particle.CRIMSON_SPORE, explosionRadius, 4, 3L);

        double totalDamageDealt = 0;
        for (Entity entity : world.getNearbyEntities(center, explosionRadius, explosionRadius, explosionRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                totalDamageDealt += damage;
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }

        if (totalDamageDealt > 0) {
            double healAmount = totalDamageDealt * explosionHealFraction;
            double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));
        }
    }

    /**
     * A ring of crimson dust at the AoE's edge — visually distinct from ability1's flat self-centered
     * burst. Three concentric rings (inner/mid/outer) plus a raised copy give the ring real thickness
     * instead of reading as one thin outline.
     */
    private void bloodRing(Location center, double radius) {
        int points = 40;
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double[] radii = {radius - 0.4, radius, radius + 0.4};
        for (double r : radii) {
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double x = center.getX() + r * Math.cos(angle);
                double z = center.getZ() + r * Math.sin(angle);
                Fx.coloredBurst(new Location(world, x, center.getY(), z), BLOOD_BRIGHT, 2.0f, 4, 0.08);
                Fx.coloredBurst(new Location(world, x, center.getY() + 0.5, z), BLOOD_BRIGHT, 1.6f, 2, 0.06);
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.7f);
        Fx.spinningIcon(plugin, player.getEyeLocation().add(0, 0.6, 0), Material.WITHER_SKELETON_SKULL, 1.1f, drainDurationTicks, 14);

        new BukkitRunnable() {
            int ticks = 0;
            final double damage = drainDamagePerTick * rarity().statMultiplier();

            @Override
            public void run() {
                if (ticks >= drainDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                // Ambient swirl every tick so the channel reads as continuously active between
                // damage pulses, instead of flickering only when health actually transfers.
                Fx.coloredBurst(player.getLocation().add(0, 1.2, 0), BLOOD_DEEP, 1.2f, 4, 0.3);
                if (ticks % 5 == 0) {
                    double healed = 0;
                    for (Entity entity : world.getNearbyEntities(center, drainRadius, drainRadius, drainRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            healed += damage;
                            drainBeam(living.getLocation().add(0, 1, 0), player.getLocation().add(0, 1, 0));
                        }
                    }
                    if (healed > 0) {
                        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                        player.setHealth(Math.min(maxHealth, player.getHealth() + healed));
                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), BLOOD_BRIGHT, 2.0f, 26, 0.5);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Three parallel offset lines between victim and drainer so the drain beam reads as a thick tether, not a wire. */
    private void drainBeam(Location from, Location to) {
        Fx.line(from, to, Particle.CRIMSON_SPORE, 20);
        Vector step = to.toVector().subtract(from.toVector());
        Vector dir = step.lengthSquared() > 0.0001 ? step.clone().normalize() : new Vector(1, 0, 0);
        Vector horizontal = new Vector(-dir.getZ(), 0, dir.getX());
        Vector perp = (horizontal.lengthSquared() > 0.0001 ? horizontal.normalize() : new Vector(1, 0, 0)).multiply(0.3);
        Fx.line(from.clone().add(perp), to.clone().add(perp), Particle.CRIMSON_SPORE, 16);
        Fx.line(from.clone().subtract(perp), to.clone().subtract(perp), Particle.CRIMSON_SPORE, 16);
    }
}
