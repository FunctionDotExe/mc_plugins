package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import dev.rbm72.weaponsplugin.listeners.PlayerSummonTargetListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Vex;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Soulharvester — the Necro Overlord's legendary death scythe (reskinned hoe).
 * A reaping cone that heals on the swing, a temporary friendly wraith, a
 * withering soul bolt, and an AoE harvest ultimate that drains life from every
 * enemy struck. Passive: kills heal the wielder and bank a soul stack that
 * adds bonus melee damage.
 */
public final class Soulharvester extends Weapon {

    private static final Color BONE = Color.fromRGB(235, 235, 210);
    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final double reapRange;
    private final double reapDamage;
    private final double reapHealFraction;
    private final int wraithDurationTicks;
    private final double soulBoltDamage;
    private final double soulBoltSpeed;
    private final int soulBoltWitherTicks;
    private final double harvestRadius;
    private final int harvestDurationTicks;
    private final double harvestDamagePerPulse;
    private final double harvestHealFraction;
    private final int soulHealAmount;
    private final int maxSoulStacks;
    private final double soulStackDamageBonus;

    private final Map<UUID, Integer> soulStacks = new HashMap<>();
    private final NamespacedKey playerSummonKey;

    public Soulharvester(WeaponsPlugin plugin) {
        super(plugin);
        this.playerSummonKey = new NamespacedKey(plugin, PlayerSummonTargetListener.KEY_NAME);
        this.reapRange = configDouble("reap-range", 4.5);
        this.reapDamage = configDouble("reap-damage", 7.0);
        this.reapHealFraction = configDouble("reap-heal-fraction", 0.4);
        this.wraithDurationTicks = configInt("wraith-duration-ticks", 300);
        this.soulBoltDamage = configDouble("soul-bolt-damage", 6.0);
        this.soulBoltSpeed = configDouble("soul-bolt-speed", 1.8);
        this.soulBoltWitherTicks = configInt("soul-bolt-wither-ticks", 80);
        this.harvestRadius = configDouble("harvest-radius", 6.0);
        this.harvestDurationTicks = configInt("harvest-duration-ticks", 50);
        this.harvestDamagePerPulse = configDouble("harvest-damage-per-pulse", 3.0);
        this.harvestHealFraction = configDouble("harvest-heal-fraction", 0.5);
        this.soulHealAmount = configInt("soul-heal-amount", 4);
        this.maxSoulStacks = configInt("max-soul-stacks", 5);
        this.soulStackDamageBonus = configDouble("soul-stack-damage-bonus", 1.5);
    }

    @Override
    public String id() {
        return "soulharvester";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_HOE;
    }

