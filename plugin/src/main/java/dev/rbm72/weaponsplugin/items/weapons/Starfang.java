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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Starfang — a curved horn torn from the Voidwyrm before it ever grew into its ancient shape. A
 * biting breath cone, a gravity-well pull, a homing void bolt, and an ultimate that briefly collapses
 * a small star on top of whoever's unlucky enough to be standing there.
 */
public final class Starfang extends Weapon {

    private static final Color VOID_PURPLE = Color.fromRGB(110, 20, 160);

    private final double biteRange;
    private final double biteDamage;
    private final int biteNauseaTicks;
    private final double pullRadius;
    private final double pullStrength;
    private final double boltDamage;
    private final double boltHitRadius;
    private final double boltSpeed;
    private final int boltMaxLifeTicks;
    private final double collapseDamage;
    private final double collapseRadius;
    private final int collapseDelayTicks;

    public Starfang(WeaponsPlugin plugin) {
        super(plugin);
        this.biteRange = configDouble("bite-range", 4.0);
        this.biteDamage = configDouble("bite-damage", 6.5);
        this.biteNauseaTicks = configInt("bite-nausea-ticks", 50);
        this.pullRadius = configDouble("pull-radius", 5.0);
        this.pullStrength = configDouble("pull-strength", 0.3);
        this.boltDamage = configDouble("bolt-damage", 6.0);
        this.boltHitRadius = configDouble("bolt-hit-radius", 1.4);
        this.boltSpeed = configDouble("bolt-speed", 0.85);
        this.boltMaxLifeTicks = configInt("bolt-max-life-ticks", 50);
        this.collapseDamage = configDouble("collapse-damage", 11.0);
        this.collapseRadius = configDouble("collapse-radius", 4.5);
        this.collapseDelayTicks = configInt("collapse-delay-ticks", 30);
    }

    @Override
    public String id() {
        return "starfang";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_PICKAXE;
    }

    @Override
    public String displayNameText() {
        return "Starfang";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 6.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 10.0);
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
        return ChargeSpec.builder("Singularity")
                .accent(VOID_PURPLE)
                .perMeleeHit(configDouble("singularity-per-hit", 6.0))
                .perDamageDealt(configDouble("singularity-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("singularity-per-ability", 8.0))
                .perKill(configDouble("singularity-per-kill", 12.0))
                .decay(configDouble("singularity-decay-per-second", 2.0), configDouble("singularity-decay-grace", 7.0))
                .cooldownFloor(configDouble("singularity-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Void Bite — a cone of", NamedTextColor.GRAY),
                Component.text("corrosive breath disorients everything ahead.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Void Bite";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Gravity Well — drags", NamedTextColor.GRAY),
                Component.text("nearby enemies toward you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Gravity Well";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Void Bolt — a homing bolt", NamedTextColor.GRAY),
                Component.text("chases down whatever you're aiming near.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Void Bolt";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Collapse — a small star", NamedTextColor.GRAY),
                Component.text("gathers ahead of you and detonates.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Collapse";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDER_DRAGON_GROWL;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_ENDER_DRAGON_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_ENDER_DRAGON_FLAP;
    }

    @Override
    public void ability1(Player player) {
        double damage = biteDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        Fx.sound(player, castSound(), 1.0f, 0.9f);
        for (double d = 1; d <= biteRange; d += 0.5) {
            Fx.coloredBurst(origin.clone().add(direction.clone().multiply(d)), VOID_PURPLE, 1.0f, 5, 0.3);
            Fx.dragonBreathBurst(origin.clone().add(direction.clone().multiply(d)), 2, 0);
        }
        for (Entity entity : world.getNearbyEntities(origin, biteRange, biteRange, biteRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = direction.dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.6) {
                continue;
            }
            living.damage(damage, player);
            StatusEffectManager.apply(living, PotionEffectType.NAUSEA, biteNauseaTicks, 0);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        Location center = player.getLocation();
        World world = player.getWorld();
        Fx.coloredBurst(center.clone().add(0, 1, 0), VOID_PURPLE, 1.6f, 30, 0.5);
        Fx.sound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
        for (Entity entity : world.getNearbyEntities(center, pullRadius, pullRadius, pullRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                Vector toCenter = center.toVector().subtract(living.getLocation().toVector()).setY(0.1);
                if (toCenter.lengthSquared() > 1.0) {
                    living.setVelocity(living.getVelocity().add(toCenter.normalize().multiply(pullStrength)));
                }
            }
        }
    }

    @Override
    public void ability3(Player player) {
        double damage = boltDamage * rarity().statMultiplier();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.0f);
        LivingEntity target = null;
        double closestDot = 0.85;
        for (Entity entity : player.getWorld().getNearbyEntities(eye, 20, 20, 20)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(eye.toVector()).normalize();
            double dot = direction.dot(toEntity);
            if (dot > closestDot) {
                closestDot = dot;
                target = living;
            }
        }
        LivingEntity finalTarget = target;
        ItemDisplay icon = Fx.spinningIcon(plugin, eye, Material.ECHO_SHARD, 0.6f, boltMaxLifeTicks + 5, 45.0);
        new BukkitRunnable() {
            int ticks = 0;
            Location pos = eye.clone();

            @Override
            public void run() {
                if (ticks >= boltMaxLifeTicks || (finalTarget != null && !finalTarget.isValid())) {
                    removeIcon();
                    cancel();
                    return;
                }
                Vector heading = finalTarget != null
                        ? finalTarget.getLocation().add(0, 1, 0).toVector().subtract(pos.toVector())
                        : direction.clone().multiply(10);
                if (finalTarget != null && heading.lengthSquared() <= boltHitRadius * boltHitRadius) {
                    finalTarget.damage(damage, player);
                    Fx.coloredBurst(pos, VOID_PURPLE, 1.8f, 30, 0.4);
                    Fx.bloodSpray(pos);
                    removeIcon();
                    cancel();
                    return;
                }
                if (finalTarget == null && pos.distance(eye) > 10) {
                    removeIcon();
                    cancel();
                    return;
                }
                pos.add(heading.normalize().multiply(boltSpeed));
                if (icon != null && !icon.isDead()) {
                    icon.teleport(pos);
                }
                Fx.coloredBurst(pos, VOID_PURPLE, 1.0f, 6, 0.1);
                Fx.point(pos, Particle.PORTAL, 4);
                ticks++;
            }

            private void removeIcon() {
                if (icon != null && !icon.isDead()) {
                    icon.remove();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        double damage = collapseDamage * rarity().statMultiplier();
        Location markedSpot = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(6));
        World world = player.getWorld();
        Fx.coloredRing(markedSpot, VOID_PURPLE, 1.6f, collapseRadius * 0.6, 24, 0);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        // A real small star (nether star prop) gathers at the mark instead of just a particle ring.
        Fx.spinningIcon(plugin, markedSpot.clone().add(0, 1, 0), Material.NETHER_STAR, 0.8f, collapseDelayTicks + 5, 25.0);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Fx.coloredBurst(markedSpot.clone().add(0, 1, 0), VOID_PURPLE, 2.2f, 50, 0.8);
                Fx.burst(markedSpot.clone().add(0, 1, 0), Particle.EXPLOSION, 4, 0.3);
                Fx.sound(markedSpot, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                for (Entity entity : world.getNearbyEntities(markedSpot, collapseRadius, collapseRadius, collapseRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
            }
        }.runTaskLater(plugin, collapseDelayTicks);
    }
}
