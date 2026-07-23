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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rotscourge — the Plague Warden's legendary scythe. A poison-focused reaper:
 * a venom cone, a thrown lingering toxic cloud, a life-draining leech, an
 * expanding plague outbreak ultimate, and a passive that stacks poison on
 * every melee hit.
 */
public final class Rotscourge extends Weapon {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);
    private static final Color SICKLY = Color.fromRGB(150, 200, 80);

    private final double venomRange;
    private final double venomDamage;
    private final int venomPoisonTicks;
    private final double cloudRangeAhead;
    private final double cloudRadius;
    private final int cloudDurationTicks;
    private final double cloudDamagePerTick;
    private final double leechRange;
    private final double leechDamage;
    private final double leechHealFraction;
    private final double outbreakMaxRadius;
    private final int outbreakDurationTicks;
    private final double outbreakDamage;
    private final int passivePoisonTicks;
    private final int passiveMaxStacks;
    private final long passiveStackWindowMs;

    private final Map<UUID, Integer> poisonStacks = new HashMap<>();
    private final Map<UUID, Long> lastHitMs = new HashMap<>();

    public Rotscourge(WeaponsPlugin plugin) {
        super(plugin);
        this.venomRange = configDouble("venom-range", 4.5);
        this.venomDamage = configDouble("venom-damage", 6.0);
        this.venomPoisonTicks = configInt("venom-poison-ticks", 100);
        this.cloudRangeAhead = configDouble("cloud-range-ahead", 5.0);
        this.cloudRadius = configDouble("cloud-radius", 3.0);
        this.cloudDurationTicks = configInt("cloud-duration-ticks", 100);
        this.cloudDamagePerTick = configDouble("cloud-damage-per-tick", 1.5);
        this.leechRange = configDouble("leech-range", 3.5);
        this.leechDamage = configDouble("leech-damage", 7.0);
        this.leechHealFraction = configDouble("leech-heal-fraction", 0.6);
        this.outbreakMaxRadius = configDouble("outbreak-max-radius", 7.0);
        this.outbreakDurationTicks = configInt("outbreak-duration-ticks", 30);
        this.outbreakDamage = configDouble("outbreak-damage", 8.0);
        this.passivePoisonTicks = configInt("passive-poison-ticks", 80);
        this.passiveMaxStacks = configInt("passive-max-stacks", 4);
        this.passiveStackWindowMs = (long) (configDouble("passive-stack-window-seconds", 5.0) * 1000);
    }

    @Override
    public String id() {
        return "rotscourge";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_HOE;
    }

    @Override
    public String displayNameText() {
        return "Rotscourge";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
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
        return configDouble("ability2-cooldown-seconds", 8.0);
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
                Component.text("Right-click: Venom Slash — a cone of", NamedTextColor.GRAY),
                Component.text("toxic spores poisons everything ahead.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Venom Slash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Toxic Cloud — hurl", NamedTextColor.GRAY),
                Component.text("a lingering poison zone ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Toxic Cloud";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Life Leech — drain the", NamedTextColor.GRAY),
                Component.text("nearest enemy, healing yourself.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Life Leech";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Outbreak — an expanding", NamedTextColor.GRAY),
                Component.text("plague nova ravages all around you.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Outbreak";
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

    /** Passive: every melee hit deepens a stacking infection on the victim. */
    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        UUID id = victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastHitMs.get(id);
        int stacks = (last != null && now - last <= passiveStackWindowMs) ? poisonStacks.getOrDefault(id, 0) : 0;
        stacks = Math.min(passiveMaxStacks - 1, stacks + 1);
        poisonStacks.put(id, stacks);
        lastHitMs.put(id, now);
        StatusEffectManager.apply(victim, PotionEffectType.POISON, passivePoisonTicks, stacks);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), TOXIC, 1.0f, 8, 0.3);
    }

    @Override
    public void ability1(Player player) {
        double damage = venomDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location origin = player.getEyeLocation();
        var direction = origin.getDirection().normalize();
        Fx.sound(player, castSound(), 1.0f, 0.8f);
        // Draw the cone of spores.
        for (double d = 1; d <= venomRange; d += 0.5) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            Fx.coloredBurst(point, SICKLY, 1.0f, 4, 0.3);
            Fx.point(point, Particle.SPORE_BLOSSOM_AIR, 2);
        }
        for (Entity entity : world.getNearbyEntities(origin, venomRange, venomRange, venomRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = direction.dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.6) {
                continue;
            }
            living.damage(damage, player);
            StatusEffectManager.apply(living, PotionEffectType.POISON, venomPoisonTicks, 1);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = cloudDamagePerTick * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(cloudRangeAhead));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_WITCH_THROW, 1.0f, 0.6f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= cloudDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                double pulse = 0.7 + 0.3 * Math.sin(ticks * 0.2);
                Fx.burst(center, Particle.SPORE_BLOSSOM_AIR, 10, cloudRadius * 0.5 * pulse);
                Fx.coloredBurst(center, TOXIC, 1.0f, (int) (8 * pulse), cloudRadius * 0.4 * pulse);
                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(center, cloudRadius, cloudRadius, cloudRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            StatusEffectManager.apply(living, PotionEffectType.POISON, 60, 1);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        double damage = leechDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location origin = player.getLocation();
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(origin, leechRange, leechRange, leechRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = origin.getDirection().normalize().dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.4) {
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
        target.damage(damage, player);
        StatusEffectManager.apply(target, PotionEffectType.POISON, 80, 1);
        double heal = damage * leechHealFraction;
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
        Fx.line(target.getLocation().add(0, 1, 0), player.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 18);
        Fx.coloredBurst(target.getLocation().add(0, 1, 0), TOXIC, 1.3f, 18, 0.4);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), SICKLY, 1.2f, 14, 0.4);
        Fx.sound(player, Sound.ENTITY_WITCH_DRINK, 1.0f, 1.0f);
    }

    @Override
    public void ultimate(Player player) {
        double damage = outbreakDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location center = player.getLocation();
        Fx.sound(player, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.7f);
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= outbreakDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                double radius = outbreakMaxRadius * (ticks + 1) / (double) outbreakDurationTicks;
                Fx.coloredRing(center, TOXIC, 1.4f, radius, 30, ticks * 0.3);
                Fx.ring(center, Particle.SPORE_BLOSSOM_AIR, radius, 24, ticks * 0.3);
                if (ticks % 5 == 0) {
                    for (Entity entity : world.getNearbyEntities(center, radius, 3.0, radius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            double dist = living.getLocation().distance(center);
                            if (dist <= radius && dist >= radius - 1.5) {
                                living.damage(damage, player);
                                StatusEffectManager.apply(living, PotionEffectType.POISON, 100, 2);
                                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