    @Override
    public String displayNameText() {
        return "Soulharvester";
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
        return configDouble("ability2-cooldown-seconds", 18.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 5.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Reaping")
                .accent(NECROTIC)
                .perMeleeHit(configDouble("reaping-per-hit", 6.0))
                .perDamageDealt(configDouble("reaping-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("reaping-per-ability", 8.0))
                .perKill(configDouble("reaping-per-kill", 12.0))
                .decay(configDouble("reaping-decay-per-second", 2.0), configDouble("reaping-decay-grace", 7.0))
                .cooldownFloor(configDouble("reaping-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Reaping Slash — a cone of", NamedTextColor.GRAY),
                Component.text("soul-rending scythe work that heals you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Reaping Slash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Summon Wraith — raise", NamedTextColor.GRAY),
                Component.text("a temporary spectral ally.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Summon Wraith";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Soul Bolt — hurl a withering", NamedTextColor.GRAY),
                Component.text("mote of soul-fire.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Soul Bolt";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Harvest — drain the life", NamedTextColor.GRAY),
                Component.text("from all around you, mending yourself.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Harvest";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_WITHER_SHOOT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_WITHER_SKELETON_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.PARTICLE_SOUL_ESCAPE;
    }

    /** Passive: banked soul stacks each add flat bonus melee damage. */
    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        int stacks = soulStacks.getOrDefault(attacker.getUniqueId(), 0);
        if (stacks > 0) {
            event.setDamage(event.getDamage() + stacks * soulStackDamageBonus * rarity().statMultiplier());
            Fx.coloredBurst(victim.getLocation().add(0, 1, 0), NECROTIC, 1.0f, 6, 0.3);
        }
    }

    /** Passive: kills heal the wielder and bank a soul stack (capped). */
    @Override
    public void onKill(Player attacker, LivingEntity victim) {
        int stacks = Math.min(maxSoulStacks, soulStacks.getOrDefault(attacker.getUniqueId(), 0) + 1);
        soulStacks.put(attacker.getUniqueId(), stacks);
        double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + soulHealAmount));
        Fx.line(victim.getLocation().add(0, 1, 0), attacker.getLocation().add(0, 1, 0), Particle.SOUL, 16);
        Fx.coloredBurst(attacker.getLocation().add(0, 1, 0), NECROTIC, 1.6f, 20, 0.4);
        Fx.sound(attacker, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 1.2f);
    }

    @Override
    public void ability1(Player player) {
        double damage = reapDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location origin = player.getEyeLocation();
        var direction = origin.getDirection().normalize();
        Fx.sound(player, castSound(), 1.0f, 0.9f);
        for (double d = 1; d <= reapRange; d += 0.5) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            Fx.coloredBurst(point, BONE, 1.0f, 3, 0.25);
            Fx.point(point, Particle.SOUL, 2);
        }

        double totalDealt = 0;
        for (Entity entity : world.getNearbyEntities(origin, reapRange, reapRange, reapRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = direction.dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.6) {
                continue;
            }
            living.damage(damage, player);
            totalDealt += damage;
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }

        if (totalDealt > 0) {
            double heal = totalDealt * reapHealFraction;
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), NECROTIC, 1.6f, 20, 0.45);
        }
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        Location spawnLoc = player.getLocation().add((Math.random() - 0.5) * 2, 1, (Math.random() - 0.5) * 2);
        Vex wraith = world.spawn(spawnLoc, Vex.class, mob -> {
            mob.customName(Component.text("Wraith", NamedTextColor.DARK_GREEN));
            mob.setCustomNameVisible(true);
            mob.getPersistentDataContainer().set(playerSummonKey, PersistentDataType.BYTE, (byte) 1);
        });
        Fx.coloredBurst(spawnLoc, NECROTIC, 1.8f, 26, 0.5);
        Fx.burst(spawnLoc, Particle.SCULK_SOUL, 24, 0.4);
        Fx.sound(player, Sound.ENTITY_VEX_AMBIENT, 1.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!wraith.isValid() || ticks >= wraithDurationTicks) {
                    if (wraith.isValid()) {
                        Fx.burst(wraith.getLocation().add(0, 1, 0), Particle.SOUL, 20, 0.4);
                        wraith.remove();
                    }
                    cancel();
                    return;
                }
                // Never let the wraith turn on its summoner.
                if (wraith.getTarget() != null && wraith.getTarget().equals(player)) {
                    wraith.setTarget(null);
                }
                if (ticks % 5 == 0) {
                    Fx.point(wraith.getLocation().add(0, 0.5, 0), Particle.SOUL, 2);
                }
                ticks += 5;
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    @Override
    public void ability3(Player player) {
        Location eye = player.getEyeLocation();
        Snowball projectile = player.launchProjectile(Snowball.class, eye.getDirection().multiply(soulBoltSpeed));
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        Fx.sound(player, castSound(), 1.0f, 1.1f);
        ItemDisplay icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.WITHER_SKELETON_SKULL, 0.5f, 65, 40.0);

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
                Fx.point(projectile.getLocation(), Particle.SOUL_FIRE_FLAME, 4);
                Fx.coloredBurst(projectile.getLocation(), NECROTIC, 0.8f, 3, 0.05);
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
        double damage = soulBoltDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.SCULK_SOUL, 14, 0.3);
        Fx.coloredBurst(loc, NECROTIC, 1.2f, 16, 0.3);
        Fx.sound(loc, hitSound(), 0.9f, 1.0f);
        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            StatusEffectManager.apply(target, PotionEffectType.WITHER, soulBoltWitherTicks, 0);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ultimate(Player player) {
        double damage = harvestDamagePerPulse * rarity().statMultiplier();
        World world = player.getWorld();
        Fx.sound(player, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.6f);
        Fx.sound(player, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.5f);
        Fx.spinningIcon(plugin, player.getEyeLocation().add(0, 0.6, 0), Material.WITHER_SKELETON_SKULL, 1.1f, harvestDurationTicks, 14);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= harvestDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location origin = player.getLocation();
                Fx.coloredRing(origin, NECROTIC, 1.3f, harvestRadius, 26, ticks * 0.25);
                Fx.ring(origin, Particle.SOUL, harvestRadius * 0.7, 20, -ticks * 0.2);
                if (ticks % 5 == 0) {
                    double healed = 0;
                    for (Entity entity : world.getNearbyEntities(origin, harvestRadius, harvestRadius, harvestRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            healed += damage;
                            Fx.line(living.getLocation().add(0, 1, 0), origin.clone().add(0, 1, 0), Particle.SOUL, 14);
                        }
                    }
                    if (healed > 0) {
                        double heal = healed * harvestHealFraction;
                        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                        player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
                        Fx.coloredBurst(origin.clone().add(0, 1, 0), NECROTIC, 2.0f, 24, 0.5);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
