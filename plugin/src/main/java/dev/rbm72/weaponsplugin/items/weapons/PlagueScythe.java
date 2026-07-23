package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
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
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Poison-themed scythe (reskinned hoe): poison clouds, direct infection, exploding spores, and a chaining plague ultimate. */
public final class PlagueScythe extends Weapon {

    private final double cloudDamagePerTick;
    private final double cloudRadius;
    private final int cloudDurationTicks;
    private final double cloudRangeAhead;
    private final double infectRange;
    private final int infectPoisonTicks;
    private final double sporeSpeed;
    private final double sporeDamage;
    private final double sporeCloudRadius;
    private final double plagueInitialRadius;
    private final double plagueChainRadius;
    private final int plagueDurationTicks;
    private final double plagueDamagePerTick;
    private final double deathCloudRadius;
    private final int deathCloudDurationTicks;
    private final double deathCloudDamagePerTick;

    public PlagueScythe(WeaponsPlugin plugin) {
        super(plugin);
        this.cloudDamagePerTick = configDouble("cloud-damage-per-tick", 1.5);
        this.cloudRadius = configDouble("cloud-radius", 3.0);
        this.cloudDurationTicks = configInt("cloud-duration-ticks", 80);
        this.cloudRangeAhead = configDouble("cloud-range-ahead", 5.0);
        this.infectRange = configDouble("infect-range", 3.5);
        this.infectPoisonTicks = configInt("infect-poison-ticks", 100);
        this.sporeSpeed = configDouble("spore-speed", 1.6);
        this.sporeDamage = configDouble("spore-damage", 4.0);
        this.sporeCloudRadius = configDouble("spore-cloud-radius", 2.5);
        this.plagueInitialRadius = configDouble("plague-initial-radius", 5.0);
        this.plagueChainRadius = configDouble("plague-chain-radius", 4.0);
        this.plagueDurationTicks = configInt("plague-duration-ticks", 100);
        this.plagueDamagePerTick = configDouble("plague-damage-per-tick", 1.5);
        this.deathCloudRadius = configDouble("death-cloud-radius", 3.0);
        this.deathCloudDurationTicks = configInt("death-cloud-duration-ticks", 60);
        this.deathCloudDamagePerTick = configDouble("death-cloud-damage-per-tick", 1.0);
    }

    @Override
    public String id() {
        return "plague_scythe";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_HOE;
    }

