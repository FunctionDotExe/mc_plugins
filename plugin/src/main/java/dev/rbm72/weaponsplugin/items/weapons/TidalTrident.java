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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Water-themed trident: wave cone, whirlpool pull, riptide jet, tsunami ultimate. */
public final class TidalTrident extends Weapon {

    private static final Color TIDE_COLOR = Color.fromRGB(30, 144, 255);
    private static final Color DEEP_TIDE_COLOR = Color.fromRGB(10, 60, 140);

    private final double waveDamage;
    private final double waveRange;
    private final double whirlpoolDamagePerTick;
    private final double whirlpoolRadius;
    private final int whirlpoolDurationTicks;
    private final double jetSpeed;
    private final double jetDamage;
    private final int jetTicks;
    private final double jetHitRadius;
    private final double tsunamiMaxRadius;
    private final double tsunamiDamage;
    private final int tsunamiRings;

    public TidalTrident(WeaponsPlugin plugin) {
        super(plugin);
        this.waveDamage = configDouble("wave-damage", 6.0);
        this.waveRange = configDouble("wave-range", 5.0);
        this.whirlpoolDamagePerTick = configDouble("whirlpool-damage-per-tick", 1.0);
        this.whirlpoolRadius = configDouble("whirlpool-radius", 4.0);
        this.whirlpoolDurationTicks = configInt("whirlpool-duration-ticks", 40);
        this.jetSpeed = configDouble("jet-speed", 1.8);
        this.jetDamage = configDouble("jet-damage", 5.0);
        this.jetTicks = configInt("jet-ticks", 12);
        this.jetHitRadius = configDouble("jet-hit-radius", 1.6);
        this.tsunamiMaxRadius = configDouble("tsunami-max-radius", 8.0);
        this.tsunamiDamage = configDouble("tsunami-damage", 10.0);
        this.tsunamiRings = configInt("tsunami-rings", 4);
    }

    @Override
    public String id() {
        return "tidal_trident";
    }

    @Override
    public Material material() {
        return Material.TRIDENT;
    }

