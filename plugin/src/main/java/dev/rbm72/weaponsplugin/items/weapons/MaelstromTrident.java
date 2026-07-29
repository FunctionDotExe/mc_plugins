package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Legendary storm-trident dropped by the Tide Leviathan: thrusting lunge, ranged jet, pulling whirlpool, and a drowning maelstrom ultimate. */
public final class MaelstromTrident extends Weapon {

    private static final Color TIDE_COLOR = Color.fromRGB(40, 200, 200);
    private static final Color DEEP_TIDE_COLOR = Color.fromRGB(10, 90, 130);

    private final double thrustDamage;
    private final double thrustSpeed;
    private final double thrustRange;
    private final double jetDamage;
    private final double jetRange;
    private final double jetHitRadius;
    private final double whirlpoolDamagePerTick;
    private final double whirlpoolRadius;
    private final int whirlpoolDurationTicks;
    private final double maelstromMaxRadius;
    private final double maelstromDamage;
    private final int maelstromRings;
    private final double waterDamageMultiplier;

    public MaelstromTrident(WeaponsPlugin plugin) {
        super(plugin);
        this.thrustDamage = configDouble("thrust-damage", 8.0);
        this.thrustSpeed = configDouble("thrust-speed", 1.6);
        this.thrustRange = configDouble("thrust-range", 5.0);
        this.jetDamage = configDouble("jet-damage", 7.0);
        this.jetRange = configDouble("jet-range", 14.0);
        this.jetHitRadius = configDouble("jet-hit-radius", 1.8);
        this.whirlpoolDamagePerTick = configDouble("whirlpool-damage-per-tick", 1.5);
        this.whirlpoolRadius = configDouble("whirlpool-radius", 4.5);
        this.whirlpoolDurationTicks = configInt("whirlpool-duration-ticks", 50);
        this.maelstromMaxRadius = configDouble("maelstrom-max-radius", 9.0);
        this.maelstromDamage = configDouble("maelstrom-damage", 11.0);
        this.maelstromRings = configInt("maelstrom-rings", 5);
        this.waterDamageMultiplier = configDouble("water-damage-multiplier", 1.35);
    }

    @Override
    public String id() {
        return "maelstrom_trident";
    }

    @Override
    public Material material() {
        return Material.TRIDENT;
    }