    @Override
    public String displayNameText() {
        return "Plague Scythe";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 5.0);
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
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: create a poison cloud", NamedTextColor.GRAY),
                Component.text("ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Miasma Cloud";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: infect the", NamedTextColor.GRAY),
                Component.text("nearest enemy in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Infect";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: throw an exploding", NamedTextColor.GRAY),
                Component.text("spore pod.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Spore Bomb";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: infect every nearby", NamedTextColor.GRAY),
                Component.text("enemy; the plague jumps between", NamedTextColor.GRAY),
                Component.text("infected enemies.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Pandemic";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_WITCH_THROW;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_WITCH_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_BREWING_STAND_BREW;
    }

    @Override
    public void onKill(Player attacker, LivingEntity victim) {
        Location loc = victim.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= deathCloudDurationTicks || !attacker.isOnline()) {
                    cancel();
                    return;
                }
                Fx.burst(loc, Particle.ITEM_SLIME, 6, deathCloudRadius * 0.4);
                Fx.coloredBurst(loc, Color.fromRGB(80, 140, 40), 0.9f, 5, deathCloudRadius * 0.35);
                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(loc, deathCloudRadius, deathCloudRadius, deathCloudRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(attacker)) {
                            living.damage(deathCloudDamagePerTick * rarity().statMultiplier(), attacker);
                            StatusEffectManager.apply(living, PotionEffectType.POISON, 40, 0);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability1(Player player) {
        double damage = cloudDamagePerTick * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(cloudRangeAhead));
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= cloudDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                double pulseScale = 0.7 + 0.3 * Math.sin(ticks * 0.2);
                Fx.burst(center, Particle.ITEM_SLIME, 10, cloudRadius * 0.5 * pulseScale);
                Fx.coloredBurst(center, Color.fromRGB(80, 140, 40), 1.0f, (int) (8 * pulseScale), cloudRadius * 0.4 * pulseScale);
                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(center, cloudRadius, cloudRadius, cloudRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            StatusEffectManager.apply(living, PotionEffectType.POISON, 60, 0);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(origin, infectRange, infectRange, infectRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = origin.getDirection().normalize().dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.5) {
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
        StatusEffectManager.apply(target, PotionEffectType.POISON, infectPoisonTicks, 2);
        StatusEffectManager.apply(target, PotionEffectType.WEAKNESS, infectPoisonTicks, 1);
        Fx.burst(target.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 20, 0.4);
        Fx.coloredBurst(target.getLocation().add(0, 1, 0), Color.fromRGB(80, 140, 40), 1.1f, 16, 0.4);
        Fx.sound(target.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.2f);
    }

    @Override
    public void ability3(Player player) {
        Snowball projectile = player.launchProjectile(Snowball.class,
                player.getLocation().getDirection().multiply(sporeSpeed));
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());

        var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.FERMENTED_SPIDER_EYE, 0.8f, 60, 18.0);

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
                Fx.point(projectile.getLocation(), Particle.ITEM_SLIME, 3);
                if (ticks % 3 == 0) {
                    Fx.coloredBurst(projectile.getLocation(), Color.fromRGB(80, 140, 40), 0.7f, 3, 0.06);
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
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        double damage = sporeDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.ITEM_SLIME, 25, sporeCloudRadius * 0.5);
        Fx.coloredBurst(loc, Color.fromRGB(80, 140, 40), 1.3f, 20, sporeCloudRadius * 0.5);
        Fx.sound(loc, hitSound(), 1.0f, 1.0f);

        for (Entity entity : world.getNearbyEntities(loc, sporeCloudRadius, sporeCloudRadius, sporeCloudRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(shooter)) {
                living.damage(damage, shooter);
                StatusEffectManager.apply(living, PotionEffectType.POISON, 60, 0);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        Fx.sound(player, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.8f);
        Set<UUID> infected = new HashSet<>();

        for (Entity entity : world.getNearbyEntities(origin, plagueInitialRadius, plagueInitialRadius, plagueInitialRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                infected.add(living.getUniqueId());
                Fx.burst(living.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 35, 0.55);
                Fx.coloredBurst(living.getLocation().add(0, 1, 0), Color.fromRGB(80, 140, 40), 2.4f, 30, 0.55);
                Fx.coloredBurst(living.getLocation().add(0, 1.7, 0), Color.fromRGB(120, 180, 60), 1.8f, 18, 0.35);
                Fx.spinningIcon(plugin, living.getLocation().add(0, 2.0, 0), Material.SPIDER_EYE, 1.0f, plagueDurationTicks, 15);
            }
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= plagueDurationTicks || infected.isEmpty() || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks % 10 == 0) {
                    Set<UUID> newlyInfected = new HashSet<>();
                    for (Entity entity : world.getEntities()) {
                        if (!(entity instanceof LivingEntity living) || !infected.contains(living.getUniqueId())) {
                            continue;
                        }
                        living.damage(plagueDamagePerTick * rarity().statMultiplier(), player);
                        StatusEffectManager.apply(living, PotionEffectType.POISON, 40, 0);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));

                        for (Entity nearby : world.getNearbyEntities(living.getLocation(), plagueChainRadius, plagueChainRadius, plagueChainRadius)) {
                            if (nearby instanceof LivingEntity candidate && !candidate.equals(player)
                                    && !infected.contains(candidate.getUniqueId())) {
                                newlyInfected.add(candidate.getUniqueId());
                                Fx.line(living.getLocation().add(0, 1, 0), candidate.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 18);
                                Fx.line(living.getLocation().add(0, 1.3, 0), candidate.getLocation().add(0, 1.3, 0), Particle.ITEM_SLIME, 12);
                                Fx.coloredBurst(candidate.getLocation().add(0, 1, 0), Color.fromRGB(80, 140, 40), 2.2f, 26, 0.5);
                                Fx.coloredBurst(candidate.getLocation().add(0, 1.7, 0), Color.fromRGB(120, 180, 60), 1.6f, 14, 0.3);
                                Fx.spinningIcon(plugin, candidate.getLocation().add(0, 2.0, 0), Material.SPIDER_EYE, 1.0f,
                                        Math.max(20, plagueDurationTicks - ticks), 15);
                            }
                        }
                    }
                    infected.addAll(newlyInfected);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