    @Override
    public String displayNameText() {
        return "Tidal Trident";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
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
        return configDouble("ability2-cooldown-seconds", 8.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 6.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Undertow")
                .accent(TIDE_COLOR)
                .perMeleeHit(configDouble("undertow-per-hit", 4.0))
                .perDamageDealt(configDouble("undertow-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("undertow-per-ability", 8.0))
                .perKill(configDouble("undertow-per-kill", 11.0))
                .decay(configDouble("undertow-decay-per-second", 2.0), configDouble("undertow-decay-grace", 7.0))
                .cooldownFloor(configDouble("undertow-cooldown-floor", 45.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: summon a wave that", NamedTextColor.GRAY),
                Component.text("damages and knocks back enemies", NamedTextColor.GRAY),
                Component.text("in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Wave Crash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: spawn a whirlpool", NamedTextColor.GRAY),
                Component.text("that pulls nearby enemies in.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Whirlpool";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: launch yourself forward", NamedTextColor.GRAY),
                Component.text("on a jet of water, damaging enemies", NamedTextColor.GRAY),
                Component.text("in your path.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Riptide Jet";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: summon a tidal", NamedTextColor.GRAY),
                Component.text("tsunami that expands outward.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Tsunami";
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
        return Sound.BLOCK_WATER_AMBIENT;
    }

    @Override
    public void onTick(Player player) {
        if (player.isInWater()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 30, 0, true, false));
        }
    }

    @Override
    public void ability1(Player player) {
        double damage = waveDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        Fx.trail(origin.clone().add(0, 1, 0), Particle.SPLASH, 70, 1.4, 0.15);
        Fx.trail(origin.clone().add(direction.clone().multiply(1.0)).add(0, 1.3, 0), Particle.SPLASH, 45, 1.1, 0.12);
        Fx.coloredBurst(origin.clone().add(direction.clone().multiply(1.5)).add(0, 1, 0), TIDE_COLOR, 1.8f, 40, 1.0);
        Fx.coloredBurst(origin.clone().add(direction.clone().multiply(2.5)).add(0, 0.6, 0), TIDE_COLOR, 1.6f, 24, 0.9);

        for (Entity entity : world.getNearbyEntities(origin, waveRange, waveRange, waveRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (direction.dot(toEntity) < 0.5) {
                continue;
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            living.setVelocity(living.getVelocity().add(direction.clone().multiply(1.2).setY(0.3)));
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

        Fx.spinningIcon(plugin, center.clone().add(0, 0.2, 0), Material.HEART_OF_THE_SEA, 0.9f, whirlpoolDurationTicks, 12);

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
                // Stack rings at multiple heights so the vortex has real depth instead of
                // reading as a single flat circle spinning on the water surface.
                Fx.ring(center, Particle.SPLASH, currentRadius, 28, angle);
                Fx.ring(center.clone().add(0, 0.5, 0), Particle.SPLASH, currentRadius * 0.85, 24, angle + 0.5);
                Fx.ring(center.clone().add(0, 1.0, 0), Particle.SPLASH, currentRadius * 0.65, 20, angle + 1.0);
                Fx.coloredBurst(center.clone().add(0, 0.1, 0), DEEP_TIDE_COLOR, 1.3f, 10, currentRadius * 0.5);
                angle += 0.4;

                for (Entity entity : world.getNearbyEntities(center, whirlpoolRadius, whirlpoolRadius, whirlpoolRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.15);
                    living.setVelocity(living.getVelocity().add(pull));
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
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(jetSpeed).setY(0.4));

        double damage = jetDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= jetTicks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 1, 0);
                Fx.trail(loc, Particle.SPLASH, 8, 0.3, 0.05);
                Fx.coloredBurst(loc, TIDE_COLOR, 0.8f, 6, 0.25);

                for (Entity nearby : player.getNearbyEntities(jetHitRadius, jetHitRadius, jetHitRadius)) {
                    if (!(nearby instanceof LivingEntity entity) || !alreadyHit.add(entity.getUniqueId())) {
                        continue;
                    }
                    entity.damage(damage, player);
                    Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                    entity.setVelocity(entity.getVelocity().add(direction.clone().multiply(0.8).setY(0.3)));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        double damage = tsunamiDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 0.8f);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ring = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ring >= tsunamiRings || !player.isOnline()) {
                    cancel();
                    return;
                }
                double radius = tsunamiMaxRadius * (ring + 1) / (double) tsunamiRings;
                int points = 60 + ring * 16;
                // Stack the ring at several heights so each expanding step reads as a genuine
                // vertical wall/curtain of water sweeping outward, not a thin flat circle on the ground.
                Fx.ring(center, Particle.SPLASH, radius, points, angle);
                Fx.ring(center.clone().add(0, 0.4, 0), Particle.SPLASH, radius * 0.97, points, angle + 0.15);
                Fx.ring(center.clone().add(0, 0.8, 0), Particle.SPLASH, radius * 0.94, points, angle + 0.3);
                Fx.ring(center.clone().add(0, 1.2, 0), Particle.SPLASH, radius * 0.9, (int) (points * 0.8), angle + 0.45);
                Fx.coloredBurst(center.clone().add(0, 0.3, 0), DEEP_TIDE_COLOR, 2.4f, 45 + ring * 15, radius * 0.9);
                Fx.coloredBurst(center.clone().add(0, 1.0, 0), TIDE_COLOR, 2.0f, 30 + ring * 10, radius * 0.8);
                // A handful of real (cosmetic) water-block props riding the leading edge of the wave.
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
                    Vector outward = living.getLocation().toVector().subtract(center.toVector()).setY(0);
                    // An entity standing exactly on the epicenter normalizes to a NaN vector — fall back
                    // to an arbitrary horizontal push instead of crashing setVelocity().
                    Vector knockback = (outward.lengthSquared() > 1.0E-6 ? outward.normalize() : new Vector(1, 0, 0)).setY(0.4);
                    living.setVelocity(living.getVelocity().add(knockback));
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }
}