    @Override
    public String displayNameText() {
        return "Maelstrom Trident";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.0);
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
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Riptide")
                .accent(TIDE_COLOR)
                .perMeleeHit(configDouble("riptide-per-hit", 6.0))
                .perDamageDealt(configDouble("riptide-per-damage-dealt", 0.45))
                .perAbilityCast(configDouble("riptide-per-ability", 9.0))
                .perKill(configDouble("riptide-per-kill", 13.0))
                .decay(configDouble("riptide-decay-per-second", 2.0), configDouble("riptide-decay-grace", 7.0))
                .cooldownFloor(configDouble("riptide-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Tidal Thrust — lunge", NamedTextColor.GRAY),
                Component.text("forward, damaging and hurling back", NamedTextColor.GRAY),
                Component.text("enemies in your path.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Tidal Thrust";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Whirlpool — spawn", NamedTextColor.GRAY),
                Component.text("a vortex ahead that drags enemies in.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Whirlpool";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Water Jet — fire a piercing", NamedTextColor.GRAY),
                Component.text("beam of water in a straight line.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Water Jet";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Maelstrom — unleash a", NamedTextColor.GRAY),
                Component.text("storm-vortex that pulls, drowns, and", NamedTextColor.GRAY),
                Component.text("batters everything around you.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Maelstrom";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_RIPTIDE_2;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_SPLASH;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_CONDUIT_ACTIVATE;
    }

    /** Wet = submerged, or standing exposed to the sky during a storm. */
    private boolean isWet(Player player) {
        if (player.isInWater()) {
            return true;
        }
        World world = player.getWorld();
        if (!world.hasStorm()) {
            return false;
        }
        Location loc = player.getLocation();
        return world.getHighestBlockYAt(loc) <= loc.getBlockY();
    }

    @Override
    public void onTick(Player player) {
        if (isWet(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 30, 0, true, false));
        }
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (isWet(attacker)) {
            event.setDamage(event.getDamage() * waterDamageMultiplier);
            Fx.coloredBurst(victim.getLocation().add(0, 1, 0), TIDE_COLOR, 1.1f, 10, 0.3);
        }
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
        player.setVelocity(direction.clone().multiply(thrustSpeed).setY(0.35));
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.0f);
        Fx.trail(origin.clone().add(0, 1, 0), Particle.BUBBLE, 40, 1.0, 0.1);
        Fx.coloredBurst(origin.clone().add(direction.clone().multiply(1.5)).add(0, 1, 0), TIDE_COLOR, 1.6f, 30, 0.8);

        for (Entity entity : world.getNearbyEntities(origin, thrustRange, thrustRange, thrustRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector());
            toEntity.setY(0);
            if (toEntity.lengthSquared() < 1.0e-4 || direction.clone().setY(0).normalize().dot(toEntity.normalize()) < 0.4) {
                continue;
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            living.setVelocity(living.getVelocity().add(direction.clone().multiply(1.3).setY(0.4)));
        }
    }

    @Override
    public void ability2(Player player) {
        double damagePerTick = whirlpoolDamagePerTick * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(5));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.7f);
        Fx.spinningIcon(plugin, center.clone().add(0, 0.2, 0), Material.HEART_OF_THE_SEA, 0.9f, whirlpoolDurationTicks, 14);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= whirlpoolDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                double currentRadius = whirlpoolRadius * (1 - ticks / (double) whirlpoolDurationTicks);
                Fx.ring(center, Particle.SPLASH, currentRadius, 28, angle);
                Fx.ring(center.clone().add(0, 0.5, 0), Particle.BUBBLE, currentRadius * 0.8, 22, angle + 0.6);
                Fx.coloredBurst(center.clone().add(0, 0.1, 0), DEEP_TIDE_COLOR, 1.3f, 10, currentRadius * 0.5);
                angle += 0.4;

                for (Entity entity : world.getNearbyEntities(center, whirlpoolRadius, whirlpoolRadius, whirlpoolRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    Vector pull = center.toVector().subtract(living.getLocation().toVector());
                    if (pull.lengthSquared() < 1.0e-4) {
                        continue;
                    }
                    living.setVelocity(living.getVelocity().add(pull.normalize().multiply(0.18)));
                    if (ticks % 10 == 0) {
                        living.damage(damagePerTick, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        double damage = jetDamage * rarity().statMultiplier();
        Location origin = player.getEyeLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Vector direction = origin.getDirection().normalize();
        Location end = origin.clone().add(direction.clone().multiply(jetRange));
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.0f, 1.2f);
        Fx.line(origin, end, Particle.BUBBLE, 48);
        Fx.line(origin, end, Particle.SPLASH, 48);
        Fx.coloredBurst(end, TIDE_COLOR, 1.5f, 20, 0.5);
        // Real (cosmetic) water-block props along the jet's path instead of splash particles alone.
        for (double d = 1.0; d <= jetRange; d += 1.5) {
            Location base = origin.clone().add(direction.clone().multiply(d));
            Fx.glowPillar(plugin, base, Material.WATER, 1.0f, 1.3f, 12);
        }

        Set<UUID> alreadyHit = new HashSet<>();
        for (Entity entity : world.getNearbyEntities(origin, jetRange, jetRange, jetRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player) || !alreadyHit.add(living.getUniqueId())) {
                continue;
            }
            Vector toEntity = living.getLocation().add(0, 1, 0).toVector().subtract(origin.toVector());
            double proj = toEntity.dot(direction);
            if (proj < 0 || proj > jetRange) {
                continue;
            }
            double lateral = toEntity.clone().subtract(direction.clone().multiply(proj)).length();
            if (lateral > jetHitRadius) {
                continue;
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            living.setVelocity(living.getVelocity().add(direction.clone().multiply(0.9).setY(0.25)));
        }
    }

    @Override
    public void ultimate(Player player) {
        double damage = maelstromDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.3f, 0.5f);
        Fx.sound(player, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ring = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ring >= maelstromRings || !player.isOnline()) {
                    cancel();
                    return;
                }
                double radius = maelstromMaxRadius * (ring + 1) / (double) maelstromRings;
                int points = 60 + ring * 16;
                Fx.ring(center, Particle.SPLASH, radius, points, angle);
                Fx.ring(center.clone().add(0, 0.6, 0), Particle.BUBBLE, radius * 0.9, points, angle + 0.3);
                Fx.ring(center.clone().add(0, 1.2, 0), Particle.SPLASH, radius * 0.8, (int) (points * 0.8), angle + 0.6);
                Fx.coloredBurst(center.clone().add(0, 0.3, 0), DEEP_TIDE_COLOR, 2.4f, 45 + ring * 15, radius * 0.9);
                world.spawnParticle(Particle.BUBBLE_COLUMN_UP, center.clone().add(0, 0.5, 0), 90, radius * 0.64, 1.6, radius * 0.64, 0.05);
                // A handful of real (cosmetic) water-block props riding the leading edge of the vortex.
                for (int i = 0; i < 6; i++) {
                    double waveAngle = angle + (2 * Math.PI * i) / 6;
                    Location wavePoint = center.clone().add(radius * Math.cos(waveAngle), 0, radius * Math.sin(waveAngle));
                    Fx.glowPillar(plugin, wavePoint, Material.WATER, 1.0f, 1.8f, 10);
                }
                angle += 0.6;

                for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player) || !alreadyHit.add(living.getUniqueId())) {
                        continue;
                    }
                    living.damage(damage, player);
                    StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, 60, 2);
                    Vector pull = center.toVector().subtract(living.getLocation().toVector());
                    if (pull.lengthSquared() > 1.0e-4) {
                        living.setVelocity(living.getVelocity().add(pull.normalize().multiply(0.4).setY(0.2)));
                    }
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }
}
