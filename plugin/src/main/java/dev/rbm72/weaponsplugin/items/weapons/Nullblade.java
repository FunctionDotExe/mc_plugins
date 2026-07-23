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
import org.bukkit.entity.ItemDisplay;
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

/** Void/blink sword: a teleport-slash, a ranged rift bolt, a banishing knockback, and a collapsing singularity. */
public final class Nullblade extends Weapon {

    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);
    private static final Color DEEP_VOID = Color.fromRGB(45, 0, 70);

    private final double blinkRange;
    private final double blinkDamage;
    private final double blinkHitRadius;
    private final double boltDamage;
    private final double boltRange;
    private final double boltHitRadius;
    private final double boltSpeed;
    private final double banishDamage;
    private final double banishRange;
    private final double banishDistance;
    private final double singularityPullRadius;
    private final int singularityPullDurationTicks;
    private final double singularityDamage;
    private final double singularityRadius;
    private final int killInvisibilityTicks;
    private final int killSpeedTicks;

    public Nullblade(WeaponsPlugin plugin) {
        super(plugin);
        this.blinkRange = configDouble("blink-range", 6.0);
        this.blinkDamage = configDouble("blink-damage", 6.0);
        this.blinkHitRadius = configDouble("blink-hit-radius", 2.5);
        this.boltDamage = configDouble("bolt-damage", 7.0);
        this.boltRange = configDouble("bolt-range", 24.0);
        this.boltHitRadius = configDouble("bolt-hit-radius", 1.6);
        this.boltSpeed = configDouble("bolt-speed", 1.1);
        this.banishDamage = configDouble("banish-damage", 5.0);
        this.banishRange = configDouble("banish-range", 6.0);
        this.banishDistance = configDouble("banish-distance", 8.0);
        this.singularityPullRadius = configDouble("singularity-pull-radius", 7.0);
        this.singularityPullDurationTicks = configInt("singularity-pull-duration-ticks", 40);
        this.singularityDamage = configDouble("singularity-damage", 14.0);
        this.singularityRadius = configDouble("singularity-radius", 5.0);
        this.killInvisibilityTicks = configInt("kill-invisibility-ticks", 60);
        this.killSpeedTicks = configInt("kill-speed-ticks", 80);
    }

    @Override
    public String id() {
        return "nullblade";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Nullblade";
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
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 8.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 10.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: blink forward,", NamedTextColor.GRAY),
                Component.text("slashing enemies where you land.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Blink Slash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: fire a void bolt", NamedTextColor.GRAY),
                Component.text("that tears through the first enemy hit.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Void Lance";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: banish the enemy ahead,", NamedTextColor.GRAY),
                Component.text("hurling them away through the void.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Banish";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: collapse a singularity", NamedTextColor.GRAY),
                Component.text("that pulls enemies in, then implodes.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Event Horizon";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDERMAN_TELEPORT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_WARDEN_SONIC_BOOM;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_PORTAL_AMBIENT;
    }

    @Override
    public void onKill(Player attacker, LivingEntity victim) {
        // Passive: a kill folds the wielder briefly out of sight and speeds their step.
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, killInvisibilityTicks, 0));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, killSpeedTicks, 1));
        Fx.burst(attacker.getLocation().add(0, 1, 0), Particle.PORTAL, 20, 0.4);
        Fx.coloredBurst(attacker.getLocation().add(0, 1, 0), VOID_PURPLE, 1.4f, 16, 0.4);
        Fx.sound(attacker, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Location start = player.getLocation();
        Location best = start.clone();
        for (double d = 0.5; d <= blinkRange; d += 0.5) {
            Location candidate = start.clone().add(direction.clone().multiply(d));
            if (!candidate.getBlock().getType().isSolid() && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                best = candidate;
            }
        }
        Fx.line(start.clone().add(0, 1, 0), best.clone().add(0, 1, 0), Particle.PORTAL, 24);
        player.teleport(best);
        Fx.burst(best.clone().add(0, 1, 0), Particle.PORTAL, 40, 0.6);
        Fx.coloredBurst(best.clone().add(0, 1, 0), VOID_PURPLE, 1.6f, 28, 0.6);

        double damage = blinkDamage * rarity().statMultiplier();
        for (Entity nearby : player.getNearbyEntities(blinkHitRadius, blinkHitRadius, blinkHitRadius)) {
            if (nearby instanceof LivingEntity target) {
                target.damage(damage, player);
                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        Vector direction = player.getLocation().getDirection().normalize();
        double damage = boltDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();
        Location eye = player.getEyeLocation();
        ItemDisplay icon = Fx.spinningIcon(plugin, eye, Material.ECHO_SHARD, 0.6f,
                (int) Math.ceil(boltRange / boltSpeed) + 5, 40.0);

        new BukkitRunnable() {
            double travelled = 0;
            Location pos = eye.clone();

            @Override
            public void run() {
                if (!player.isOnline() || travelled >= boltRange) {
                    removeIcon();
                    cancel();
                    return;
                }
                pos.add(direction.clone().multiply(boltSpeed));
                travelled += boltSpeed;
                if (pos.getBlock().getType().isSolid()) {
                    Fx.coloredBurst(pos, DEEP_VOID, 1.8f, 24, 0.4);
                    Fx.sound(pos, Sound.BLOCK_PORTAL_TRIGGER, 0.7f, 1.2f);
                    removeIcon();
                    cancel();
                    return;
                }
                if (icon != null && !icon.isDead()) {
                    icon.teleport(pos);
                }
                Fx.coloredBurst(pos, VOID_PURPLE, 1.2f, 6, 0.12);
                Fx.point(pos, Particle.REVERSE_PORTAL, 4);
                for (Entity nearby : world.getNearbyEntities(pos, boltHitRadius, boltHitRadius, boltHitRadius)) {
                    if (nearby instanceof LivingEntity target && !target.equals(player) && alreadyHit.add(target.getUniqueId())) {
                        target.damage(damage, player);
                        Fx.coloredBurst(pos, DEEP_VOID, 1.6f, 20, 0.4);
                        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        Fx.sound(pos, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.2f);
                        removeIcon();
                        cancel();
                        return;
                    }
                }
            }

            private void removeIcon() {
                if (icon != null && !icon.isDead()) {
                    icon.remove();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        double damage = banishDamage * rarity().statMultiplier();
        LivingEntity target = null;
        double closest = banishRange * banishRange;
        Vector look = player.getLocation().getDirection().normalize();
        Location eye = player.getEyeLocation();
        for (Entity nearby : player.getNearbyEntities(banishRange, banishRange, banishRange)) {
            if (!(nearby instanceof LivingEntity living)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(eye.toVector());
            if (toEntity.lengthSquared() > closest || look.dot(toEntity.clone().normalize()) < 0.4) {
                continue;
            }
            target = living;
            closest = toEntity.lengthSquared();
        }
        if (target == null) {
            return;
        }
        Location from = target.getLocation().clone();
        Vector away = from.toVector().subtract(player.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.001) {
            away = look.clone().setY(0);
        }
        Location to = from.clone().add(away.normalize().multiply(banishDistance));
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());

        target.damage(damage, player);
        Fx.burst(from.clone().add(0, 1, 0), Particle.PORTAL, 30, 0.5);
        Fx.coloredBurst(from.clone().add(0, 1, 0), VOID_PURPLE, 1.8f, 24, 0.5);
        target.teleport(to);
        Fx.burst(to.clone().add(0, 1, 0), Particle.PORTAL, 30, 0.4);
        Fx.bloodSpray(to.clone().add(0, 1, 0));
        Fx.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
    }

    @Override
    public void ultimate(Player player) {
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(5));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double explosionDamage = singularityDamage * rarity().statMultiplier();
        Fx.spinningIcon(plugin, center.clone().add(0, 1, 0), Material.ECHO_SHARD, 1.0f, singularityPullDurationTicks, 16);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks < singularityPullDurationTicks) {
                    Fx.ring(center, Particle.PORTAL, 2.0, 24, angle);
                    Fx.ring(center.clone().add(0, 0.6, 0), Particle.REVERSE_PORTAL, 1.6, 20, angle + 0.4);
                    Fx.coloredBurst(center, DEEP_VOID, 1.6f, 6, 0.3);
                    angle += 0.5;
                    for (Entity entity : world.getNearbyEntities(center, singularityPullRadius, singularityPullRadius, singularityPullRadius)) {
                        if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                            continue;
                        }
                        Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.25);
                        living.setVelocity(living.getVelocity().add(pull));
                    }
                    ticks++;
                    return;
                }

                Fx.burst(center, Particle.EXPLOSION_EMITTER, 1, 0.1);
                Fx.burst(center, Particle.PORTAL, 140, singularityRadius * 0.7);
                Fx.coloredBurst(center, DEEP_VOID, 2.4f, 50, singularityRadius * 0.6);
                Fx.coloredBurst(center.clone().add(0, 1.0, 0), VOID_PURPLE, 2.0f, 30, singularityRadius * 0.5);
                Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                for (Entity entity : world.getNearbyEntities(center, singularityRadius, singularityRadius, singularityRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    living.damage(explosionDamage, player);
                    Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.6);
                    living.setVelocity(living.getVelocity().add(knockback.multiply(1.5)));
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
